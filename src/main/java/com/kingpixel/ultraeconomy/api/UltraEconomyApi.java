package com.kingpixel.ultraeconomy.api;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.database.DataBaseFactory;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 20:50
 */
public class UltraEconomyApi {
  /**
   * Get the account of a target
   *
   * @param player the target
   * @return the account
   */
  public static Account getAccount(@NotNull ServerPlayerEntity player) {
    long start = System.currentTimeMillis();
    Account account = DatabaseFactory.INSTANCE.getAccount(player.getUuid());
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get account with player took " + (end - start) + "ms");
    }
    return account;
  }

  /**
   * Get the account of a target by UUID
   *
   * @param uuid the target's UUID
   * @return the account
   */
  public static Account getAccount(@NotNull UUID uuid) {
    long start = System.currentTimeMillis();
    Account account = DatabaseFactory.INSTANCE.getAccount(uuid);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get account with uuid took " + (end - start) + "ms");
    }
    return account;
  }

  /**
   * Get the account of a target by name
   *
   * @param playerName the target's name
   * @return the account
   */
  public static Account getAccount(@NotNull String playerName) {
    var userModel = DataBaseFactory.dataBaseUsers.findUserByName(playerName);
    if (userModel == null) return null;
    return DatabaseFactory.INSTANCE.getAccount(userModel.getPlayerUUID());
  }

  /**
   * Withdraw money from a target's account
   *
   * @param player   the target
   * @param currency the currency
   * @param amount   the amount
   * @return true if the operation was successful
   */
  public static boolean withdraw(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return false;
    boolean result = DatabaseFactory.INSTANCE.withdraw(player.getUuid(), currency, amount);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Withdraw took " + (end - start) + "ms");
    }
    return result;
  }

  private static Currency getCurrency(String currency) {
    return Currencies.getCurrency(currency);
  }

  /**
   * Deposit money to a target's account
   *
   * @param player   the target
   * @param currency the currency
   * @param amount   the amount
   * @return true if the operation was successful
   */
  public static boolean deposit(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return false;
    boolean result = DatabaseFactory.INSTANCE.deposit(player.getUuid(), currency, amount);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Deposit took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Set the balance of a target's account
   *
   * @param player   the target
   * @param currency the currency
   * @param amount   the amount
   * @return the new balance
   */
  public static BigDecimal setBalance(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return null;
    BigDecimal result = DatabaseFactory.INSTANCE.setBalance(player.getUuid(), currency, amount);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Set balance took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Check if a target has enough balance
   *
   * @param player   the target
   * @param currency the currency
   * @param amount   the amount
   * @return true if the target has enough balance
   */
  public static boolean hasEnoughBalance(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    if (getCurrency(currency) == null) return false;
    var result = DatabaseFactory.INSTANCE.hasEnoughBalance(player.getUuid(), currency, amount);
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "hasEnoughBalance took " + (System.currentTimeMillis() - start) + "ms");
    }
    return result;
  }

  public static void pay(ServerPlayerEntity executor, ServerPlayerEntity target, String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency curr = getCurrency(currency);
    if (!curr.isTransferable()) return;
    Account account = UltraEconomyApi.getAccount(executor.getUuid());
    Account targetAccount = UltraEconomyApi.getAccount(target.getUuid());
    if (account == null || targetAccount == null) return;
    if (!hasEnoughBalance(executor, currency, amount)) return;
    if (!withdraw(target, currency, amount)) return;
    if (!deposit(target, currency, amount)) {
      deposit(executor, currency, amount);
    }
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Pay took " + (end - start) + "ms");
    }
  }
}
