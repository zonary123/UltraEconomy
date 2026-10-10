package com.kingpixel.ultraeconomy.services;

import com.google.gson.JsonObject;
import com.kingpixel.cobbleutils.util.redis.RedisManager;
import com.kingpixel.cobbleutils.util.redis.RedisService;
import com.kingpixel.cobbleutils.util.redis.handlers.RedisHandler;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service managing real-time cross-server cache invalidation and transaction alerts
 * via CobbleUtils' RedisManager.
 */
public class EconomyRedisService {

  public static final String IDENTIFIER_ACCOUNT_UPDATE = "ultraeconomy:account_update";
  public static final String IDENTIFIER_TRANSACTION_NOTIFY = "ultraeconomy:transaction_notify";
  private static final String KEY_SERVER_ID = "server_id";
  private static final String KEY_UUID = "uuid";
  private static final String SERVER_ID = UUID.randomUUID().toString();

  private static RedisManager manager;
  private static volatile boolean active = false;

  private EconomyRedisService() {}

  /**
   * Initializes Redis cross-server synchronization if enabled in config.
   */
  public static synchronized void init() {
    shutdown();

    if (UltraEconomy.config == null || !UltraEconomy.config.isUseRedis() || UltraEconomy.config.getRedis() == null) {
      active = false;
      manager = null;
      return;
    }

    try {
      manager = RedisService.getOrCreateManager(UltraEconomy.config.getRedis());
      if (manager == null || manager.getConnected() == null || !manager.getConnected().get()) {
        UltraEconomy.LOGGER.warn("[Redis] RedisManager could not establish a connection. Cross-server sync disabled.");
        active = false;
        return;
      }

      manager.registerHandler(createAccountUpdateHandler());
      manager.registerHandler(createTransactionNotifyHandler());

      active = true;
      UltraEconomy.LOGGER.info("[Redis] Cross-server synchronization active on channel: {}", UltraEconomy.config.getRedis().getChannel());
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("[Redis] Failed to initialize RedisManager", e);
      active = false;
    }
  }

  private static RedisHandler createAccountUpdateHandler() {
    return new RedisHandler() {
      @Override
      public String getIdentifier() {
        return IDENTIFIER_ACCOUNT_UPDATE;
      }

      @Override
      public void handle(JsonObject data) {
        try {
          if (data == null || !data.has(KEY_UUID) || isSelfBroadcast(data)) return;
          UUID uuid = UUID.fromString(data.get(KEY_UUID).getAsString());

          DatabaseFactory.ACCOUNTS.invalidate(uuid);

          // If player is connected to this server, reload fresh from DB
          if (UltraEconomy.server != null
              && UltraEconomy.server.getPlayerManager() != null
              && UltraEconomy.server.getPlayerManager().getPlayer(uuid) != null
              && DatabaseFactory.INSTANCE != null) {
            DatabaseFactory.INSTANCE.getAccount(uuid);
          }
        } catch (Exception e) {
          UltraEconomy.LOGGER.error("[Redis] Error handling account update broadcast", e);
        }
      }
    };
  }

  private static RedisHandler createTransactionNotifyHandler() {
    return new RedisHandler() {
      @Override
      public String getIdentifier() {
        return IDENTIFIER_TRANSACTION_NOTIFY;
      }

      @Override
      public void handle(JsonObject data) {
        try {
          if (data == null || !data.has(KEY_UUID) || isSelfBroadcast(data)) return;
          UUID uuid = UUID.fromString(data.get(KEY_UUID).getAsString());

          // If player is connected to this server, wake up transaction worker immediately
          if (UltraEconomy.server != null
              && UltraEconomy.server.getPlayerManager() != null
              && UltraEconomy.server.getPlayerManager().getPlayer(uuid) != null
              && DatabaseFactory.INSTANCE != null) {
            DatabaseFactory.INSTANCE.triggerTransactionCheck();
          }
        } catch (Exception e) {
          UltraEconomy.LOGGER.error("[Redis] Error handling transaction notify broadcast", e);
        }
      }
    };
  }

  private static boolean isSelfBroadcast(JsonObject data) {
    return data.has(KEY_SERVER_ID) && SERVER_ID.equals(data.get(KEY_SERVER_ID).getAsString());
  }

  /**
   * Checks whether Redis synchronization is actively connected and ready.
   */
  public static boolean isAvailable() {
    return active && manager != null && manager.getConnected() != null && manager.getConnected().get();
  }

  /**
   * Broadcasts an account update to all other servers to invalidate local cache.
   */
  public static void publishAccountUpdate(UUID uuid, Currency currency, BigDecimal balance) {
    if (!isAvailable() || uuid == null) return;
    UltraEconomy.runAsync(() -> {
      try {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_SERVER_ID, SERVER_ID);
        json.addProperty(KEY_UUID, uuid.toString());
        if (currency != null) json.addProperty("currency", currency.getId());
        if (balance != null) json.addProperty("balance", balance.toPlainString());
        manager.publish(IDENTIFIER_ACCOUNT_UPDATE, json);
      } catch (Exception e) {
        UltraEconomy.LOGGER.error("[Redis] Failed to broadcast account update for " + uuid, e);
      }
    });
  }

  /**
   * Broadcasts a notification that a new pending transaction exists for a player.
   */
  public static void publishTransactionNotify(UUID uuid) {
    if (!isAvailable() || uuid == null) return;
    UltraEconomy.runAsync(() -> {
      try {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_SERVER_ID, SERVER_ID);
        json.addProperty(KEY_UUID, uuid.toString());
        manager.publish(IDENTIFIER_TRANSACTION_NOTIFY, json);
      } catch (Exception e) {
        UltraEconomy.LOGGER.error("[Redis] Failed to broadcast transaction notification for " + uuid, e);
      }
    });
  }

  /**
   * Shuts down Redis service reference.
   */
  public static synchronized void shutdown() {
    active = false;
    manager = null;
  }
}
