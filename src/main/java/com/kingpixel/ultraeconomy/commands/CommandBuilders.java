package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;

/**
 * @author Carlos Varas Alonso - 02/05/2026
 * Centralized suggestion providers to eliminate duplicate Brigadier suggestion code.
 */
public class CommandBuilders {

  private CommandBuilders() {
    /* Utility class */
  }

  public static final SuggestionProvider<ServerCommandSource> AMOUNT_SUGGESTIONS =
    (context, builder) -> {
      for (String s : new String[]{"1", "10", "100", "1000", "10000"}) {
        builder.suggest(s);
      }
      return builder.buildFuture();
    };

  public static final SuggestionProvider<ServerCommandSource> BALANCE_SUGGESTIONS =
    (context, builder) -> {
      for (String s : new String[]{"0", "1", "10", "100", "1000", "10000"}) {
        builder.suggest(s);
      }
      return builder.buildFuture();
    };

  public static final SuggestionProvider<ServerCommandSource> CURRENCY_SUGGESTIONS =
    (context, builder) -> {
      for (String currencyId : Currencies.CURRENCY_IDS) {
        builder.suggest(currencyId);
      }
      return builder.buildFuture();
    };
}

