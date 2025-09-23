package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public abstract class DatabaseClient {
  public static Cache<UUID, Account> accounts = Caffeine
    .newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .maximumSize(1000)
    .removalListener((key, value, cause) -> {
      if (value != null) {
        DatabaseFactory.INSTANCE.saveOrUpdateAccount((Account) value);
      }
    })
    .build();

  public abstract void connect(DataBaseConfig config);

  public abstract void disconnect();

  public abstract boolean isConnected();

  public abstract Account getAccount(UUID uuid);

  public abstract void saveOrUpdateAccount(Account account);

  public abstract boolean addBalance(UUID uuid, String currency, BigDecimal amount);

  public abstract BigDecimal getBalance(UUID uuid, String currency);

  public abstract BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount);

}
