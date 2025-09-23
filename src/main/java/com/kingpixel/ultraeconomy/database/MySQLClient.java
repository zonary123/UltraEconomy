package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MySQLClient extends DatabaseClient {

  private Connection connection;

  // Cache con expiración automática
  public static final Cache<UUID, Account> accounts = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS) // tras 30s sin acceso, se invalida
    .maximumSize(10_000)
    .build();

  @Override
  public void connect(DataBaseConfig config) {
    try {
      connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
      CobbleUtils.LOGGER.info("Connected to MySQL database at " + config.getUrl());

      try (Statement stmt = connection.createStatement()) {
        // Tabla de cuentas
        stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS accounts (" +
            "uuid VARCHAR(36) PRIMARY KEY," +
            "player_name VARCHAR(64) NOT NULL" +
            ")"
        );

        // Tabla de balances
        stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS balances (" +
            "account_uuid VARCHAR(36) NOT NULL," +
            "currency_id VARCHAR(64) NOT NULL," +
            "amount DECIMAL(36,18) NOT NULL," +
            "PRIMARY KEY (account_uuid, currency_id)," +
            "FOREIGN KEY (account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE" +
            ")"
        );
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to connect to MySQL database", e);
    }
  }

  @Override
  public void disconnect() {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
        CobbleUtils.LOGGER.info("Disconnected from MySQL database.");
      }
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error disconnecting MySQL database");
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

  @Override
  public Account getAccount(UUID uuid) {
    // Revisa la cache primero
    Account cached = accounts.getIfPresent(uuid);
    if (cached != null) return cached;

    try (PreparedStatement stmt = connection.prepareStatement(
      "SELECT uuid, player_name FROM accounts WHERE uuid = ?")) {
      stmt.setString(1, uuid.toString());
      ResultSet rs = stmt.executeQuery();

      Account account;
      if (rs.next()) {
        // Recuperar balances
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
        // Crear nueva cuenta si no existe
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
          "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name)")) {
        stmt.setString(1, account.getPlayerUUID().toString());
        stmt.setString(2, account.getPlayerName());
        stmt.executeUpdate();
      }

      // Guardar balances
      for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
        try (PreparedStatement stmt = connection.prepareStatement(
          "INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE amount = VALUES(amount)")) {
          stmt.setString(1, account.getPlayerUUID().toString());
          stmt.setString(2, entry.getKey());
          stmt.setBigDecimal(3, entry.getValue());
          stmt.executeUpdate();
        }
      }

      connection.commit();
      connection.setAutoCommit(true);

      // Refrescar cache
      accounts.put(account.getPlayerUUID(), account);

    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException ex) {
        CobbleUtils.LOGGER.error("Failed to rollback transaction");
      }
      throw new RuntimeException("Error saving account " + account.getPlayerUUID(), e);
    }
  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    boolean result = account.addBalance(currency, amount);
    saveOrUpdateAccount(account);
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    boolean result = account.removeBalance(currency, amount);
    saveOrUpdateAccount(account);
    return result;
  }

  @Override
  public BigDecimal getBalance(UUID uuid, String currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    BigDecimal result = account.setBalance(currency, amount);
    saveOrUpdateAccount(account);
    return result;
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override public List<Account> getTopBalances(String currency, int page) {
    return List.of();
  }
}
