package com.kingpixel.ultraeconomy.commands.base;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 02/05/2026 12:00
 * Command to set your own balance
 */
public class SetCommand {
  private static final String KEY_AMOUNT = "amount";
  private static final String KEY_CURRENCY = "currency";

  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("set")
      .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.command.set", 0))
      .then(
        CommandManager.argument(KEY_AMOUNT, FloatArgumentType.floatArg())
          .suggests((context, builder) -> {
            for (String s : new String[]{"0", "1", "10", "100", "1000", "10000"}) builder.suggest(s);
            return builder.buildFuture();
          })
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
                if (PlayerUtils.hasCooldownCommand(player, "ultraeconomy.command.set", UltraEconomy.config.getCommandCooldown()))
                  return 0;
                run(player == null ? null : player.getUuid(), context, Register.getCurrencyArgId(context,
                  KEY_CURRENCY));
                return 1;
              })
          )
      );
  }

  public static void run(UUID playerUUID, CommandContext<ServerCommandSource> context, String currencyId) {
    UltraEconomy.runAsync(() -> {
      var source = context.getSource();
      if (playerUUID == null) {
        source.sendError(AdventureTranslator.toNative(UltraEconomy.lang.getMessageOnlyPlayers()));
        return;
      }

      var currency = Currencies.getCurrency(currencyId);
      var amount = BigDecimal.valueOf(FloatArgumentType.getFloat(context, KEY_AMOUNT));

      // Validate amount
      if (amount.compareTo(BigDecimal.ZERO) < 0) {
        UltraEconomy.lang.getMessageInvalidAmount().sendMessage(
          source.getPlayer(),
          UltraEconomy.lang.getPrefix(),
          false
        );
        return;
      }

      UltraEconomyApi.setBalance(playerUUID, currencyId, amount);
      Register.sendMessage(currency, amount, playerUUID, UltraEconomy.lang.getMessageSetBalance());
    });
  }
}
