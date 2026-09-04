package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.SQL.SQLClient;
import com.kingpixel.ultraeconomy.exceptions.DatabaseConnectionException;
import com.kingpixel.ultraeconomy.models.Account;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DatabaseFactory {
  private DatabaseFactory() {}
  /**
   * Local account cache. In cross-server environments with a shared DB,
   * the TTL ensures stale data is evicted and re-read from the database.
   * On eviction, dirty accounts are saved before removal.
   */
  public static Cache<@NotNull UUID, Account> ACCOUNTS = buildCache();

  public static DatabaseClient INSTANCE;

  /**
   * Build the Caffeine cache with the configured TTL.
   * Called on init and on reload to pick up config changes.
   */
  public static Cache<@NotNull UUID, Account> buildCache() {
    int ttl = 60; // default 60 seconds
    try {
      if (UltraEconomy.config != null) {
        ttl = UltraEconomy.config.getCacheTtlSeconds();
      }
    } catch (Exception ignored) {
      // Config not loaded yet, use default
    }

    Caffeine<Object, Object> builder = Caffeine.newBuilder();
    if (ttl > 0) {
      builder.expireAfterWrite(ttl, TimeUnit.SECONDS);
    }
    return builder
      .removalListener((UUID key, Account account, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
        if (account.isDirty() && INSTANCE != null) {
          INSTANCE.saveOrUpdateAccount(account);
        }
      })
      .build();
  }

  public static void init(DataBaseConfig config) {
    if (INSTANCE != null) INSTANCE.disconnect();

    // Rebuild cache with current config TTL
    ACCOUNTS = buildCache();

    switch (config.getType()) {
      case SQLITE, MYSQL, MARIADB, H2 -> INSTANCE = new SQLClient();
      case MONGODB -> INSTANCE = new MongoDBClient();
      default ->
        throw new DatabaseConnectionException("Unknown database type " + Arrays.toString(DataBaseType.values()));
    }
    INSTANCE.connect(config);
    if (!INSTANCE.isConnected()) {
      throw new DatabaseConnectionException(config.getType().name());
    }
  }

  public static boolean isConnected() {
    return INSTANCE != null && INSTANCE.isConnected();
  }
}
