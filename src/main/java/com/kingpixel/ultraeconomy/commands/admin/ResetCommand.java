package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 02/05/2026
 * Admin command to reset a player's balance to zero for a specific currency
 */
public class ResetCommand {
  public static void put(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("reset")
        .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.admin.reset", 2))
        .then(
          CommandManager.argument("currency", StringArgumentType.string())
            .suggests((context, builder) -> {
              var size = Currencies.CURRENCY_IDS.length;
              for (int i = 0; i < size; i++) {
                builder.suggest(Currencies.CURRENCY_IDS[i]);
              }
              return builder.buildFuture();
            }).then(
              CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName("player", List.of(
                  "ultraeconomy.admin.reset"), 2)
                .executes(context -> {
                  UltraEconomy.runAsync(() -> {
                    var target = StringArgumentType.getString(context, "player");
                    var currency = Currencies.getCurrency(Register.getCurrencyArgId(context, "currency"));
                    ServerCommandSource source = context.getSource();
                    if (!UltraEconomyApi.existsPlayerWithName(target)) {
                      if (source.isExecutedByPlayer() && source.getPlayer() != null) {
                        UltraEconomy.lang.getMessagePlayerNotFound().sendMessage(
                          source.getPlayer(), UltraEconomy.lang.getPrefix(), false);
                      } else {
                        source.sendError(
                          com.kingpixel.cobbleutils.util.AdventureTranslator.toNative(
                            UltraEconomy.lang.getMessagePlayerNotFound().getRawMessage()));
                      }
                      return;
                    }
                    var playerUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
                    if (playerUUID != null) {
                      UltraEconomyApi.setBalance(playerUUID, currency.getId(), BigDecimal.ZERO);
                      Register.sendMessage(currency, BigDecimal.ZERO, playerUUID, UltraEconomy.lang.getMessageSetBalance());
                    } else {
                      if (source.isExecutedByPlayer() && source.getPlayer() != null) {
                        UltraEconomy.lang.getMessagePlayerNotFound().sendMessage(
                          source.getPlayer(), UltraEconomy.lang.getPrefix(), false);
                      } else {
                        source.sendError(
                          com.kingpixel.cobbleutils.util.AdventureTranslator.toNative(
                            UltraEconomy.lang.getMessagePlayerNotFound().getRawMessage()));
                      }
                    }
                  });
                  return 1;
                })
            )
        )
    );
  }
}
