package com.kingpixel.ultraeconomy.services;

import com.kingpixel.ultraeconomy.UltraEconomy;
import lombok.Data;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class VaultService {
  public static Economy service;
  private static Boolean present;

  public static boolean isPresent() {
    if (present != null) return present;
    present = false;
    try {
      if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
        UltraEconomy.LOGGER.info("Cannot find Vault!");
        List<String> plugins = new ArrayList<>();
        for (Plugin plugin : Bukkit.getServer().getPluginManager().getPlugins()) {
          plugins.add(plugin.getName());
        }
        UltraEconomy.LOGGER.info("Report this to zonary123 Plugins to Vault -> " + plugins);
      } else {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
          UltraEconomy.LOGGER.info("Registered Service Provider for Economy.class not found");
        } else {
          service = rsp.getProvider();
          UltraEconomy.LOGGER.info("Economy successfully hooked up");
          UltraEconomy.LOGGER.info("Economy: " + service.getName());
          present = true;
        }
      }
    } catch (Exception | NoClassDefFoundError | NoSuchMethodError e) {
      UltraEconomy.LOGGER.info("This error can be ignored if you are not using Vault:");
      e.printStackTrace();
    }
    return present;
  }

  public static BigDecimal getBalance(@NotNull UUID uuid, @NotNull String currency) {
    if (service == null) return BigDecimal.ZERO;
    double balance = service.getBalance(Bukkit.getOfflinePlayer(uuid));
    return BigDecimal.valueOf(balance);
  }

  public static void setBalance(@NotNull UUID uuid, @NotNull String currency, BigDecimal amount) {
    if (service == null) return;
    double currentBalance = service.getBalance(Bukkit.getOfflinePlayer(uuid));
    double targetBalance = amount.doubleValue();
    double difference = targetBalance - currentBalance;
    if (difference > 0) {
      deposit(uuid, currency, BigDecimal.valueOf(difference));
    } else if (difference < 0) {
      withdraw(uuid, currency, BigDecimal.valueOf(-difference));
    }
  }

  public static boolean deposit(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    if (service == null) return false;
    return service.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount.doubleValue()).transactionSuccess();
  }

  public static boolean withdraw(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    if (service == null) return false;
    return service.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount.doubleValue()).transactionSuccess();
  }

  public static boolean hashEnoughBalance(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    if (service == null) return false;
    return service.has(Bukkit.getOfflinePlayer(uuid), amount.doubleValue());
  }
}
