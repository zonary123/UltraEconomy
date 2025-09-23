package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public abstract class DatabaseClient {
  /**
   * Connect to the database
   *
   * @param config The database configuration
   */
  public abstract void connect(DataBaseConfig config);

  /**
   * Disconnect from the database
   */
  public abstract void disconnect();

  /**
   * Check if the database is connected
   *
   * @return true if connected, false otherwise
   */
  public abstract boolean isConnected();

  /**
   * Get an account by UUID
   *
   * @param uuid The UUID of the account
   *
   * @return The account, or null if not found
   */
  public abstract Account getAccount(UUID uuid);

  /**
   * Save or update an account
   *
   * @param account The account to save or update
   */
  public abstract void saveOrUpdateAccount(Account account);

  /**
   * Add balance to an account
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to add
   * @param amount   The amount to add
   *
   * @return true if successful, false otherwise
   */
  public abstract boolean addBalance(UUID uuid, String currency, BigDecimal amount);

  /**
   * Withdraw balance from an account
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to withdraw
   * @param amount   The amount to withdraw
   *
   * @return true if successful, false otherwise
   */
  public boolean deposit(UUID uuid, String currency, BigDecimal amount) {
    return addBalance(uuid, currency, amount);
  }

  /**
   * Remove balance from an account
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to remove
   * @param amount   The amount to remove
   *
   * @return true if successful, false otherwise
   */
  public abstract boolean removeBalance(UUID uuid, String currency, BigDecimal amount);

  /**
   * Withdraw balance from an account
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to withdraw
   * @param amount   The amount to withdraw
   *
   * @return true if successful, false otherwise
   */
  public boolean withdraw(UUID uuid, String currency, BigDecimal amount) {
    return removeBalance(uuid, currency, amount);
  }


  /**
   * Get the balance of an account
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to get
   *
   * @return The balance, or null if not found
   */
  public abstract BigDecimal getBalance(UUID uuid, String currency);

  /**
   * Set the balance of an account
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to set
   * @param amount   The amount to set
   *
   * @return The new balance, or null if not found
   */
  public abstract BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount);

  /**
   * Check if an account has enough balance
   *
   * @param uuid     The UUID of the account
   * @param currency The currency to check
   * @param amount   The amount to check
   *
   * @return true if the account has enough balance, false otherwise
   */
  public abstract boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount);

  /**
   * Get the top balances for a currency
   *
   * @param currency The currency to get
   * @param page     The page number (starting from 1)
   *
   * @return A list of accounts with the top balances
   */
  public abstract List<Account> getTopBalances(String currency, int page);

}
