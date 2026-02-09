package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.database.SQL.SQLClient;
import com.kingpixel.ultraeconomy.exceptions.DatabaseConnectionException;
import com.kingpixel.ultraeconomy.models.Account;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public class DatabaseFactory {
  /**
   * Only use cache accounts for local database operations
   * 1 minute expiration after last access
   * Maximum size of 1000 accounts
   * On removal, save or update the account in the database
   */
  public static final Cache<@NotNull UUID, Account> ACCOUNTS = Caffeine
    .newBuilder()
    .build();

  public static DatabaseClient INSTANCE;

  public static void init(DataBaseConfig config) {
    if (INSTANCE != null) INSTANCE.disconnect();
    switch (config.getType()) {
      case SQLITE, MYSQL, MARIADB, H2 -> INSTANCE = new SQLClient();
      case MONGODB -> INSTANCE = new MongoDBClient();
      default ->
        throw new DatabaseConnectionException("Unknown database type " + Arrays.toString(DataBaseType.values()));
    }
    INSTANCE.connect(config);
  }

  public static boolean isConnected() {
    return INSTANCE != null && INSTANCE.isConnected();
  }
}
