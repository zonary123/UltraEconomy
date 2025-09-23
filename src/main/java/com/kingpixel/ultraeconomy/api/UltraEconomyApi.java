package com.kingpixel.ultraeconomy.api;

import com.kingpixel.cobbleutils.database.DataBaseFactory;
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
   *
   * @return the account
   */
  public static Account getAccount(@NotNull ServerPlayerEntity player) {
    return DatabaseFactory.INSTANCE.getAccount(player.getUuid());
  }

  /**
   * Get the account of a target by UUID
   *
   * @param uuid the target's UUID
   *
   * @return the account
   */
  public static Account getAccount(@NotNull UUID uuid) {
    return DatabaseFactory.INSTANCE.getAccount(uuid);
  }

  /**
   * Get the account of a target by name
   *
   * @param playerName the target's name
   *
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
   *
   * @return true if the operation was successful
   */
  public static boolean withdraw(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    if (getCurrency(currency) == null) return false;
    return DatabaseFactory.INSTANCE.withdraw(player.getUuid(), currency, amount);
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
   *
   * @return true if the operation was successful
   */
  public static boolean deposit(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    if (getCurrency(currency) == null) return false;
    return DatabaseFactory.INSTANCE.deposit(player.getUuid(), currency, amount);
  }

  /**
   * Set the balance of a target's account
   *
   * @param player   the target
   * @param currency the currency
   * @param amount   the amount
   *
   * @return the new balance
   */
  public static BigDecimal setBalance(@NotNull ServerPlayerEntity player, @NotNull String currency, @NotNull BigDecimal amount) {
    if (getCurrency(currency) == null) return null;
    return DatabaseFactory.INSTANCE.setBalance(player.getUuid(), currency, amount);
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
    if (getCurrency(currency) == null) return false;
    return DatabaseFactory.INSTANCE.hasEnoughBalance(player.getUuid(), currency, amount);
  }

  public static void pay(ServerPlayerEntity executor, ServerPlayerEntity target, String currency, BigDecimal amount) {
    Currency curr = getCurrency(currency);
    if (!curr.isTransferable()) {
      return;
    }
    Account account = UltraEconomyApi.getAccount(executor.getUuid());
    Account targetAccount = UltraEconomyApi.getAccount(target.getUuid());
    if (account == null || targetAccount == null) return;
    if (!hasEnoughBalance(executor, currency, amount)) return;
    if (!withdraw(target, currency, amount)) return;
    if (!deposit(target, currency, amount)) {
      deposit(executor, currency, amount);
    }
  }
}
