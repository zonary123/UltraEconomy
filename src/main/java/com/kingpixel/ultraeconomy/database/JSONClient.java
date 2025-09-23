package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.models.Account;

import java.io.File;
import java.math.BigDecimal;
import java.util.UUID;

public class JSONClient extends DatabaseClient {
  private static final String PATH = "config/ultraeconomy/accounts/";

  @Override
  public void connect(DataBaseConfig config) {
    CobbleUtils.LOGGER.info("Using JSON database at " + PATH);
  }

  @Override
  public void disconnect() {
    CobbleUtils.LOGGER.info("JSON database does not require disconnection.");
  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account account = DatabaseFactory.accounts.getIfPresent(uuid);
    if (account != null) return account;
    File accountFile = Utils.getAbsolutePath(PATH + uuid + ".json");
    if (accountFile.exists()) {
      account = Utils.newWithoutSpacingGson().fromJson(Utils.newWithoutSpacingGson().toJson(accountFile), Account.class);
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        CobbleUtils.LOGGER.info("Creating new account for " + player.getName().getString());
      } else {
        CobbleUtils.LOGGER.info("Creating new account for " + uuid);
        return null;
      }
      account = new Account(player);
      Utils.writeFileAsync(accountFile, Utils.newWithoutSpacingGson().toJson(account));
    }
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    File accountFile = Utils.getAbsolutePath(PATH + account.getPlayerUUID() + ".json");
    Utils.writeFileAsync(accountFile, Utils.newWithoutSpacingGson().toJson(account));
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

}
