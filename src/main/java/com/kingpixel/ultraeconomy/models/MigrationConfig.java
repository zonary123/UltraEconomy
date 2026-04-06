package com.kingpixel.ultraeconomy.models;

import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.mixins.UserCacheMixin;
import com.kingpixel.ultraeconomy.models.migration.Migration;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class MigrationConfig {
  private boolean active;
  private List<Migration> migrations;

  public MigrationConfig() {
    active = false;
    migrations = List.of(
      new Migration("IMPACTOR", "impactor:dollars", "dollars"),
      new Migration("IMPACTOR", "impactor:tokens", "tokens")
    );
  }

  public void startMigration() {
    if (!active) {
      UltraEconomy.migrationDone = true;
      return;
    }

    UltraEconomy.runAsync(() -> {
      long start = System.currentTimeMillis();
      UltraEconomy.LOGGER.info("Migration started ->");
      UltraEconomy.LOGGER.info("Order of economy uses:");
      for (var use : migrations) {
        UltraEconomy.LOGGER.info("- " + use.getEconomyId() + " -> " + use.getMigrationToCurrencyId());
      }
      var playerUUIDs = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDs();
      var userCache = UltraEconomy.server.getUserCache();
      Set<UUID> missingUUIDs = new HashSet<>();
      if (userCache != null) missingUUIDs = ((UserCacheMixin) userCache).getByUuid().keySet();
      Set<UUID> fusionUUIDs = new HashSet<>();
      fusionUUIDs.addAll(missingUUIDs);
      fusionUUIDs.addAll(playerUUIDs);

      for (var uuid : fusionUUIDs) {
        // Obtener o crear cuenta
        var account = UltraEconomyApi.getAccount(uuid);
        if (account == null) {
          account = new Account(uuid);
          UltraEconomyApi.saveAccount(account);
        }
        UltraEconomyApi.getAccount(account.getPlayerUUID());

        for (Migration migration : migrations) {
          String currencyId = migration.getMigrationToCurrencyId();

          BigDecimal balance = EconomyApi.getBalance(uuid, migration.toEconomyUse());

          if (balance == null) {
            UltraEconomy.LOGGER.warn("Balance for player " + uuid + " is null, skipping");
            continue;
          }

          if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            UltraEconomy.LOGGER.info("Balance for player " + uuid + " is zero or negative, skipping");
            continue;
          }

          UltraEconomyApi.setBalance(uuid, currencyId, balance);
          UltraEconomy.LOGGER.info("Migrated " + balance + " " + currencyId + " for player " + uuid);
        }

        UltraEconomyApi.saveAccount(account);
      }

      long end = System.currentTimeMillis();
      UltraEconomy.LOGGER.info("Migration took " + (end - start) + "ms. Migration finished.");
      active = false;

      try {
        UltraEconomy.config.writeConfig();
      } catch (Exception e) {
        UltraEconomy.LOGGER.error("Failed to write UltraEconomy config after migration", e);
      }
      UltraEconomy.migrationDone = true;
    });
  }
}
