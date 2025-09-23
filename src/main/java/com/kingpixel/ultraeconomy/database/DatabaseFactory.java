package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;

public class DatabaseFactory {
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
