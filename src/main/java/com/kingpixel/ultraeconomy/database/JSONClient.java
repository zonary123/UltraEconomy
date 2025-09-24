package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class JSONClient extends DatabaseClient {
  private static final String PATH = UltraEconomy.PATH + "/accounts/";

  @Override
  public void connect(DataBaseConfig config) {
    Utils.getAbsolutePath(PATH).mkdirs();
    CobbleUtils.LOGGER.info("Using JSON database at " + PATH);
  }

  @Override
  public void disconnect() {
    CobbleUtils.LOGGER.info("JSON database does not require disconnection.");
  }

  @Override public void invalidate(UUID playerUUID) {
    DatabaseFactory.accounts.invalidate(playerUUID);
  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account account = DatabaseFactory.accounts.getIfPresent(uuid);
    if (account != null) return account;
    File accountFile = Utils.getAbsolutePath(PATH + uuid.toString() + ".json");
    if (accountFile.exists()) {
      try {
        String data = Utils.readFileSync(accountFile);
        account = Utils.newWithoutSpacingGson().fromJson(data, Account.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        CobbleUtils.LOGGER.info("Creating new account for " + player.getName().getString());
      } else {
        CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid + ", account creation failed.");
        return null;
      }
      account = new Account(player);
      saveOrUpdateAccount(account);
    }
    DatabaseFactory.accounts.put(uuid, account);
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    String data = Utils.newWithoutSpacingGson().toJson(account, Account.class);
    File accountFile = Utils.getAbsolutePath(PATH + account.getPlayerUUID().toString() + ".json");
    Utils.writeFileAsync(accountFile, data);
  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).addBalance(currency, amount);
  }

  @Override
  public boolean removeBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).removeBalance(currency, amount);
  }

  @Override
  public BigDecimal getBalance(UUID uuid, String currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).setBalance(currency, amount);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override public List<Account> getTopBalances(String currency, int page) {
    return List.of();
  }

}
