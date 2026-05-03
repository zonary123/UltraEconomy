package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.commands.admin.BackUpCommands;
import com.kingpixel.ultraeconomy.commands.admin.ReloadCommand;
import com.kingpixel.ultraeconomy.commands.admin.TransactionsCommand;
import com.kingpixel.ultraeconomy.commands.base.*;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.exceptions.UnknownCurrencyException;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:29
 * Command registration orchestrator.
 * <p>
 * Structure:
 * - Base commands: Player-only commands (balance, pay, withdraw, deposit, set, reset, baltop)
 * - Admin commands: Operator-only commands (reload, deposit <player>, withdraw <player>, set <player>, reset <player>, backup, transactions)
 * - Help command: Displays available commands
 */
public class Register {
  private Register() {
    /* Utility class */
  }

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    for (String command : UltraEconomy.config.getCommands()) {
      var base = CommandManager.literal(command);
      base.executes(context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BalanceCommand.run(player == null ? null : player.getUuid(), context,
          Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      });

      PayCommand.put(dispatcher, base);
      BalanceCommand.put(dispatcher, base);
      BaltopCommand.put(dispatcher, base);
      WithdrawCommand.put(dispatcher, base);
      DepositCommand.put(dispatcher, base);
      SetCommand.put(dispatcher, base);
      ResetCommand.put(dispatcher, base);

      var admin = CommandManager.literal("admin")
        .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.admin", 2));

      registerAdminCommand(base, admin, ReloadCommand::put);
      com.kingpixel.ultraeconomy.commands.admin.DepositCommand.put(admin);
      com.kingpixel.ultraeconomy.commands.admin.WithdrawCommand.put(admin);
      com.kingpixel.ultraeconomy.commands.admin.SetCommand.put(admin);
      com.kingpixel.ultraeconomy.commands.admin.ResetCommand.put(admin);
      registerAdminCommand(base, admin, BackUpCommands::register);
      registerAdminCommand(base, admin, TransactionsCommand::register);

      base.then(admin);

      base.then(CommandManager.literal("help").executes(context -> {
        String cmdName = command;
        context.getSource().sendFeedback(() -> AdventureTranslator.toNative(
          String.join("\n",
            UltraEconomy.lang.getPrefix() + UltraEconomy.lang.getHelpTitle(),
            "  /" + cmdName + " " + UltraEconomy.lang.getHelpBalance(),
            "  " + UltraEconomy.lang.getHelpBalanceWithCurrency().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpPay().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpWithdraw().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpDeposit().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpSet().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpReset().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpBaltop().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpBaltopmenu().replace("command", cmdName),
            UltraEconomy.lang.getHelpAdminTitle(),
            "  " + UltraEconomy.lang.getHelpAdminDeposit().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpAdminWithdraw().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpAdminSet().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpAdminTransactions().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpAdminBackup().replace("command", cmdName),
            "  " + UltraEconomy.lang.getHelpAdminReload().replace("command", cmdName)
          )
        ), false);
        return 1;
      }));

      dispatcher.register(base);
    }
  }

  /**
   * Registers an admin command in both backward-compatible and /eco admin contexts.
   * Eliminates duplicate code.
   *
   * @param basePath  the command's base path (for /eco command direct access)
   * @param adminPath the /eco admin subgroup
   * @param registrar the command's registration function
   */
  private static void registerAdminCommand(
    LiteralArgumentBuilder<ServerCommandSource> basePath,
    LiteralArgumentBuilder<ServerCommandSource> adminPath,
    Consumer<LiteralArgumentBuilder<ServerCommandSource>> registrar) {
    registrar.accept(basePath);  // /eco <command>
    registrar.accept(adminPath); // /eco admin <command>
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

  // Supports commands where another mod may register currency as Identifier instead of String.
  public static String getCurrencyArgId(CommandContext<ServerCommandSource> context, String argName) {
    Object value = context.getArgument(argName, Object.class);
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Identifier identifierValue) {
      return identifierValue.getPath();
    }
    return value == null ? "" : value.toString();
  }
}
