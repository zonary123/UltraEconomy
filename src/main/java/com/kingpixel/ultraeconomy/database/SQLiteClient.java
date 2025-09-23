package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLiteClient extends DatabaseClient {

  private static final String DB_PATH = UltraEconomy.PATH + "/ultraeconomy.db";
  private Connection connection;

  @Override
  public void connect(DataBaseConfig config) {
    try {

      connection = DriverManager.getConnection(config.getUrl());
      CobbleUtils.LOGGER.info("Connected to SQLite database at " + DB_PATH);

      try (Statement stmt = connection.createStatement()) {
        // Tabla de cuentas
        stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS accounts (" +
            "uuid TEXT PRIMARY KEY," +
            "player_name TEXT NOT NULL" +
            ")"
        );

        // Tabla de balances
        stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS balances (" +
            "account_uuid TEXT NOT NULL," +
            "currency_id TEXT NOT NULL," +
            "amount TEXT NOT NULL," +
            "PRIMARY KEY (account_uuid, currency_id)," +
            "FOREIGN KEY (account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE)"
        );
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to connect to SQLite database", e);
    }
  }

  @Override
  public void disconnect() {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
        CobbleUtils.LOGGER.info("Disconnected from SQLite database.");
      }
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error disconnecting SQLite database");
      e.printStackTrace();
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
    Account cached = DatabaseFactory.accounts.getIfPresent(uuid);
    if (cached != null) return cached;

    try (PreparedStatement stmt = connection.prepareStatement(
      "SELECT uuid, player_name FROM accounts WHERE uuid = ?")) {
      stmt.setString(1, uuid.toString());
      ResultSet rs = stmt.executeQuery();

      Account account;
      if (rs.next()) {
        String playerName = rs.getString("player_name");

        // Recuperar balances
        Map<String, BigDecimal> balances = new HashMap<>();
        try (PreparedStatement balStmt = connection.prepareStatement(
          "SELECT currency_id, amount FROM balances WHERE account_uuid = ?")) {
          balStmt.setString(1, uuid.toString());
          ResultSet balRs = balStmt.executeQuery();
          while (balRs.next()) {
            balances.put(
              balRs.getString("currency_id"),
              new BigDecimal(balRs.getString("amount"))
            );
          }
        }
        account = new Account(uuid, playerName, balances);
      } else {
        // Crear nueva cuenta
        var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
          CobbleUtils.LOGGER.info("Creating new account for " + player.getName().getString());
          account = new Account(player); // este constructor ya llena UUID + Name
          saveOrUpdateAccount(account);
        } else {
          CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid + ", account creation failed.");
          return null;
        }
      }

      DatabaseFactory.accounts.put(uuid, account);
      return account;

    } catch (SQLException e) {
      throw new RuntimeException("Error fetching account " + uuid, e);
    }
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    CompletableFuture.runAsync(() -> {
        try {
          connection.setAutoCommit(false);

          try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO accounts (uuid, player_name) VALUES (?, ?) " +
              "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name")) {
            stmt.setString(1, account.getPlayerUUID().toString());
            stmt.setString(2, account.getPlayerName());
            stmt.executeUpdate();
          }

          // Guardar balances
          for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
            try (PreparedStatement stmt = connection.prepareStatement(
              "INSERT INTO balances (account_uuid, currency_id, amount) VALUES (?, ?, ?) " +
                "ON CONFLICT(account_uuid, currency_id) DO UPDATE SET amount = excluded.amount")) {
              stmt.setString(1, account.getPlayerUUID().toString());
              stmt.setString(2, entry.getKey());
              stmt.setString(3, entry.getValue().toPlainString());
              stmt.executeUpdate();
            }
          }

          connection.commit();
          connection.setAutoCommit(true);

        } catch (SQLException e) {
          try {
            connection.rollback();
          } catch (SQLException ex) {
            CobbleUtils.LOGGER.error("Failed to rollback transaction");
            ex.printStackTrace();
          }
          throw new RuntimeException("Error saving account " + account.getPlayerUUID(), e);
        }
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
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
    BigDecimal result = getAccount(uuid).setBalance(currency, amount);
    saveOrUpdateAccount(getAccount(uuid));
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
