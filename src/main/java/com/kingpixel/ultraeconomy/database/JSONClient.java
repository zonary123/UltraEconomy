package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.util.UUID;

public class JSONClient extends DatabaseClient {
  private static final String PATH = "config/ultraeconomy/accounts/";

  @Override
  public void connect(DataBaseConfig config) {

  }

  @Override
  public void disconnect() {

  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account account = accounts.getIfPresent(uuid);
    if (account != null) return account;


    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {

  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    return account.addBalance(currency, amount);
  }

  @Override
  public BigDecimal getBalance(UUID uuid, String currency) {
    return null;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
    return null;
  }

}
