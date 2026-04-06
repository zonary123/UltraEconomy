package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.commands.admin.*;
import com.kingpixel.ultraeconomy.commands.base.BalanceCommand;
import com.kingpixel.ultraeconomy.commands.base.BaltopCommand;
import com.kingpixel.ultraeconomy.commands.base.PayCommand;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.exceptions.UnknownCurrencyException;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:29
 */
public class Register {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    for (String command : UltraEconomy.config.getCommands()) {
      var base = CommandManager.literal(command);
      base.executes(context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BalanceCommand.run(player == null ? null : player.getUuid(), context,
          Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      });

      // Base player commands
      PayCommand.put(dispatcher, base);
      BalanceCommand.put(dispatcher, base);
      BaltopCommand.put(dispatcher, base);

      // Admin commands — also accessible directly under /eco for backwards compatibility
      ReloadCommand.put(base);
      DepositCommand.put(base);
      WithdrawCommand.put(base);
      SetCommand.put(base);
      BackUpCommands.register(base);
      TransactionsCommand.register(base);

      // Admin grouping node: /eco admin <subcommand>
      var admin = CommandManager.literal("admin")
        .requires(source -> source.hasPermissionLevel(2));
      ReloadCommand.put(admin);
      DepositCommand.put(admin);
      WithdrawCommand.put(admin);
      SetCommand.put(admin);
      BackUpCommands.register(admin);
      TransactionsCommand.register(admin);
      base.then(admin);

      // Help command: /eco help
      base.then(CommandManager.literal("help").executes(context -> {
        context.getSource().sendFeedback(() -> AdventureTranslator.toNative(
          String.join("\n",
            UltraEconomy.lang.getPrefix() + "<#FFD700>Available commands:",
            "<#FFDD55>  /" + command + " <#AAAAAA>— Check your balance",
            "<#FFDD55>  /" + command + " balance <currency> [player] <#AAAAAA>— Check balance",
            "<#FFDD55>  /" + command + " pay <currency> <amount> <player> <#AAAAAA>— Transfer money",
            "<#FFDD55>  /" + command + " baltop <currency> [page] <#AAAAAA>— Top balances",
            "<#FFDD55>  /" + command + " baltopmenu <currency> <#AAAAAA>— Top balances GUI",
            "<#FF9900>Admin commands:",
            "<#FFDD55>  /" + command + " deposit <amount> <currency> <player>",
            "<#FFDD55>  /" + command + " withdraw <amount> <currency> <player>",
            "<#FFDD55>  /" + command + " set <amount> <currency> <player>",
            "<#FFDD55>  /" + command + " transactions <player> <#AAAAAA>— View history",
            "<#FFDD55>  /" + command + " backup create|list|restore <uuid>",
            "<#FFDD55>  /" + command + " reload <#AAAAAA>— Reload configuration"
          )
        ), false);
        return 1;
      }));

      dispatcher.register(base);
    }
  }

  public static void sendMessage(Currency currency, BigDecimal value, UUID playerUUID,
                                 HiperMessage message) {
    if (UltraEconomy.config.isNotifications()) {
      message.sendMessage(playerUUID, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace("%amount%", currency.format(value)));
    }
  }

  public static Void sendFeedBack(Throwable e, CommandContext<ServerCommandSource> context) {
    if (e instanceof UnknownCurrencyException) {
      if (context.getSource().isExecutedByPlayer()) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null)
          UltraEconomy.lang.getMessageUnknownCurrency()
            .sendMessage(player.getUuid(), UltraEconomy.lang.getPrefix(), false);
      } else {
        context.getSource().sendError(
          AdventureTranslator.toNative(UltraEconomy.lang.getMessageCurrencyNotFound().replace("%currency%", ""))
        );
      }
      return null;
    }
    e.printStackTrace();
    return null;
  }
}
