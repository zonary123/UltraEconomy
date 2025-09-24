package com.kingpixel.ultraeconomy.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.mixins.UserCacheMixin;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Data
public class MigrationConfig {
  private boolean active;
  private String economyId;
  private List<EconomyUse> economyUses;
  private List<String> currencyIds;

  public MigrationConfig() {
    active = false;
    economyId = "IMPACTOR";
    economyUses = List.of(
      new EconomyUse(
        "IMPACTOR",
        "impactor:dollars"
      )
    );
    currencyIds = List.of(
      "dollars"
    );
  }

  public void startMigration() {
    if (!active) return;
    CompletableFuture.runAsync(() -> {
        long start = System.currentTimeMillis();
        UserCacheMixin userCache = (UserCacheMixin) CobbleUtils.server.getUserCache();
        if (userCache == null) {
          CobbleUtils.LOGGER.error("UserCache is null, cannot migrate");
          return;
        }
        CobbleUtils.LOGGER.info("Starting migration from " + economyId);

        var names = userCache.getByName();
        names.entrySet()
          .parallelStream()
          .forEach((entry) -> {
            var name = entry.getKey();
            var data = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayer(name);
            data.ifPresent(dataResultPlayer -> {
              var player = dataResultPlayer.player();
              Account account = UltraEconomyApi.getAccount(player.getUuid());
              if (account == null) {
                account = new Account(player);
                UltraEconomyApi.saveAccount(account);
                CobbleUtils.LOGGER.info("Created new account for player " + name);
              }
              UltraEconomyApi.getAccount(player.getUuid());
              int index = 0;
              for (EconomyUse economyUs : economyUses) {
                var currencyId = currencyIds.get(Math.min(index, currencyIds.size() - 1));
                index++;
                BigDecimal balance = EconomyApi.getBalance(
                  player.getUuid(),
                  economyUs
                );
                if (balance == null) {
                  CobbleUtils.LOGGER.warn("Balance for player " + name + " is null, skipping");
                  continue;
                }
                if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                  CobbleUtils.LOGGER.info("Balance for player " + name + " is zero, skipping");
                  continue;
                }
                UltraEconomyApi.setBalance(
                  player.getUuid(),
                  currencyId,
                  balance
                );
                CobbleUtils.LOGGER.info("Migrated " + balance + " " + currencyId + " for player " + name);
              }
              UltraEconomyApi.saveAccount(account);
            });
          });

        long end = System.currentTimeMillis();
        CobbleUtils.LOGGER.info("Migration took " + (end - start) + "ms  Migration finished");
        active = false;
        UltraEconomy.config.writeConfig();
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

}
