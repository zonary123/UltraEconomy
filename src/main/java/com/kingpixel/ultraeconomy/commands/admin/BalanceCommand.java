package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class BalanceCommand {
  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("balance")
      .executes(context -> {
        run(context.getSource().getPlayer(), context.getSource(), Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      }).then(
        CommandManager.argument("currency", StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          }).executes(context -> {
            run(context.getSource().getPlayer(), context.getSource(), StringArgumentType.getString(context, "currency"));
            return 1;
          }).then(
            CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName("player", List.of(
                "ultraeconomy.admin.balance"
              ), 0)
              .executes(context -> {
                CompletableFuture.runAsync(() -> {
                  var target = StringArgumentType.getString(context, "player");
                  var currencyId = StringArgumentType.getString(context, "currency");
                  var data = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayer(target);
                  data.ifPresentOrElse(
                    d -> run(d.player(), context.getSource(), currencyId),
                    () -> context.getSource().sendError(Text.literal("§cPlayer not found"))
                  );
                }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR).exceptionally(e -> {
                  e.printStackTrace();
                  return null;
                });
                return 1;
              })
          )
      );
  }

  public static void run(ServerPlayerEntity target, ServerCommandSource source, String currencyId) {
    CompletableFuture.runAsync(() -> {
      if (target == null) {
        source.sendError(Text.literal("§cYou must be a player to use this command"));
        return;
      }

      var account = UltraEconomyApi.getAccount(target.getUuid());
      if (account == null) {
        source.sendError(Text.literal("§cAccount not found"));
        return;
      }

      var currency = Currencies.getCurrency(currencyId);
      if (currency == null) {
        source.sendError(Text.literal("§cCurrency not found: " + currencyId + ". Available: " + String.join(", ",
          Currencies.CURRENCY_IDS)));
        return;
      }

      var balance = account.getBalance(currency.getId());
      if (balance == null) {
        source.sendError(Text.literal("§cBalance not found"));
        return;
      }
      ServerPlayerEntity player = source.getPlayer();

      var lang = UltraEconomy.lang;
      String modifiedContent = lang.getMessageBalance().getRawMessage().replace("%balance%", currency.format(balance,
        Locale.US));
      var message = lang.getMessageBalance();
      message.sendMessage(
        player,
        modifiedContent,
        UltraEconomy.MOD_ID,
        false
      );
    }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR).exceptionally(e -> {
      e.printStackTrace();
      return null;
    });
  }
}
