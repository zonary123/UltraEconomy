package com.kingpixel.ultraeconomy.api;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 20:50
 */
public class UltraEconomyApi {
  /**
   * Get the account of a target by UUID
   *
   * @param playerUUID the target's UUID
   *
   * @return the account
   */
  public static Account getAccount(@NotNull UUID playerUUID) {
    long start = System.currentTimeMillis();
    Account account = DatabaseFactory.INSTANCE.getAccount(playerUUID);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get account with playerUUID took " + (end - start) + "ms");
    }
    return account;
  }

  /**
   * Get the account of a target by name
   *
   * @param playerName the target's name
   *
   * @return the account
   */
  public static Account getAccount(@NotNull String playerName) {
    UserCache userCache = CobbleUtils.server.getUserCache();
    if (userCache == null) return null;
    var profile = userCache.findByName(playerName);
    return profile.map(gameProfile -> getAccount(gameProfile.getId())).orElse(null);
  }

  public static boolean withdraw(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return false;
    boolean result = DatabaseFactory.INSTANCE.withdraw(uuid, currency, amount);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Withdraw took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Get a currency by its ID
   *
   * @param currency the currency ID
   *
   * @return the currency
   */
  private static Currency getCurrency(String currency) {
    return Currencies.getCurrency(currency);
  }

  /**
   * Deposit an amount to a target's account
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   *
   * @return true if successful, false otherwise
   */
  public static boolean deposit(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return false;
    boolean result = DatabaseFactory.INSTANCE.deposit(uuid, currency, amount);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Deposit took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Set a target's balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   *
   * @return the new balance, or null if the currency does not exist
   */
  public static BigDecimal setBalance(@NotNull UUID uuid, @NotNull String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return null;
    BigDecimal result = DatabaseFactory.INSTANCE.setBalance(uuid, currency, amount);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get balance took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Get a target's balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   *
   * @return the balance, or null if the currency does not exist
   */
  public static @Nullable BigDecimal getBalance(@NotNull UUID uuid, @NotNull String currency) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return BigDecimal.ZERO;
    BigDecimal result = DatabaseFactory.INSTANCE.getBalance(uuid, currency);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get balance took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Check if a target has enough balance
   *
   * @param player   the target
   * @param currency the currency
   * @param amount   the amount
   *
   * @return true if the target has enough balance
   */
  public static boolean hasEnoughBalance(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    return hasEnoughBalance(player.getUuid(), currency, amount);
  }

  /**
   * Check if a target has enough balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   *
   * @return true if the target has enough balance
   */
  public static boolean hasEnoughBalance(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return false;
    var result = DatabaseFactory.INSTANCE.hasEnoughBalance(uuid, currency, amount);
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "hasEnoughBalance took " + (System.currentTimeMillis() - start) + "ms");
    }
    return result;
  }


  public static boolean transfer(UUID executor, UUID target, String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency curr = getCurrency(currency);
    if (!curr.isTransferable()) return false;
    Account account = UltraEconomyApi.getAccount(executor);
    Account targetAccount = UltraEconomyApi.getAccount(target);
    if (account == null || targetAccount == null) return false;
    if (!hasEnoughBalance(executor, currency, amount)) return false;
    if (!withdraw(target, currency, amount)) return false;
    if (!deposit(target, currency, amount)) {
      deposit(executor, currency, amount);
    }
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Pay took " + (end - start) + "ms");
    }
    return true;
  }

  /**
   * Save an account to the database (This is done automatically when modifying the account)
   *
   * @param account the account
   */
  public static void saveAccount(Account account) {
    long start = System.currentTimeMillis();
    DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Save account took " + (end - start) + "ms");
    }
  }
}
