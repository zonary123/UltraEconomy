package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class BaltopCommand {
  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("baltop")
      .executes(context -> {
        run(context.getSource(), Currencies.DEFAULT_CURRENCY.getId(), 1);
        return 1;
      }).then(
        CommandManager.argument("currency", StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          })
          .executes(context -> {
            run(context.getSource(), StringArgumentType.getString(context, "currency"), 1);
            return 1;
          }).then(
            CommandManager.argument("page", IntegerArgumentType.integer(1, 100))
              .executes(context -> {
                run(context.getSource(), StringArgumentType.getString(context,
                    "currency"),
                  IntegerArgumentType.getInteger(context, "page"));
                return 1;
              })
          )
      );
  }

  public static void run(ServerCommandSource source, String currencyId, int page) {
    if (PlayerUtils.isCooldownMenu(source.getPlayer(), "ultraeconomy.baltop",
      UltraEconomy.config.getBalTopCooldown())) return;
    CompletableFuture.runAsync(() -> {
        Currency currency = Currencies.getCurrency(currencyId);
        List<Account> topAccounts = DatabaseFactory.INSTANCE.getTopBalances(currency.getId(), page);

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(UltraEconomy.lang.getMessageBalTopHeader()
          .replace("%number%", String.valueOf(page == 0 ? 1 : page * UltraEconomy.config.getLimitTopPlayers())));

        if (topAccounts.isEmpty()) {
          joiner.add(UltraEconomy.lang.getMessageBalTopEmpty());
        } else {
          int limit = UltraEconomy.config.getLimitTopPlayers();
          int rank = (page - 1) * limit + 1;

          for (Account account : topAccounts) {
            String line = UltraEconomy.lang.getMessageBalTopLine()
              .replace("%rank%", Integer.toString(rank))
              .replace("%player%", account.getPlayerName())
              .replace("%balance%", currency.format(account.getBalance(currency.getId())));
            joiner.add(line);
            rank++;
          }
        }

        int previousPage = Math.min(1, page - 1);
        int nextPage = page + 1;

        joiner.add(UltraEconomy.lang.getMessageBalTopFooter()
          .replace("%page%", Integer.toString(page))
          .replace("%currency%", currency.getId())
          .replace("%previous_page%", Integer.toString(previousPage))
          .replace("%next_page%", Integer.toString(nextPage)));
        String output = joiner.toString();

        // Enviar todo de una sola vez
        source.sendFeedback(() -> AdventureTranslator.toNative(output), false);
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

}
