package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.util.UUID;

public class MongoDBClient extends DatabaseClient {
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
    return null;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {

  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    return false;
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
