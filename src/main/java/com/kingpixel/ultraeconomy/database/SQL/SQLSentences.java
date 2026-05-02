package com.kingpixel.ultraeconomy.database.SQL;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centraliza todas las sentencias SQL según el motor de base de datos.
 * Esto evita duplicación y errores de sintaxis entre H2, SQLite y MySQL/MariaDB.
 *
 * @author Carlos
 */
public class SQLSentences {

  private static DataBaseType getType() {
    return ((SQLClient) DatabaseFactory.INSTANCE).getDbType();
  }

  @lombok.Data
  public static class Data {
    public ExecutorService service;
    public HikariDataSource dataSource;

    public Data(ExecutorService service, HikariDataSource dataSource) {
      this.service = service;
      this.dataSource = dataSource;
    }
  }

  public static Data configure() throws ClassNotFoundException {
    HikariConfig hikariConfig = new HikariConfig();
    DataBaseConfig config = UltraEconomy.config.getDatabase();
    ExecutorService service;
    HikariDataSource dataSource;
    switch (getType()) {
      case SQLITE -> {
        Class.forName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl(config.getUrl()); // Use the URL from config, not hardcoded path
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.addDataSourceProperty("journal_mode", "WAL");

        dataSource = new HikariDataSource(hikariConfig);
        service = Executors.newSingleThreadExecutor(r -> {
          Thread t = new Thread(r, "SQLite-Worker");
          t.setDaemon(true);
          return t;
        });
      }
      case MYSQL, MARIADB -> {
        Class.forName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUser());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setAutoCommit(true);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(hikariConfig);
        service = Executors.newFixedThreadPool(4, r -> {
          Thread t = new Thread(r, "MySQL-Worker-UltraEconomy-%d");
          t.setDaemon(true);
          return t;
        });
      }
      case H2 -> {
        Class.forName("org.h2.Driver");
        // Usamos MODE=MySQL para compatibilidad con la sintaxis
        hikariConfig.setJdbcUrl("jdbc:h2:./config/ultraeconomy/database;MODE=MySQL;DATABASE_TO_UPPER=false");
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);

        dataSource = new HikariDataSource(hikariConfig);
        service = Executors.newSingleThreadExecutor(r -> {
          Thread t = new Thread(r, "H2-Worker");
          t.setDaemon(true);
          return t;
        });
      }
      default -> throw new IllegalArgumentException("Unsupported database type: " + config.getType());
    }
    return new Data(service, dataSource);
  }

  // ========================
  // ACCOUNTS
  // ========================
  public static String insertAccount() {
    return switch (getType()) {
      case H2 -> "MERGE INTO accounts (uuid, player_name) KEY (uuid) VALUES (?, ?)";
      case SQLITE -> "INSERT INTO accounts (uuid, player_name) VALUES (?, ?) " +
        "ON CONFLICT(uuid) DO UPDATE SET player_name=excluded.player_name";
      case MYSQL, MARIADB -> "INSERT INTO accounts (uuid, player_name) VALUES (?, ?) " +
        "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name)";
      default -> throw new IllegalArgumentException("Unsupported DB type for insertAccount: " + getType());
    };
  }

  // ========================
  // BALANCES
  // ========================
  public static String insertBalance() {
    return switch (getType()) {
      case H2 -> "MERGE INTO balances (account_uuid, currency_id, amount) " +
        "KEY (account_uuid, currency_id) VALUES (?, ?, ?)";
      case SQLITE -> "INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) " +
        "ON CONFLICT(account_uuid, currency_id) DO UPDATE SET amount=excluded.amount";
      case MYSQL, MARIADB -> "INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE amount=VALUES(amount)";
      default -> throw new IllegalArgumentException("Unsupported DB type for insertBalance: " + getType());
    };
  }

  // ========================
  // TRANSACTIONS
  // ========================
  public static String insertTransaction() {
    return "INSERT INTO transactions (account_uuid, currency_id, amount, type, processed) VALUES (?, ?, ?, ?, ?)";
    // Igual en todos los motores
  }

  public static String markTransactionProcessed() {
    return "UPDATE transactions SET processed=TRUE WHERE id=?";
    // Igual en todos los motores
  }

  public static String selectPendingTransactions() {
    return "SELECT id, account_uuid, currency_id, amount, type FROM transactions WHERE processed=FALSE";
    // Igual en todos los motores
  }

  // ========================
  // QUERIES AUXILIARES
  // ========================
  public static String selectAccountByUUID() {
    return "SELECT uuid, player_name FROM accounts WHERE uuid=?";
  }

  public static String selectBalancesByUUID() {
    return "SELECT currency_id, amount FROM balances WHERE account_uuid=?";
  }

  public static String selectTopBalances() {
    return """
      SELECT a.uuid, a.player_name, b.amount
      FROM accounts a
      JOIN balances b ON a.uuid=b.account_uuid
      WHERE b.currency_id=?
      ORDER BY CAST(b.amount AS DECIMAL) DESC
      LIMIT ? OFFSET ?
      """;
  }
}
