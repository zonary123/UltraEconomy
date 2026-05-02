package com.kingpixel.ultraeconomy.commands.base;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.StringJoiner;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class BaltopCommand {
  private static final String CURRENCY_ARG = "currency";

  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
    base.then(getBalTopMenu());
    dispatcher.register(getBalTopMenu());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> getBalTopMenu() {
    return CommandManager.literal("baltopmenu")
      .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.command.baltopmenu", 0))
      .then(
        CommandManager.argument(CURRENCY_ARG, StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          })
          .executes(context -> {
            String currencyId = Register.getCurrencyArgId(context, CURRENCY_ARG);
            Currency currency = Currencies.getCurrency(currencyId);
            UltraEconomy.lang.getBalTopMenu().open(context.getSource().getPlayer(), 1,
              currency);
            return 1;
          })
      );
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("baltop")
      .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.command.baltop", 0))
      .executes(context -> {
        run(context, Currencies.DEFAULT_CURRENCY.getId(), 1);
        return 1;
      }).then(
        CommandManager.argument(CURRENCY_ARG, StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          })
          .executes(context -> {
            run(context, Register.getCurrencyArgId(context, CURRENCY_ARG), 1);
            return 1;
          }).then(
            CommandManager.argument("page", IntegerArgumentType.integer(1))
              .executes(context -> {
                run(context, Register.getCurrencyArgId(context,
                    CURRENCY_ARG),
                  IntegerArgumentType.getInteger(context, "page"));
                return 1;
              })
          )
      );
  }

  public static void run(CommandContext<ServerCommandSource> context, String currencyId, int page) {
    var source = context.getSource();
    if (PlayerUtils.hasCooldownCommand(source.getPlayer(), "ultraeconomy.baltop", UltraEconomy.config.getBalTopCooldown()))
      return;
    UltraEconomy.runAsync(() -> {
      Currency currency = Currencies.getCurrency(currencyId);
      if (currency == null) {
        source.sendMessage(AdventureTranslator.toNative(
          UltraEconomy.lang.getMessageCurrencyNotFound().replace("%currency%", currencyId)));
        return;
      }
      List<Account> topAccounts = DatabaseFactory.INSTANCE.getTopBalances(currency, page,
        UltraEconomy.config.getLimitTopPlayers());

      StringJoiner joiner = new StringJoiner("\n");
      joiner.add(UltraEconomy.lang.getMessageBalTopHeader()
        .replace("%number%", String.valueOf(page == 0 ? 1 : page * UltraEconomy.config.getLimitTopPlayers())));

      if (topAccounts.isEmpty()) {
        joiner.add(UltraEconomy.lang.getMessageBalTopEmpty());
      } else {
        int limit = UltraEconomy.config.getLimitTopPlayers();
        int rank = (page - 1) * limit + 1;
        int size = topAccounts.size();
        if (size > limit) size = limit;
        for (int i = 0; i < size; i++) {
          Account account = topAccounts.get(i);
          String line = UltraEconomy.lang.getMessageBalTopLine()
            .replace("%rank%", Integer.toString(rank))
            .replace("%player%", account.getPlayerName())
            .replace("%balance%", currency.format(account.getBalance(currency)));
          joiner.add(line);
          rank++;
        }
      }

      int previousPage = Math.max(1, page - 1);
      int nextPage = page + 1;

      joiner.add(UltraEconomy.lang.getMessageBalTopFooter()
        .replace("%page%", Integer.toString(page))
        .replace("%currency%", currency.getId())
        .replace("%previous_page%", Integer.toString(previousPage))
        .replace("%next_page%", Integer.toString(nextPage)));
      String output = joiner.toString();

      source.sendFeedback(() -> AdventureTranslator.toNative(output), false);
    });
  }

}
