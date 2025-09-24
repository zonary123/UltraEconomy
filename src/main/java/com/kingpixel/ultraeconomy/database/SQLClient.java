package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SQLClient extends DatabaseClient {

  private HikariDataSource dataSource;
  private ScheduledExecutorService transactionExecutor;
  private ExecutorService asyncExecutor;
  private boolean runningTransactions = false;

  public static final Cache<UUID, Account> ACCOUNT_CACHE = Caffeine.newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .maximumSize(10_000)
    .removalListener((key, value, cause) -> {
      if (cause.equals(RemovalCause.REPLACED)) return;
      if (UltraEconomy.config.isDebug())
        CobbleUtils.LOGGER.info("Account with UUID " + key + " removed due to " + cause);
      if (value != null)
        DatabaseFactory.INSTANCE.saveOrUpdateAccount((Account) value);
    }).build();

  @Override
  public void connect(DataBaseConfig config) {
    try {
      switch (config.getType()) {
        case SQLITE -> {
          Class.forName("org.sqlite.JDBC");
          HikariConfig hikariConfig = new HikariConfig();
          hikariConfig.setJdbcUrl("jdbc:sqlite:config/ultraeconomy/database.db");
          hikariConfig.setMaximumPoolSize(1);
          hikariConfig.setMinimumIdle(1);
          hikariConfig.setConnectionTimeout(5000);
          hikariConfig.setIdleTimeout(300_000);
          hikariConfig.setLeakDetectionThreshold(60_000);
          hikariConfig.addDataSourceProperty("journal_mode", "WAL");
          dataSource = new HikariDataSource(hikariConfig);

          asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SQLite-Worker");
            t.setDaemon(true);
            return t;
          });
        }
        case MYSQL, MARIADB -> {
          Class.forName("com.mysql.cj.jdbc.Driver");
          HikariConfig hikariConfig = new HikariConfig();
          hikariConfig.setJdbcUrl(config.getUrl());
          hikariConfig.setUsername(config.getUser());
          hikariConfig.setPassword(config.getPassword());
          hikariConfig.setMaximumPoolSize(10);
          hikariConfig.setMinimumIdle(5);
          hikariConfig.setConnectionTimeout(10_000);
          hikariConfig.setIdleTimeout(600_000);
          hikariConfig.setLeakDetectionThreshold(60_000);
          hikariConfig.setAutoCommit(true);
          hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
          hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
          hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
          hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
          dataSource = new HikariDataSource(hikariConfig);

          asyncExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "MySQL-Worker-UltraEconomy-%d");
            t.setDaemon(true);
            return t;
          });
        }
        default -> throw new IllegalArgumentException("Unsupported database type: " + config.getType());
      }

      CobbleUtils.LOGGER.info("Connected to " + config.getType() + " database at " + config.getUrl());
      initTables(config.getType());
      ensureProcessedColumnExists();
      createIndexes(config.getType());

      transactionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Transaction-Worker");
        t.setDaemon(true);
        return t;
      });
      runningTransactions = true;
      transactionExecutor.scheduleAtFixedRate(this::checkAndApplyTransactions, 0, 2, TimeUnit.SECONDS);

    } catch (Exception e) {
      throw new RuntimeException("Failed to connect to database: " + config.getType(), e);
    }
  }

  @Override
  public void disconnect() {
    runningTransactions = false;
    if (transactionExecutor != null) transactionExecutor.shutdownNow();
    if (asyncExecutor != null) asyncExecutor.shutdownNow();
    if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    CobbleUtils.LOGGER.info("Disconnected from database.");
  }

  @Override
  public void invalidate(UUID playerUUID) {
    ACCOUNT_CACHE.invalidate(playerUUID);
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = ACCOUNT_CACHE.getIfPresent(uuid);
    if (cached != null) return cached;

    try (Connection conn = dataSource.getConnection()) {
      Account account;
      try (PreparedStatement stmt = conn.prepareStatement("SELECT uuid, player_name FROM accounts WHERE uuid=?")) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
          Map<String, BigDecimal> balances = new HashMap<>();
          try (PreparedStatement balStmt = conn.prepareStatement("SELECT currency_id, amount FROM balances WHERE account_uuid=?")) {
            balStmt.setString(1, uuid.toString());
            ResultSet balRs = balStmt.executeQuery();
            while (balRs.next())
              balances.put(balRs.getString("currency_id"), balRs.getBigDecimal("amount"));
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
      ACCOUNT_CACHE.put(uuid, account);
      return account;
    } catch (SQLException e) {
      throw new RuntimeException("Error fetching account " + uuid, e);
    }
  }

  public void getAccountAsync(UUID uuid, Consumer<Account> callback) {
    asyncExecutor.submit(() -> callback.accept(getAccount(uuid)));
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    asyncExecutor.submit(() -> {
      try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try (PreparedStatement stmt = conn.prepareStatement(
          "INSERT INTO accounts (uuid, player_name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET player_name=excluded.player_name")) {
          stmt.setString(1, account.getPlayerUUID().toString());
          stmt.setString(2, account.getPlayerName());
          stmt.executeUpdate();
        }
        for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
          try (PreparedStatement balStmt = conn.prepareStatement(
            "INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) ON CONFLICT(account_uuid,currency_id) DO UPDATE SET amount=excluded.amount")) {
            balStmt.setString(1, account.getPlayerUUID().toString());
            balStmt.setString(2, entry.getKey());
            balStmt.setBigDecimal(3, entry.getValue());
            balStmt.executeUpdate();
          }
        }
        conn.commit();
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error saving account " + account.getPlayerUUID());
        e.printStackTrace();
      }
    });
  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = false;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, false);
    } else {
      result = account.addBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, true);
    }
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = false;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, false);
    } else {
      result = account.removeBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
    }
    return result;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.SET, false);
    } else {
      account.setBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.SET, true);
      saveBalanceSafe(uuid, currency, amount);
    }
    return amount;
  }

  private void saveBalanceSafe(UUID uuid, String currency, BigDecimal amount) {
    asyncExecutor.submit(() -> {
      try {
        saveBalance(uuid, currency, amount);
        ACCOUNT_CACHE.invalidate(uuid);
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error saving balance for " + uuid);
        e.printStackTrace();
      }
    });
  }

  @Override
  public BigDecimal getBalance(UUID uuid, String currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(String currency, int page) {
    List<Account> topAccounts = new ArrayList<>();
    int pageSize = UltraEconomy.config.getLimitTopPlayers();
    int offset = (page - 1) * pageSize;

    String query = "SELECT a.uuid, a.player_name, b.amount FROM accounts a JOIN balances b ON a.uuid=b.account_uuid WHERE b.currency_id=? ORDER BY CAST(b.amount AS DECIMAL) DESC LIMIT ? OFFSET ?";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, currency);
      stmt.setInt(2, pageSize);
      stmt.setInt(3, offset);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String playerName = rs.getString("player_name");
        BigDecimal amount = rs.getBigDecimal("amount");

        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put(currency, amount);
        Account account = new Account(uuid, playerName, balances);
        topAccounts.add(account);
      }
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error fetching top balances");
      e.printStackTrace();
    }

    return topAccounts;
  }

  private void addTransaction(UUID uuid, String currency, BigDecimal amount, TransactionType type, boolean processed) {
    asyncExecutor.submit(() -> {
      String query = "INSERT INTO transactions (account_uuid, currency_id, amount, type, processed) VALUES (?, ?, ?, ?, ?)";
      try (Connection conn = dataSource.getConnection();
           PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, uuid.toString());
        stmt.setString(2, currency);
        stmt.setBigDecimal(3, amount);
        stmt.setString(4, type.name());
        stmt.setBoolean(5, processed);
        stmt.executeUpdate();
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error adding transaction for " + uuid);
        e.printStackTrace();
      }
    });
  }

  private void checkAndApplyTransactions() {
    if (!runningTransactions) return;

    asyncExecutor.submit(() -> {
      try (Connection conn = dataSource.getConnection();
           PreparedStatement stmt = conn.prepareStatement("SELECT id, account_uuid, currency_id, amount, type FROM transactions WHERE processed=FALSE")) {

        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          UUID uuid = UUID.fromString(rs.getString("account_uuid"));
          Account account = ACCOUNT_CACHE.getIfPresent(uuid);
          if (account == null) continue;

          long id = rs.getLong("id");
          String currency = rs.getString("currency_id");
          BigDecimal amount = rs.getBigDecimal("amount");
          TransactionType type = rs.getString("type") != null ? TransactionType.valueOf(rs.getString("type")) : TransactionType.DEPOSIT;

          switch (type) {
            case DEPOSIT -> account.addBalance(currency, amount);
            case WITHDRAW -> account.removeBalance(currency, amount);
            case SET -> account.setBalance(currency, amount);
          }
          saveBalanceSafe(uuid, currency, account.getBalance(currency));

          try (PreparedStatement update = conn.prepareStatement("UPDATE transactions SET processed=TRUE WHERE id=?")) {
            update.setLong(1, id);
            update.executeUpdate();
          }

          ACCOUNT_CACHE.put(uuid, account);
        }
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error processing transactions");
        e.printStackTrace();
      }
    });
  }

  // Métodos privados para inicialización de tablas, índices y columna processed
  private void initTables(DataBaseType type) throws SQLException {
    try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
      String accountTable = switch (type) {
        case SQLITE -> "CREATE TABLE IF NOT EXISTS accounts (uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL)";
        case MYSQL, MARIADB ->
          "CREATE TABLE IF NOT EXISTS accounts (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL)";
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(accountTable);

      String balanceTable = switch (type) {
        case SQLITE ->
          "CREATE TABLE IF NOT EXISTS balances (account_uuid TEXT NOT NULL, currency_id TEXT NOT NULL, amount TEXT NOT NULL, PRIMARY KEY(account_uuid, currency_id), FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        case MYSQL, MARIADB ->
          "CREATE TABLE IF NOT EXISTS balances (account_uuid VARCHAR(36) NOT NULL, currency_id VARCHAR(64) NOT NULL, amount DECIMAL(36,18) NOT NULL, PRIMARY KEY(account_uuid, currency_id), FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(balanceTable);

      String transactionTable = switch (type) {
        case SQLITE ->
          "CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, account_uuid TEXT NOT NULL, currency_id TEXT NOT NULL, amount TEXT NOT NULL, type TEXT NOT NULL, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        case MYSQL, MARIADB ->
          "CREATE TABLE IF NOT EXISTS transactions (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_uuid VARCHAR(36) NOT NULL, currency_id VARCHAR(64) NOT NULL, amount DECIMAL(36,18) NOT NULL, type VARCHAR(10) NOT NULL, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(transactionTable);
    }
  }

  private void ensureProcessedColumnExists() {
    asyncExecutor.submit(() -> {
      try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("ALTER TABLE transactions ADD COLUMN processed INTEGER DEFAULT 0");
      } catch (SQLException e) {
        if (!e.getMessage().contains("duplicate column")) e.printStackTrace();
      }
    });
  }

  private void createIndexes(DataBaseType type) {
    asyncExecutor.submit(() -> {
      try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_balances_currency_amount ON balances(currency_id, amount DESC)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_processed ON transactions(account_uuid, processed)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_currency ON transactions(account_uuid, currency_id)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_type_account ON transactions(TYPE, account_uuid)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_timestamp ON transactions(TIMESTAMP)");
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });
  }

  private void saveBalance(UUID uuid, String currency, BigDecimal amount) throws SQLException {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement("INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) ON CONFLICT(account_uuid,currency_id) DO UPDATE SET amount=excluded.amount")) {
      stmt.setString(1, uuid.toString());
      stmt.setString(2, currency);
      stmt.setBigDecimal(3, amount);
      stmt.executeUpdate();
    }
  }

  public Account getCachedAccount(UUID uuid) {
    return ACCOUNT_CACHE.getIfPresent(uuid);
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
