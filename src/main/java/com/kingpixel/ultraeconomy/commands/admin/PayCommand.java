package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class PayCommand {
  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("pay")
      .requires(source -> PermissionApi.hasPermission(
        source,
        "ultraeconomy.command.pay",
        0
      ))
      .then(
        CommandManager.argument("currency", StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          }).then(
            CommandManager.argument("amount", FloatArgumentType.floatArg())
              .then(
                CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName(
                    "player",
                    List.of("ultraeconomy.command.pay"),
                    2
                  )
                  .executes(context -> {
                    var executor = context.getSource().getPlayer();
                    var target = StringArgumentType.getString(context, "player");
                    var currencyId = StringArgumentType.getString(context, "currency");
                    var amount = BigDecimal.valueOf(FloatArgumentType.getFloat(context, "amount"));
                    run(executor, target, currencyId, amount);
                    return 1;
                  })
              )
          )
      );
  }

  private static void run(ServerPlayerEntity executor, String target, String currencyId, BigDecimal amount) {
    CompletableFuture.runAsync(() -> {
        Currency currency = Currencies.getCurrency(currencyId);
        var player = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayer(target);
        player.ifPresent(dataResultPlayer -> {
          if (executor.getUuid().equals(dataResultPlayer.player().getUuid())) {
            UltraEconomy.lang.getMessagePayYourself().sendMessage(
              executor,
              UltraEconomy.lang.getPrefix(),
              false
            );
            return;
          }
          UltraEconomyApi.pay(executor, dataResultPlayer.player(), currency.getId(), amount);
        });
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

}
