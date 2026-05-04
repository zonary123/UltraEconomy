package com.kingpixel.ultraeconomy.commands.base;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.CommandBuilders;
import com.kingpixel.ultraeconomy.commands.CommandFeedback;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 02/05/2026 12:00
 * Sets balance for self or for a specified player.
 */
public class SetCommand {
  private static final String PERMISSION_NODE = "ultraeconomy.command.set";

  private SetCommand() {
    /* Utility class */
  }

  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("set")
      .requires(source -> PermissionApi.hasPermission(source, PERMISSION_NODE, 2))
      .then(
        CommandManager.argument(BaseCommandSupport.KEY_AMOUNT, FloatArgumentType.floatArg())
          .suggests(CommandBuilders.BALANCE_SUGGESTIONS)
          .executes(context -> {
            var executor = context.getSource().getPlayer();
            if (executor == null) {
              CommandFeedback.sendError(context.getSource(),
                "Must specify currency and player when executing from console. Usage: /set <amount> <currency> <player>");
              return 0;
            }
            run(executor.getUuid(), context, Currencies.DEFAULT_CURRENCY.getId());
            return 1;
          })
          .then(
            CommandManager.argument(BaseCommandSupport.KEY_CURRENCY, StringArgumentType.string())
              .suggests(CommandBuilders.CURRENCY_SUGGESTIONS)
              .executes(context -> {
                var executor = context.getSource().getPlayer();
                if (executor == null) {
                  CommandFeedback.sendError(context.getSource(),
                    "Must specify a player when executing from console. Usage: /set <amount> <currency> <player>");
                  return 0;
                }
                if (PlayerUtils.hasCooldownCommand(executor, PERMISSION_NODE, UltraEconomy.config.getCommandCooldown()))
                  return 0;
                run(executor.getUuid(), context, Register.getCurrencyArgId(context, BaseCommandSupport.KEY_CURRENCY));
                return 1;
              })
              .then(
                CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE
                  .suggestPlayerName(BaseCommandSupport.KEY_PLAYER, List.of("ultraeconomy.command.set.other"), 0)
                  .executes(context -> {
                    var executor = context.getSource().getPlayer();
                    var targetUUID = BaseCommandSupport.resolveTargetAllowConsole(context);
                    if (targetUUID == null) return 0;
                    if (!BaseCommandSupport.canModifyTarget(context, targetUUID, PERMISSION_NODE)) return 0;

                    if (executor != null && PlayerUtils.hasCooldownCommand(executor, PERMISSION_NODE, UltraEconomy.config.getCommandCooldown()))
                      return 0;

                    run(targetUUID, context, Register.getCurrencyArgId(context, BaseCommandSupport.KEY_CURRENCY));
                    return 1;
                  })
              )
          )
      );
  }

  public static void run(UUID playerUUID, CommandContext<ServerCommandSource> context, String currencyId) {
    UltraEconomy.runAsync(() -> {
      var source = context.getSource();
      if (playerUUID == null) {
        CommandFeedback.sendError(source, UltraEconomy.lang.getMessageOnlyPlayers());
        return;
      }

      var amount = BigDecimal.valueOf(FloatArgumentType.getFloat(context, BaseCommandSupport.KEY_AMOUNT));

      if (amount.compareTo(BigDecimal.ZERO) < 0) {
        CommandFeedback.sendError(source, UltraEconomy.lang.getMessageInvalidAmount());
        return;
      }

      UltraEconomyApi.setBalance(playerUUID, currencyId, amount);
    });
  }
}
