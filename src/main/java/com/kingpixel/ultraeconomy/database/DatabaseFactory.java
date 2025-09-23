package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DatabaseFactory {
  /**
   * Only use cache accounts for local database operations
   * 1 minute expiration after last access
   * Maximum size of 1000 accounts
   * On removal, save or update the account in the database
   */
  public static Cache<@NotNull UUID, Account> accounts = Caffeine
    .newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .maximumSize(1000)
    .removalListener((key, value, cause) -> {
      if (value != null) {
        DatabaseFactory.INSTANCE.saveOrUpdateAccount((Account) value);
      }
    })
    .build();
  
  public static DatabaseClient INSTANCE = null;

  public static void init(DataBaseConfig config) {
    if (INSTANCE != null) INSTANCE.disconnect();
    switch (config.getType()) {
      case JSON -> INSTANCE = new JSONClient();
      case SQLITE -> INSTANCE = new SQLiteClient();
      case MYSQL -> INSTANCE = new MySQLClient();
      case MONGODB -> INSTANCE = new MongoDBClient();
    }
    if (INSTANCE != null) INSTANCE.connect(config);
  }
}
