package com.kingpixel.ultraeconomy.placeholders;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Currency;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * @author Carlos Varas Alonso - 28/09/2025 5:35
 */
public class PlaceHolders {

  public static void register() {
    try {
      // %ultraeconomy:balance dollars%
      registerBalance();
      // %ultraeconomy:symbol dollars%
      registerSymbol();
      // %ultraeconomy:amount dollars%
      registerAmount();
      // %ultraeconomy:short_amount dollars%
      registerShortAmount();
    } catch (Exception | NoClassDefFoundError ignored) {
      UltraEconomy.LOGGER.warn("Placeholders API not found, skipping placeholders registration.");
    }
  }

  private static void registerBalance() {
    Placeholders.register(
      Identifier.of(UltraEconomy.MOD_ID, "balance"), (ctx, arg) -> {
        if (CobbleUtils.server == null || CobbleUtils.server.isStopping() || CobbleUtils.server.isStopped())
          return PlaceholderResult.invalid();
        var entity = ctx.entity();
        if (entity == null) return PlaceholderResult.invalid();
        if (!(entity instanceof ServerPlayerEntity player)) return PlaceholderResult.invalid();
        if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
        Currency currency = Currencies.getCurrency(arg);
        return PlaceholderResult.value(
          currency.formatText(
            UltraEconomyApi.getBalance(player.getUuid(), currency.getId()),
            UltraEconomyApi.getLocale(player)
          )
        );
      }
    );
  }

  private static void registerSymbol() {
    Placeholders.register(
      Identifier.of(UltraEconomy.MOD_ID, "symbol"), (ctx, arg) -> {
        if (CobbleUtils.server == null || CobbleUtils.server.isStopping() || CobbleUtils.server.isStopped())
          return PlaceholderResult.invalid();
        if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
        Currency currency = Currencies.getCurrency(arg);
        return PlaceholderResult.value(currency.getSymbolText());
      }
    );
  }

  private static void registerAmount() {
    Placeholders.register(
      Identifier.of(UltraEconomy.MOD_ID, "amount"), (ctx, arg) -> {
        if (CobbleUtils.server == null || CobbleUtils.server.isStopping() || CobbleUtils.server.isStopped())
          return PlaceholderResult.invalid();
        var entity = ctx.entity();
        if (entity == null) return PlaceholderResult.invalid();
        if (!(entity instanceof ServerPlayerEntity player)) return PlaceholderResult.invalid();
        if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
        Currency currency = Currencies.getCurrency(arg);
        return PlaceholderResult.value(
          currency.formatSimpleAmountText(
            UltraEconomyApi.getBalance(player.getUuid(), currency.getId()),
            UltraEconomyApi.getLocale(player)
          )
        );
      }
    );
  }

  private static void registerShortAmount() {
    Placeholders.register(
      Identifier.of(UltraEconomy.MOD_ID, "short_amount"), (ctx, arg) -> {
        if (CobbleUtils.server == null || CobbleUtils.server.isStopping() || CobbleUtils.server.isStopped())
          return PlaceholderResult.invalid();
        var entity = ctx.entity();
        if (entity == null) return PlaceholderResult.invalid();
        if (!(entity instanceof ServerPlayerEntity player)) return PlaceholderResult.invalid();
        if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
        Currency currency = Currencies.getCurrency(arg);
        if (UltraEconomyApi.getAccount(player.getUuid()) == null) return PlaceholderResult.invalid();
        return PlaceholderResult.value(
          currency.formatAmountText(
            UltraEconomyApi.getBalance(player.getUuid(), currency.getId()),
            UltraEconomyApi.getLocale(player)
          )
        );
      }
    );
  }
}
