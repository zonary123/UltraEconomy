package com.kingpixel.ultraeconomy.commands.base;

import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class BalanceCommand {
  private static final String KEY_CURRENCY = "currency";
  private static final String KEY_PLAYER = "player";

  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("balance")
      .executes(context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        run(player == null ? null : player.getUuid(), context, Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      }).then(
        CommandManager.argument(KEY_CURRENCY, StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          })
          .executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (PlayerUtils.hasCooldownCommand(player, "ultraeconomy.balance", UltraEconomy.config.getCommandCooldown()))
              return 0;
            run(player == null ? null : player.getUuid(), context, StringArgumentType.getString(context,
              KEY_CURRENCY));
            return 1;
          }).then(
            CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName(KEY_PLAYER, List.of(
                "ultraeconomy.admin.balance"
              ), 0)
              .executes(context -> {
                var target = StringArgumentType.getString(context, KEY_PLAYER);
                if (!UltraEconomyApi.existsPlayerWithName(target)) {
                  UltraEconomy.lang.getMessagePlayerNotFound().sendMessage(
                    context.getSource().getPlayer(), UltraEconomy.lang.getPrefix(), false);
                  return 0;
                }
                var currencyId = StringArgumentType.getString(context, KEY_CURRENCY);
                var targetUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
                run(targetUUID, context, currencyId);
                return 1;
              })
          )
      );
  }

  public static void run(UUID targetUUID, CommandContext<ServerCommandSource> context, String currencyId) {
    UltraEconomy.runAsync(() -> {
      var source = context.getSource();
      if (targetUUID == null) {
        source.sendError(AdventureTranslator.toNative(UltraEconomy.lang.getMessageOnlyPlayers()));
        return;
      }

      var currency = Currencies.getCurrency(currencyId);
      var balance = UltraEconomyApi.getBalance(targetUUID, currencyId);
      if (balance == null) {
        source.sendError(AdventureTranslator.toNative(UltraEconomy.lang.getMessageBalanceNotFound()));
        return;
      }
      ServerPlayerEntity player = source.getPlayer();

      var lang = UltraEconomy.lang;
      String modifiedContent = lang.getMessageBalance().getRawMessage().replace("%balance%", currency.format(balance, UltraEconomyApi.getLocale(player)));
      var message = lang.getMessageBalance();
      message.sendMessage(
        player,
        modifiedContent,
        UltraEconomy.lang.getPrefix(),
        false
      );
    });
  }
}
