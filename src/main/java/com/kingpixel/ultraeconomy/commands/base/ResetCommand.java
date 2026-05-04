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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 02/05/2026 12:00
 * Resets balance to zero for self or for a specified player.
 */
public class ResetCommand {
  private static final String PERMISSION_NODE = "ultraeconomy.command.reset";

  private ResetCommand() {
    /* Utility class */
  }

  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("reset")
      .requires(source -> PermissionApi.hasPermission(source, PERMISSION_NODE, 2))
      .executes(context -> {
        var executor = context.getSource().getPlayer();
        if (executor == null) {
          CommandFeedback.sendError(context.getSource(),
            "Must specify currency and player when executing from console. Usage: /reset <currency> <player>");
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
                "Must specify a player when executing from console. Usage: /reset <currency> <player>");
              return 0;
            }
            if (PlayerUtils.hasCooldownCommand(executor, PERMISSION_NODE, UltraEconomy.config.getCommandCooldown()))
              return 0;
            run(executor.getUuid(), context, Register.getCurrencyArgId(context, BaseCommandSupport.KEY_CURRENCY));
            return 1;
          })
          .then(
            CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE
              .suggestPlayerName(BaseCommandSupport.KEY_PLAYER, List.of("ultraeconomy.command.reset.other"), 0)
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
      );
  }

  public static void run(UUID playerUUID, CommandContext<ServerCommandSource> context, String currencyId) {
    UltraEconomy.runAsync(() -> {
      var source = context.getSource();
      if (playerUUID == null) {
        CommandFeedback.sendError(source, UltraEconomy.lang.getMessageOnlyPlayers());
        return;
      }

      var currency = Currencies.getCurrency(currencyId);
      UltraEconomyApi.setBalance(playerUUID, currencyId, BigDecimal.ZERO);

      var message = UltraEconomy.lang.getMessageSetBalance();
      ServerPlayerEntity player = source.getPlayer();
      boolean notifyExecutor = player == null
        || !player.getUuid().equals(playerUUID)
        || !UltraEconomy.config.isNotifications();

      if (!notifyExecutor) {
        return;
      }

      if (player != null) {
        message.sendMessage(
          player,
          message.getRawMessage().replace("%amount%", currency.format(BigDecimal.ZERO, UltraEconomyApi.getLocale(player))),
          UltraEconomy.lang.getPrefix(),
          false
        );
      } else {
        CommandFeedback.sendFeedback(source, message.getRawMessage().replace("%amount%", currency.format(BigDecimal.ZERO)));
      }
    });
  }
}
