package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SQLClient extends DatabaseClient {

  private Connection connection;

  public static final Cache<UUID, Account> accounts = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .maximumSize(10_000)
    .build();
  private ScheduledExecutorService transactionExecutor;
  private boolean runningTransactions = false; // flag opcional

  @Override
  public void connect(DataBaseConfig config) {
    try {
      switch (config.getType()) {
        case SQLITE -> Class.forName("org.sqlite.JDBC");
        case MYSQL, MARIADB -> Class.forName("com.mysql.cj.jdbc.Driver");
        default -> throw new IllegalArgumentException("Unsupported database type: " + config.getType());
      }

      connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
      CobbleUtils.LOGGER.info("Connected to " + config.getType() + " database at " + config.getUrl());
      initTables(config.getType());
      ensureProcessedColumnExists();
      createIndexes(config.getType());
      // Inicializar executor para transacciones
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

  private void createIndexes(DataBaseType type) {
    try (Statement stmt = connection.createStatement()) {

      switch (type) {
        case SQLITE, MYSQL, MARIADB -> {
          // Índices para balances
          stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_balances_currency_amount ON balances(currency_id, amount DESC)");

          // Índices para transacciones
          stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_processed ON transactions(account_uuid, processed)");
          stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_currency ON transactions(account_uuid, currency_id)");
          stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_type_account ON transactions(TYPE, account_uuid)");
          stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_timestamp ON transactions(TIMESTAMP)");
        }
        default -> throw new IllegalArgumentException("Unsupported database type for creating indexes: " + type);
      }

      CobbleUtils.LOGGER.info("Indexes created successfully for " + type);

    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error creating indexes for " + type);
      e.printStackTrace();
    }
  }


  private void ensureProcessedColumnExists() {
    try (Statement stmt = connection.createStatement()) {
      stmt.executeUpdate("ALTER TABLE transactions ADD COLUMN processed INTEGER DEFAULT 0");
    } catch (SQLException e) {
      // Error 1 = columna ya existe, ignorar
      if (!e.getMessage().contains("duplicate column")) {
        e.printStackTrace();
      }
    }
  }


  @Override
  public void disconnect() {
    try {
      if (transactionExecutor != null) {
        runningTransactions = false;
        transactionExecutor.shutdownNow();
      }
      if (connection != null && !connection.isClosed()) {
        connection.close();
        CobbleUtils.LOGGER.info("Disconnected from database.");
      }
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error disconnecting database");
      e.printStackTrace();
    }
  }

  private void checkAndApplyTransactions() {
    if (!runningTransactions) return;

    try {
      String query = "SELECT id, account_uuid, currency_id, amount, type FROM transactions WHERE processed = FALSE";
      try (PreparedStatement stmt = connection.prepareStatement(query)) {
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          UUID uuid = UUID.fromString(rs.getString("account_uuid"));
          Account account = accounts.getIfPresent(uuid);
          if (account == null) continue; // Jugador no está en este servidor, saltar

          long id = rs.getLong("id");
          String currency = rs.getString("currency_id");
          BigDecimal amount = rs.getBigDecimal("amount");
          TransactionType type = rs.getString("type") != null ? TransactionType.valueOf(rs.getString("type")) : TransactionType.DEPOSIT;

          switch (type) {
            case DEPOSIT -> account.addBalance(currency, amount);
            case WITHDRAW -> account.removeBalance(currency, amount);
            case SET -> account.setBalance(currency, amount);
          }
          saveBalance(uuid, currency, account.getBalance(currency));

          try (PreparedStatement update = connection.prepareStatement(
            "UPDATE transactions SET processed = TRUE WHERE id = ?")) {
            update.setLong(1, id);
            update.executeUpdate();
          }

          accounts.put(uuid, account);
        }
      }
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error processing transactions");
      e.printStackTrace();
    }
  }


  private void initTables(DataBaseType type) throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      // Tabla de cuentas
      String accountTable = switch (type) {
        case SQLITE -> "CREATE TABLE IF NOT EXISTS accounts (" +
          "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL)";
        case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS accounts (" +
          "uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL)";
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(accountTable);

      // Tabla de balances
      String balanceTable = switch (type) {
        case SQLITE -> "CREATE TABLE IF NOT EXISTS balances (" +
          "account_uuid TEXT NOT NULL, currency_id TEXT NOT NULL, amount TEXT NOT NULL," +
          "PRIMARY KEY(account_uuid, currency_id)," +
          "FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS balances (" +
          "account_uuid VARCHAR(36) NOT NULL, currency_id VARCHAR(64) NOT NULL, amount DECIMAL(36,18) NOT NULL," +
          "PRIMARY KEY(account_uuid, currency_id)," +
          "FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(balanceTable);

      // Tabla de transacciones
// Tabla de transacciones
      String transactionTable = switch (type) {
        case SQLITE -> "CREATE TABLE IF NOT EXISTS transactions (" +
          "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "account_uuid TEXT NOT NULL, " +
          "currency_id TEXT NOT NULL, " +
          "amount TEXT NOT NULL, " +
          "type TEXT NOT NULL, " +  // nuevo campo para tipo de transacción
          "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
          "FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS transactions (" +
          "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
          "account_uuid VARCHAR(36) NOT NULL, " +
          "currency_id VARCHAR(64) NOT NULL, " +
          "amount DECIMAL(36,18) NOT NULL, " +
          "type VARCHAR(10) NOT NULL, " +  // nuevo campo para tipo de transacción
          "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
          "FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)";
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(transactionTable);

    }
  }


  @Override
  public boolean isConnected() {
    try {
      return connection != null && !connection.isClosed();
    } catch (SQLException e) {
      return false;
    }
  }

  public Account getCachedAccount(UUID uuid) {
    return accounts.getIfPresent(uuid); // solo cache
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = accounts.getIfPresent(uuid);
    if (cached != null) return cached;

    try (PreparedStatement stmt = connection.prepareStatement(
      "SELECT uuid, player_name FROM accounts WHERE uuid = ?")) {
      stmt.setString(1, uuid.toString());
      ResultSet rs = stmt.executeQuery();

      Account account;
      if (rs.next()) {
        Map<String, BigDecimal> balances = new HashMap<>();
        try (PreparedStatement balStmt = connection.prepareStatement(
          "SELECT currency_id, amount FROM balances WHERE account_uuid = ?")) {
          balStmt.setString(1, uuid.toString());
          ResultSet balRs = balStmt.executeQuery();
          while (balRs.next()) {
            balances.put(
              balRs.getString("currency_id"),
              balRs.getBigDecimal("amount")
            );
          }
        }
        account = new Account(uuid, rs.getString("player_name"), balances);
      } else {
        var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
          CobbleUtils.LOGGER.info("Creating new account for " + player.getName().getString());
          account = new Account(player);
          saveOrUpdateAccount(account);
        } else {
          CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid + ", account creation failed.");
          return null;
        }
      }

      accounts.put(uuid, account);
      return account;
    } catch (SQLException e) {
      throw new RuntimeException("Error fetching account " + uuid, e);
    }
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    try {
      connection.setAutoCommit(false);

      // Insertar o actualizar cuenta
      try (PreparedStatement stmt = connection.prepareStatement(
        "INSERT INTO accounts (uuid, player_name) VALUES (?, ?) " +
          "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name")) {
        stmt.setString(1, account.getPlayerUUID().toString());
        stmt.setString(2, account.getPlayerName());
        stmt.executeUpdate();
      }

      // Guardar balances
      for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
        saveBalance(account.getPlayerUUID(), entry.getKey(), entry.getValue());
      }

      connection.commit();
      connection.setAutoCommit(true);

      accounts.put(account.getPlayerUUID(), account);

    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException ex) {
        CobbleUtils.LOGGER.error("Rollback failed");
        ex.printStackTrace();
      }
      throw new RuntimeException("Error saving account " + account.getPlayerUUID(), e);
    }
  }

  private void saveBalance(UUID uuid, String currency, BigDecimal amount) throws SQLException {
    try (PreparedStatement stmt = connection.prepareStatement(
      "INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) " +
        "ON CONFLICT(account_uuid, currency_id) DO UPDATE SET amount = excluded.amount")) {
      stmt.setString(1, uuid.toString());
      stmt.setString(2, currency);
      stmt.setBigDecimal(3, amount);
      stmt.executeUpdate();
    }
  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid); // solo cache
    boolean result = false; // sumar dinero localmente
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
    Account account = getCachedAccount(uuid); // solo cache
    boolean result = false; // sumar dinero localmente
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
    Account account = getCachedAccount(uuid); // solo cache
    if (account == null) {
      // Registration of transaction for later processing
      addTransaction(uuid, currency, amount, TransactionType.SET, false);
    } else {
      account.setBalance(currency, amount);
      // Registration transaction to have a history of transactions
      addTransaction(uuid, currency, amount, TransactionType.SET, true);
      saveBalanceSafe(uuid, currency, amount);
    }
    return amount;
  }

  private void saveBalanceSafe(UUID uuid, String currency, BigDecimal amount) {
    try {
      saveBalance(uuid, currency, amount);
      accounts.invalidate(uuid); // refrescar cache
    } catch (SQLException e) {
      throw new RuntimeException("Error saving balance for " + uuid, e);
    }
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

    String query = "SELECT a.uuid, a.player_name, b.amount " +
      "FROM accounts a " +
      "JOIN balances b ON a.uuid = b.account_uuid " +
      "WHERE b.currency_id = ? " +
      "ORDER BY CAST(b.amount AS DECIMAL) DESC " +
      "LIMIT ? OFFSET ?";

    try (PreparedStatement stmt = connection.prepareStatement(query)) {
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

        accounts.put(uuid, account);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error fetching top balances for currency: " + currency, e);
    }

    return topAccounts;
  }

  private void addTransaction(UUID uuid, String currency, BigDecimal amount, TransactionType type, boolean processed) {
    String query = "INSERT INTO transactions (account_uuid, currency_id, amount, type, processed) VALUES (?, ?, ?, ?," +
      " ?)";
    try (PreparedStatement stmt = connection.prepareStatement(query)) {
      stmt.setString(1, uuid.toString());
      stmt.setString(2, currency);
      stmt.setBigDecimal(3, amount);
      stmt.setString(4, type.name());
      stmt.setBoolean(5, processed);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Error adding transaction for " + uuid, e);
    }
  }


  public void syncAccount(UUID uuid) {
    try {
      Account account = getAccount(uuid);
      String query = "SELECT currency_id, SUM(amount) as total FROM transactions WHERE account_uuid = ? GROUP BY currency_id";
      try (PreparedStatement stmt = connection.prepareStatement(query)) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          String currency = rs.getString("currency_id");
          BigDecimal total = rs.getBigDecimal("total");
          account.setBalance(currency, total);
        }
      }
      saveOrUpdateAccount(account); // actualizar la cuenta con los totales
    } catch (SQLException e) {
      throw new RuntimeException("Error synchronizing account " + uuid, e);
    }
  }


}
