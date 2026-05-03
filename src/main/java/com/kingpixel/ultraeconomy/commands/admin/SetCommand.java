package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.CommandBuilders;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class SetCommand {
  private SetCommand() {
    /* Utility class */
  }

  public static void put(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("set")
        .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.admin.set", 2))
        .then(
          CommandManager.argument(AdminCommandSupport.KEY_AMOUNT, FloatArgumentType.floatArg())
            .suggests(CommandBuilders.BALANCE_SUGGESTIONS)
            .then(
              CommandManager.argument(AdminCommandSupport.KEY_CURRENCY, StringArgumentType.string())
                .suggests(CommandBuilders.CURRENCY_SUGGESTIONS)
                .then(
                  CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName(AdminCommandSupport.KEY_PLAYER, List.of(
                      "ultraeconomy.admin.set"), 2)
                    .executes(context -> {
                      UltraEconomy.runAsync(() -> {
                        var playerUUID = AdminCommandSupport.resolveTarget(context);
                        if (playerUUID == null) return;

                        BigDecimal value = AdminCommandSupport.getAmount(context);
                        if (!AdminCommandSupport.isNonNegativeAmount(context, value)) return;

                        var currency = Currencies.getCurrency(Register.getCurrencyArgId(context, AdminCommandSupport.KEY_CURRENCY));
                        UltraEconomyApi.setBalance(playerUUID, currency.getId(), value);
                      });
                      return 1;
                    })
                )
            )
        )
    );
  }
}
