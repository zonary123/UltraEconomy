package com.kingpixel.ultraeconomy;

import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.cobbleutils.util.UtilsLogger;
import com.kingpixel.cobbleutils.util.async.AsyncContext;
import com.kingpixel.cobbleutils.util.async.UtilsAsync;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Config;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.config.Lang;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.manager.PlayerMessageQueueManager;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.placeholders.PlaceHolders;
import com.kingpixel.ultraeconomy.web.WebModule;
import dev.architectury.event.events.common.PlayerEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UltraEconomy implements ModInitializer {
  public static final String MOD_ID = "ultraeconomy";
  private static final String MOD_NAME = "UltraEconomy";
  public static final String PATH = "/config/ultraeconomy";
  public static final Logger LOGGER = UtilsLogger.getLogger(MOD_NAME);
  private static final WebModule webModule = new WebModule();
  public static Config config = new Config();
  public static Lang lang = new Lang();
  public static MinecraftServer server;
  public static boolean migrationDone;

  public static AsyncContext getAsyncContext() {
    return UtilsAsync.createContext(MOD_ID, MOD_NAME, 1, 1);
  }

  @Override
  public void onInitialize() {
    File folder = Utils.getAbsolutePath(PATH);
    if (!folder.exists()) {
      folder.mkdirs();
    }
    load();
    events();
    tasks();
    PlaceHolders.register();
  }

  public static void load() {
    config.init();
    if (config.isWeb()) {
      webModule.stop();
      webModule.start();
    }
    lang.init();
    Currencies.init();
    DatabaseFactory.init(config.getDatabase());
  }

  public void events() {
    PlayerEvent.PLAYER_JOIN.register((player) -> runAsync(() -> {
      Account account = DatabaseFactory.INSTANCE.getAccount(player.getUuid());
      if (account != null) {
        account.setPlayerName(player.getGameProfile().getName());
        DatabaseFactory.ACCOUNTS.put(player.getUuid(), account);
        account.fix();
        if (account.isDirty()) {
          DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
        }
      }
    }));

    PlayerEvent.PLAYER_QUIT.register((player) -> runAsync(() -> {
      Account account = DatabaseFactory.INSTANCE.getCachedAccount(player.getUuid());
      if (account != null) {
        if (account.isDirty()) {
          DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
        }
        DatabaseFactory.ACCOUNTS.invalidate(player.getUuid());
      }
    }));

    ServerLifecycleEvents.SERVER_STARTED.register((srv) -> {
      server = srv;
      config.getMigration().startMigration();
      if (config.isQueueMessages()) {
        PlayerMessageQueueManager.init();
      }
    });

    ServerLifecycleEvents.SERVER_STOPPING.register((srv) -> {
      LOGGER.info("Server stopping — flushing all dirty accounts...");
      DatabaseFactory.INSTANCE.flushCacheSync(30, TimeUnit.SECONDS);
      DatabaseFactory.INSTANCE.disconnect();
      webModule.stop();
      server = null;
    });

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Register.register(dispatcher));
  }

  private void tasks() {
    getAsyncContext().scheduleAtFixedRate(
      () -> DatabaseFactory.ACCOUNTS.asMap().values().forEach(account -> {
        if (account.isDirty()) {
          DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
        }
      }),
      60, 30, TimeUnit.SECONDS
    );

    getAsyncContext().scheduleAtFixedRate(
      () -> DatabaseFactory.INSTANCE.createBackUp(),
      1, 1, TimeUnit.HOURS
    );
  }

  public static CompletableFuture<Void> runAsync(Runnable task) {
    return getAsyncContext().runAsync(task);
  }
}
