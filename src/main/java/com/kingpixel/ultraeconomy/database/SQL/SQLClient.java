package com.kingpixel.ultraeconomy.database.SQL;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseClient;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.database.TransactionType;
import com.kingpixel.ultraeconomy.exceptions.DatabaseConnectionException;
import com.kingpixel.ultraeconomy.exceptions.UnknownAccountException;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.BackupInfo;
import com.kingpixel.ultraeconomy.models.Currency;
import com.kingpixel.ultraeconomy.models.Transaction;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@Data
public class SQLClient extends DatabaseClient {
  private static final String KEY_AMOUNT = "amount";
  private DataBaseType dbType;
  private HikariDataSource dataSource;
  private volatile boolean runningTransactions = false;

  /**
   * Incremented on every connect()/disconnect() so stale scheduled tasks from a previous session
   * recognise they are orphaned and exit without doing any work.
   */
  private final AtomicLong sessionId = new AtomicLong(0);


  @Override
  public void connect(DataBaseConfig config) {
    try {
      dbType = config.getType();
      SQLSentences.Data data = SQLSentences.configure();
      dataSource = data.getDataSource();
      UltraEconomy.LOGGER.info("Connected to {} database at {}", config.getType(), config.getUrl());

      initTables(config.getType());
      createIndexes();

      runningTransactions = true;

      // Capture session before scheduling so the lambda can detect reload/disconnect cycles
      long mySession = sessionId.incrementAndGet();
      UltraEconomy.getAsyncContext().scheduleAtFixedRate(
        () -> {
          if (sessionId.get() != mySession) return; // Stale — skip
          checkAndApplyTransactions();
        },
        0, 2, TimeUnit.SECONDS
      );

    } catch (Exception e) {
      throw new DatabaseConnectionException(config.getType().name());
    }
  }


  @Override
  public void disconnect() {
    runningTransactions = false;
    // Invalidate active session so the scheduled task short-circuits on its next tick
    sessionId.incrementAndGet();
    if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    UltraEconomy.LOGGER.info("Disconnected from database.");
  }

  @Override
  public void invalidate(UUID playerUUID) {
    DatabaseFactory.ACCOUNTS.invalidate(playerUUID);
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = DatabaseFactory.ACCOUNTS.getIfPresent(uuid);
    if (cached != null) return cached;

    try (Connection conn = dataSource.getConnection()) {
      Account account;
      try (PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectAccountByUUID())) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
          Map<String, BigDecimal> balances = new HashMap<>();
          try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.selectBalancesByUUID())) {
            balStmt.setString(1, uuid.toString());
            ResultSet balRs = balStmt.executeQuery();
            while (balRs.next())
              balances.put(balRs.getString("currency_id"), balRs.getBigDecimal(KEY_AMOUNT));
          }
          account = new Account(uuid, rs.getString("player_name"), balances);
        } else {
          var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
          if (player != null) {
            account = new Account(player);
            saveOrUpdateAccount(account);
          } else return null;
        }
      }
      DatabaseFactory.ACCOUNTS.put(uuid, account);
      return account;
    } catch (SQLException e) {
      throw new UnknownAccountException(uuid);
    }
  }

  public void getAccountAsync(UUID uuid, Consumer<Account> callback) {
    UltraEconomy.runAsync(() -> callback.accept(getAccount(uuid)));
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    UltraEconomy.runAsync(() -> saveAccount(account));
  }

  @Override
  public void saveOrUpdateAccountSync(Account account) {
    saveAccount(account);
  }

  private void saveAccount(Account account) {
    if (account == null) {
      UltraEconomy.LOGGER.warn("Tried to save a null account.");
      return;
    }
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try {
        try (PreparedStatement stmt = conn.prepareStatement(SQLSentences.insertAccount())) {
          stmt.setString(1, account.getPlayerUUID().toString());
          stmt.setString(2, account.getPlayerName());
          stmt.executeUpdate();
        }
        for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
          try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.insertBalance())) {
            balStmt.setString(1, account.getPlayerUUID().toString());
            balStmt.setString(2, entry.getKey());
            balStmt.setBigDecimal(3, entry.getValue());
            balStmt.executeUpdate();
          }
        }
        conn.commit();
        account.markClean();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error saving account {}", account.getPlayerUUID(), e);
    }
  }

  @Override
  public boolean addBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = false;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, false);
      result = true;
    } else {
      result = account.addBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, true);
    }
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      // Load from DB to verify balance — blindly queueing a WITHDRAW would return true without checking funds
      account = getAccount(uuid);
      if (account == null) return false;
    }
    boolean result = account.removeBalance(currency, amount);
    if (result) addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
    return result;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.SET, false);
    } else {
      account.setBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.SET, true);
      // Dirty flag is set — the periodic flush will persist this
    }
    return amount;
  }

  @Override
  public BigDecimal getBalance(UUID uuid, Currency currency) {
    Account account = getAccount(uuid);
    if (account == null) return null;
    return account.getBalance(currency);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    if (account == null) return false;
    return account.hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(Currency currency, int page, int playersPerPage) {
    List<Account> topAccounts = new ArrayList<>();
    int offset = (page - 1) * playersPerPage;

    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectTopBalances())) {
      stmt.setString(1, currency.getId());
      stmt.setInt(2, playersPerPage);
      stmt.setInt(3, offset);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String playerName = rs.getString("player_name");
        BigDecimal amount = rs.getBigDecimal(KEY_AMOUNT);

        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put(currency.getId(), amount);
        Account account = new Account(uuid, playerName, balances);
        topAccounts.add(account);
      }
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error fetching top balances", e);
    }

    return topAccounts;
  }

  @Override
  public boolean existPlayerWithUUID(UUID uuid) {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectAccountByUUID())) {
      stmt.setString(1, uuid.toString());
      ResultSet rs = stmt.executeQuery();
      return rs.next();
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error checking existence of player with UUID {}", uuid, e);
      return false;
    }
  }


  public void addTransaction(UUID uuid, Currency currency, BigDecimal amount, TransactionType type, boolean processed) {
    UltraEconomy.runAsync(() -> {
      String query = SQLSentences.insertTransaction();
      try (Connection conn = dataSource.getConnection();
           PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, uuid.toString());
        stmt.setString(2, currency.getId());
        stmt.setBigDecimal(3, amount);
        stmt.setString(4, type.name());
        stmt.setBoolean(5, processed);
        stmt.executeUpdate();
      } catch (SQLException e) {
        UltraEconomy.LOGGER.error("Error adding transaction for {}", uuid, e);
      }
    });
  }

  @Override
  public CompletableFuture<?> createBackUp() {
    return UltraEconomy.runAsync(() -> {
      UUID backupUUID = UUID.randomUUID();
      Path backupDir = Path.of(UltraEconomy.PATH, "backups", "sql");
      try {
        Files.createDirectories(backupDir);

        List<Map<String, Object>> accountsList = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, player_name FROM accounts")) {
          while (rs.next()) {
            String uuid = rs.getString("uuid");
            String playerName = rs.getString("player_name");
            Map<String, BigDecimal> balances = new HashMap<>();
            try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.selectBalancesByUUID())) {
              balStmt.setString(1, uuid);
              ResultSet balRs = balStmt.executeQuery();
              while (balRs.next()) {
                balances.put(balRs.getString("currency_id"), balRs.getBigDecimal(KEY_AMOUNT));
              }
            }
            Map<String, Object> accountMap = new LinkedHashMap<>();
            accountMap.put("uuid", uuid);
            accountMap.put("player_name", playerName);
            accountMap.put("balances", balances);
            accountsList.add(accountMap);
          }
        }

        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("backup_uuid", backupUUID.toString());
        backup.put("created_at", Instant.now().toString());
        backup.put("account_count", accountsList.size());
        backup.put("accounts", accountsList);

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        Path backupFile = backupDir.resolve(backupUUID + ".json");
        Files.writeString(backupFile, gson.toJson(backup), StandardCharsets.UTF_8);

        if (UltraEconomy.config.isDebug()) {
          UltraEconomy.LOGGER.info("SQL backup created: {} ({} accounts)", backupUUID, accountsList.size());
        }
      } catch (Exception e) {
        UltraEconomy.LOGGER.error("Error creating SQL backup", e);
      }
      cleanOldBackUps();
    });
  }

  @Override
  public void loadBackUp(UUID backupUUID) {
    UltraEconomy.runAsync(() -> {
      Path backupFile = Path.of(UltraEconomy.PATH, "backups", "sql", backupUUID + ".json");
      if (!Files.exists(backupFile)) {
        UltraEconomy.LOGGER.warn("Backup file not found: {}", backupUUID);
        return;
      }
      try {
        String json = Files.readString(backupFile, StandardCharsets.UTF_8);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        @SuppressWarnings("unchecked")
        Map<String, Object> backup = gson.fromJson(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) backup.get("accounts");

        if (accounts == null) {
          UltraEconomy.LOGGER.error("Backup corrupted: no accounts found in {}", backupUUID);
          return;
        }

        try (Connection conn = dataSource.getConnection()) {
          conn.setAutoCommit(false);
          try {
            try (Statement stmt = conn.createStatement()) {
              stmt.executeUpdate("DELETE FROM balances");
              stmt.executeUpdate("DELETE FROM accounts");
            }

            for (Map<String, Object> accountMap : accounts) {
              String uuid = (String) accountMap.get("uuid");
              String playerName = (String) accountMap.get("player_name");

              try (PreparedStatement stmt = conn.prepareStatement(SQLSentences.insertAccount())) {
                stmt.setString(1, uuid);
                stmt.setString(2, playerName);
                stmt.executeUpdate();
              }

              @SuppressWarnings("unchecked")
              Map<String, Object> balances = (Map<String, Object>) accountMap.get("balances");
              if (balances != null) {
                for (Map.Entry<String, Object> entry : balances.entrySet()) {
                  BigDecimal amount = new BigDecimal(entry.getValue().toString());
                  try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.insertBalance())) {
                    balStmt.setString(1, uuid);
                    balStmt.setString(2, entry.getKey());
                    balStmt.setBigDecimal(3, amount);
                    balStmt.executeUpdate();
                  }
                }
              }
            }
            conn.commit();
            DatabaseFactory.ACCOUNTS.invalidateAll();
            UltraEconomy.LOGGER.info("SQL backup restored: {} ({} accounts)", backupUUID, accounts.size());
          } catch (SQLException e) {
            conn.rollback();
            throw e;
          }
        }
      } catch (Exception e) {
        UltraEconomy.LOGGER.error("Error restoring SQL backup: {}", backupUUID, e);
      }
    });
  }

  @Override
  protected void cleanOldBackUps() {
    try {
      Path backupDir = Path.of(UltraEconomy.PATH, "backups", "sql");
      if (!Files.exists(backupDir)) return;

      long retentionMillis = UltraEconomy.config.getRetentionBackUps().toMillis();
      Instant cutoff = Instant.now().minusMillis(retentionMillis);

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "*.json")) {
        for (Path file : stream) {
          Instant modified = Files.getLastModifiedTime(file).toInstant();
          if (modified.isBefore(cutoff)) {
            Files.delete(file);
            if (UltraEconomy.config.isDebug()) {
              UltraEconomy.LOGGER.info("Deleted old SQL backup: {}", file.getFileName());
            }
          }
        }
      }
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Error cleaning old SQL backups", e);
    }
  }

  @Override
  public List<BackupInfo> getBackups() {
    List<BackupInfo> backups = new ArrayList<>();
    Path backupDir = Path.of(UltraEconomy.PATH, "backups", "sql");
    if (!Files.exists(backupDir)) return backups;

    com.google.gson.Gson gson = new com.google.gson.Gson();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "*.json")) {
      for (Path file : stream) {
        try {
          String json = Files.readString(file, StandardCharsets.UTF_8);
          @SuppressWarnings("unchecked")
          Map<String, Object> data = gson.fromJson(json, Map.class);
          BackupInfo info = BackupInfo.builder()
            .backupUUID(UUID.fromString((String) data.get("backup_uuid")))
            .createdAt(Instant.parse((String) data.get("created_at")))
            .accountCount(((Number) data.get("account_count")).intValue())
            .transactionCount(0)
            .build();
          backups.add(info);
        } catch (Exception e) {
          UltraEconomy.LOGGER.warn("Could not read backup file: {}", file.getFileName(), e);
        }
      }
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Error listing SQL backups", e);
    }

    backups.sort(Comparator.comparing(BackupInfo::getCreatedAt).reversed());
    return backups;
  }

  /**
   * Returns all accounts with their full balances using a single JOIN query to avoid N+1 queries.
   */
  @Override
  public List<Account> getAccounts(int limit, int page) {
    List<Account> accounts = new ArrayList<>();
    int offset = Math.max(0, page - 1) * limit;

    // Single JOIN — one round-trip instead of 1 + N
    String query = """
      SELECT a.uuid, a.player_name, b.currency_id, b.amount
      FROM accounts a
      LEFT JOIN balances b ON a.uuid = b.account_uuid
      ORDER BY a.uuid
      LIMIT ? OFFSET ?
      """;

    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setInt(1, limit * 10); // Over-fetch to account for multiple balance rows per account
      stmt.setInt(2, offset);
      ResultSet rs = stmt.executeQuery();

      Map<UUID, Account> accountMap = new LinkedHashMap<>();

      while (rs.next()) {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        if (!accountMap.containsKey(uuid)) {
          if (accountMap.size() >= limit) break; // Collected enough distinct accounts
          String playerName = rs.getString("player_name");
          accountMap.put(uuid, new Account(uuid, playerName, new HashMap<>()));
        }
        String currencyId = rs.getString("currency_id");
        BigDecimal amount = rs.getBigDecimal(KEY_AMOUNT);
        if (currencyId != null && amount != null) {
          accountMap.get(uuid).getBalances().put(currencyId, amount);
        }
      }
      accounts.addAll(accountMap.values());
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error fetching accounts", e);
    }

    return accounts;
  }

  @Override
  public List<Transaction> getTransactions(UUID uuid, int limit) {
    List<Transaction> transactions = new ArrayList<>();
    String query = "SELECT id, currency_id, amount, type, timestamp, processed FROM transactions WHERE account_uuid = ? ORDER BY timestamp DESC LIMIT ?";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, uuid.toString());
      stmt.setInt(2, limit);
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        String currencyId = rs.getString("currency_id");
        BigDecimal amount = rs.getBigDecimal(KEY_AMOUNT);
        TransactionType type = TransactionType.valueOf(rs.getString("type"));
        Timestamp timestamp = rs.getTimestamp("timestamp");
        boolean processed = rs.getBoolean("processed");

        transactions.add(Transaction.builder()
          .accountUUID(uuid)
          .amount(amount)
          .currency(currencyId)
          .type(type)
          .processed(processed)
          .timestamp(timestamp.toInstant())
          .build());
      }
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error fetching transactions for {}", uuid, e);
    }

    return transactions;
  }

  @Override
  public Account getAccountByName(String name) {
    String query = "SELECT uuid, player_name FROM accounts WHERE player_name = ?";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, name);
      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        Map<String, BigDecimal> balances = new HashMap<>();

        try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.selectBalancesByUUID())) {
          balStmt.setString(1, uuid.toString());
          ResultSet balRs = balStmt.executeQuery();
          while (balRs.next()) {
            balances.put(balRs.getString("currency_id"), balRs.getBigDecimal(KEY_AMOUNT));
          }
        }

        Account account = new Account(uuid, name, balances);
        DatabaseFactory.ACCOUNTS.put(uuid, account);
        return account;
      }
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error fetching account by name {}", name, e);
    }

    return null;
  }


  private void checkAndApplyTransactions() {
    if (!runningTransactions) return;
    if (UltraEconomy.server == null) return;

    var players = UltraEconomy.server.getPlayerManager().getPlayerList();
    if (players.isEmpty()) return;

    List<String> onlineUUIDs = players.stream()
      .map(p -> p.getUuidAsString())
      .toList();

    String placeholders = String.join(",", Collections.nCopies(onlineUUIDs.size(), "?"));
    String selectSQL = "SELECT id, account_uuid, currency_id, amount, type FROM transactions " +
      "WHERE processed = FALSE AND account_uuid IN (" + placeholders + ") ORDER BY id";

    // Row-level locking for MySQL/MariaDB/H2 prevents duplicate processing across servers.
    // SQLite uses file-level locking (single-server only) so FOR UPDATE is not needed.
    if (dbType != DataBaseType.SQLITE) {
      selectSQL += " FOR UPDATE";
    }

    Set<UUID> modifiedAccounts = new HashSet<>();

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
        for (int i = 0; i < onlineUUIDs.size(); i++) {
          stmt.setString(i + 1, onlineUUIDs.get(i));
        }
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
          long id = rs.getLong("id");
          UUID uuid = UUID.fromString(rs.getString("account_uuid"));
          Account account = DatabaseFactory.ACCOUNTS.getIfPresent(uuid);
          if (account == null) continue;

          String currencyId = rs.getString("currency_id");
          Currency currency = Currencies.getCurrency(currencyId);
          BigDecimal amount = rs.getBigDecimal(KEY_AMOUNT);

          TransactionType type;
          try {
            type = TransactionType.valueOf(rs.getString("type"));
          } catch (IllegalArgumentException e) {
            UltraEconomy.LOGGER.warn("Invalid transaction type for ID {}, marking processed.", id);
            try (PreparedStatement update = conn.prepareStatement(SQLSentences.markTransactionProcessed())) {
              update.setLong(1, id);
              update.executeUpdate();
            }
            continue;
          }

          // Apply directly to Account to avoid creating duplicate transaction records.
          switch (type) {
            case DEPOSIT -> account.addBalance(currency, amount);
            case WITHDRAW -> {
              if (!account.removeBalance(currency, amount) && UltraEconomy.config.isDebug()) {
                UltraEconomy.LOGGER.warn("Insufficient funds for pending WITHDRAW ID {} (uuid={}, amount={})",
                  id, uuid, amount);
              }
            }
            case SET -> account.setBalance(currency, amount);
            default -> {
              UltraEconomy.LOGGER.warn("Unhandled transaction type {} for ID {}", type, id);
              continue;
            }
          }

          // Mark as processed inside the same DB transaction (still holding the row lock)
          try (PreparedStatement update = conn.prepareStatement(SQLSentences.markTransactionProcessed())) {
            update.setLong(1, id);
            update.executeUpdate();
          }

          modifiedAccounts.add(uuid);
        }
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      UltraEconomy.LOGGER.error("Error processing transactions", e);
    }

    for (UUID uuid : modifiedAccounts) {
      Account account = DatabaseFactory.ACCOUNTS.getIfPresent(uuid);
      if (account != null && account.isDirty()) {
        saveOrUpdateAccount(account);
      }
    }
  }

  private void initTables(DataBaseType type) throws SQLException {
    try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {

      String accountTable = switch (type) {
        case SQLITE -> """
          CREATE TABLE IF NOT EXISTS accounts (
              uuid TEXT PRIMARY KEY,
              player_name TEXT NOT NULL
          )
          """;
        case MYSQL, MARIADB, H2 -> """
          CREATE TABLE IF NOT EXISTS accounts (
              uuid VARCHAR(36) PRIMARY KEY,
              player_name VARCHAR(64) NOT NULL
          )
          """;
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(accountTable);

      String balanceTable = switch (type) {
        case SQLITE -> """
          CREATE TABLE IF NOT EXISTS balances (
              account_uuid TEXT NOT NULL,
              currency_id TEXT NOT NULL,
              amount TEXT NOT NULL,
              PRIMARY KEY(account_uuid, currency_id),
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        case MYSQL, MARIADB, H2 -> """
          CREATE TABLE IF NOT EXISTS balances (
              account_uuid VARCHAR(36) NOT NULL,
              currency_id VARCHAR(64) NOT NULL,
              amount DECIMAL(36,18) NOT NULL,
              PRIMARY KEY(account_uuid, currency_id),
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(balanceTable);

      String transactionTable = switch (type) {
        case SQLITE -> """
          CREATE TABLE IF NOT EXISTS transactions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              account_uuid TEXT NOT NULL,
              currency_id TEXT NOT NULL,
              amount TEXT NOT NULL,
              type TEXT NOT NULL,
              timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
              processed INTEGER DEFAULT 0,
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        case MYSQL, MARIADB, H2 -> """
          CREATE TABLE IF NOT EXISTS transactions (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              account_uuid VARCHAR(36) NOT NULL,
              currency_id VARCHAR(64) NOT NULL,
              amount DECIMAL(36,18) NOT NULL,
              type VARCHAR(10) NOT NULL,
              timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              processed BOOLEAN DEFAULT FALSE,
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(transactionTable);
    }
  }


  private void createIndexes() {
    UltraEconomy.runAsync(() -> {
      try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_balances_currency_amount ON balances(currency_id, amount DESC)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_processed ON transactions(account_uuid, processed)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_currency ON transactions(account_uuid, currency_id)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_type_account ON transactions(\"type\", account_uuid)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_timestamp ON transactions(\"timestamp\")");
      } catch (SQLException e) {
        UltraEconomy.LOGGER.error("Error creating indexes", e);
      }
    });
  }



  @Override
  public boolean isConnected() {
    try (Connection conn = dataSource.getConnection()) {
      return conn != null && !conn.isClosed();
    } catch (SQLException e) {
      return false;
    }
  }
}
