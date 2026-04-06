package com.kingpixel.ultraeconomy.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.exceptions.UnknownCurrencyException;
import com.kingpixel.ultraeconomy.manager.PlayerMessageQueueManager;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.kingpixel.ultraeconomy.placeholders.PlaceHoldersPrefix;
import com.kingpixel.ultraeconomy.services.VaultService;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 20:50
 */
public class UltraEconomyApi {
  /**
   * Get the account of a target by UUID
   *
   * @param playerUUID the target's UUID
   * @return the account
   */
  public static Account getAccount(@NotNull UUID playerUUID) {
    return DatabaseFactory.INSTANCE.getAccount(playerUUID);
  }

  /**
   * Get the account of a target by name
   *
   * @param playerName the target's name
   * @return the account
   */
  public static Account getAccount(@NotNull String playerName) {
    UserCache userCache = CobbleUtils.server.getUserCache();
    if (userCache == null) return null;
    var profile = userCache.findByName(playerName);
    return profile.map(gameProfile -> getAccount(gameProfile.getId())).orElse(null);
  }

  public static boolean withdraw(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    if (!hasEnoughBalance(uuid, currency, amount)) return false;
    long start = System.currentTimeMillis();
    boolean result;
    Currency c = getCurrency(currency);
    if (VaultService.isPresent() && isPrimaryCurrency(currency)) {
      result = VaultService.withdraw(uuid, currency, amount);
      DatabaseFactory.INSTANCE.setBalance(uuid, c, VaultService.getBalance(uuid, currency));
      return result;
    }
    result = DatabaseFactory.INSTANCE.withdraw(uuid, c, amount);
    if (UltraEconomy.config.isNotifications()) {
      var message = UltraEconomy.lang.getMessageWithdraw();
      message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace(PlaceHoldersPrefix.PLACEHOLDER_AMOUNT, c.format(amount)));
    }
    aggressiveSave(uuid);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("Withdraw took " + (end - start) + "ms");
    }

    return result;
  }

  /**
   * Aggressively save an account if it has been modified (dirty).
   */
  private static void aggressiveSave(UUID playerUUID) {
    var account = DatabaseFactory.ACCOUNTS.getIfPresent(playerUUID);
    if (account != null && account.isDirty()) {
      saveAccount(account);
    }
  }

  /**
   * Get a currency by its ID
   *
   * @param currency the currency ID
   * @return the currency
   */
  public static Currency getCurrency(String currency) throws UnknownCurrencyException {
    return Currencies.getCurrency(currency);
  }

  /**
   * Get the primary currency
   *
   * @return the primary currency
   */
  private static Currency getPrimaryCurrency() {
    return Currencies.DEFAULT_CURRENCY;
  }

  /**
   * Deposit an amount to a target's account
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   * @return true if successful, false otherwise
   */
  public static boolean deposit(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    boolean result;
    Currency c = getCurrency(currency);
    if (VaultService.isPresent() && isPrimaryCurrency(currency)) {
      result = VaultService.deposit(uuid, currency, amount);
      DatabaseFactory.INSTANCE.setBalance(uuid, c, VaultService.getBalance(uuid, currency));
      return result;
    }
    result = DatabaseFactory.INSTANCE.deposit(uuid, c, amount);
    if (UltraEconomy.config.isNotifications()) {
      var message = UltraEconomy.lang.getMessageDeposit();
      runMessage(
        uuid,
        () -> message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
          message.getRawMessage().replace(PlaceHoldersPrefix.PLACEHOLDER_AMOUNT, c.format(amount,
            getLocale(uuid))))
      );
    }
    aggressiveSave(uuid);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("Deposit took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Set a target's balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   * @return the new balance, or null if the currency does not exist
   */
  public static @Nullable BigDecimal setBalance(@NotNull UUID uuid, @NotNull String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    BigDecimal result;
    if (VaultService.isPresent() && isPrimaryCurrency(currency)) {
      VaultService.setBalance(uuid, currency, amount);
    }
    Currency c = getCurrency(currency);
    result = DatabaseFactory.INSTANCE.setBalance(uuid, c, amount);
    if (UltraEconomy.config.isNotifications()) {
      var message = UltraEconomy.lang.getMessageSetBalance();
      runMessage(
        uuid,
        () -> message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
          message.getRawMessage().replace(PlaceHoldersPrefix.PLACEHOLDER_AMOUNT, c.format(amount,
            getLocale(uuid))))
      );
    }
    aggressiveSave(uuid);

    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("Set balance took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Get a target's balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @return the balance, or null if the currency does not exist
   */
  public static BigDecimal getBalance(@NotNull UUID uuid, @NotNull String currency) {
    if (VaultService.isPresent() && isPrimaryCurrency(currency)) {
      return VaultService.getBalance(uuid, currency);
    } else {
      Currency c = getCurrency(currency);
      return DatabaseFactory.INSTANCE.getBalance(uuid, c);
    }
  }

  private static boolean isPrimaryCurrency(String currency) {
    Currency c = getCurrency(currency);
    return c.equals(getPrimaryCurrency());
  }

  /**
   * Check if a target has enough balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   * @return true if the target has enough balance
   */
  public static boolean hasEnoughBalance(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency c = getCurrency(currency);
    boolean result;
    if (VaultService.isPresent() && isPrimaryCurrency(currency)) {
      result = VaultService.hashEnoughBalance(uuid, currency, amount);
    } else {
      result = DatabaseFactory.INSTANCE.hasEnoughBalance(uuid, c, amount);
    }
    if (UltraEconomy.config.isNotifications() && !result) {
      var message = UltraEconomy.lang.getMessageNoMoney();
      runMessage(
        uuid,
        () -> message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
          message.getRawMessage().replace(PlaceHoldersPrefix.PLACEHOLDER_AMOUNT, c.format(amount,
            getLocale(uuid))))
      );
    }
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("hasEnoughBalance took " + (end - start) + "ms");
    }
    return result;
  }


  public static boolean transfer(UUID executor, UUID target, String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency curr = getCurrency(currency);
    String nameTarget = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerNameWithUUID(target);
    String nameExecutor = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerNameWithUUID(executor);
    if (nameExecutor == null || nameExecutor.isEmpty() || nameTarget == null || nameTarget.isEmpty()) {
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.error("Executor or target name is null in transfer");
      }
      return false;
    }
    if (!curr.isTransferable()) {
      UltraEconomy.lang.getMessageCurrencyNotTransferable().sendMessage(
        executor,
        UltraEconomy.lang.getPrefix(),
        false
      );
      return false;
    }
    String currId = curr.getId();
    if (!hasEnoughBalance(executor, currId, amount)) {
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.error("Not enough balance for executor in transfer");
      }
      return false;
    }
    if (!withdraw(executor, currId, amount)) {
      deposit(executor, currId, amount);
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.error("Failed to withdraw from executor in transfer");
      }
      return false;
    }
    if (!deposit(target, currId, amount)) {
      deposit(executor, currId, amount);
      return false;
    }
    if (UltraEconomy.config.isNotifications()) {
      var messageSender = UltraEconomy.lang.getMessagePaySuccessSender();
      runMessage(
        executor,
        () -> messageSender.sendMessage(executor, UltraEconomy.lang.getPrefix(), false, false, null,
          messageSender.getRawMessage()
            .replace(PlaceHoldersPrefix.PLACEHOLDER_AMOUNT, curr.format(amount, getLocale(executor)))
            .replace("%player%", nameTarget)
        )
      );

      var messageReceiver = UltraEconomy.lang.getMessagePaySuccessReceiver();

      runMessage(
        target,
        () -> messageReceiver.sendMessage(target, UltraEconomy.lang.getPrefix(), false, false, null,
          messageReceiver.getRawMessage()
            .replace(PlaceHoldersPrefix.PLACEHOLDER_AMOUNT, curr.format(amount, getLocale(target)))
            .replace("%player%", nameExecutor)
        )
      );
    }
    aggressiveSave(executor);
    aggressiveSave(target);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("Pay took " + (end - start) + "ms");
    }
    return true;
  }

  /**
   * Save an account to the database and mark it as clean.
   */
  public static void saveAccount(Account account) {
    long start = System.currentTimeMillis();
    DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
    // markClean is called by the DB layer AFTER the actual write completes
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("Save account took " + (end - start) + "ms");
    }
  }

  /**
   * Save an account to the database synchronously and mark it as clean.
   */
  public static void saveAccountSync(Account account) {
    long start = System.currentTimeMillis();
    DatabaseFactory.INSTANCE.saveOrUpdateAccountSync(account);
    account.markClean();
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      UltraEconomy.LOGGER.info("Save account sync took " + (end - start) + "ms");
    }
  }

  public static Locale getLocale(UUID playerUUID) {
    if (UltraEconomy.server == null) return Locale.US;
    return getLocale(UltraEconomy.server.getPlayerManager().getPlayer(playerUUID));
  }

  private static final Cache<String, Locale> localeCache = Caffeine.newBuilder()
    .maximumSize(100)
    .build();

  public static Locale getLocale(ServerPlayerEntity player) {
    Locale serverLocale = Locale.US;

    if (player == null) {
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.warn("Player is null when getting locale, returning server locale");
      }
      return serverLocale;
    }

    String localeStr = player.getClientOptions().language();

    if (localeStr == null || localeStr.isEmpty()) {
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.warn("Player locale string is null or empty, returning server locale");
      }
      return serverLocale;
    }
    return localeCache.get(localeStr, key -> {
      try {
        Locale playerLocale = Locale.forLanguageTag(localeStr.replace('_', '-'));
        if (UltraEconomy.config.isDebug()) {
          UltraEconomy.LOGGER.info("Player locale: " + playerLocale);
        }
        return playerLocale;
      } catch (Exception e) {
        if (UltraEconomy.config.isDebug()) {
          UltraEconomy.LOGGER.error("Error parsing player locale: " + localeStr + ", returning server locale", e);
        }
        return serverLocale;
      }
    });

  }


  private static void runMessage(UUID playerUUID, Runnable runnable) {
    if (UltraEconomy.config.isQueueMessages()) {
      PlayerMessageQueueManager.enqueue(playerUUID, runnable);
    } else {
      runnable.run();
    }
  }

  public static boolean existsPlayerWithName(String target) {
    return DatabaseFactory.INSTANCE.existPlayerWithName(target);
  }
}
