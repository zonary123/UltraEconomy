package com.kingpixel.ultraeconomy.mixins.impactor;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Currency;
import net.impactdev.impactor.core.economy.currency.ImpactorCurrency;
import net.kyori.adventure.text.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author Carlos Varas Alonso - 25/09/2025 2:05
 */
@Mixin(ImpactorCurrency.class)
public abstract class ImpactorCurrencyMixin {

  @Unique private Map<ImpactorCurrency, String> currencyIdCache = new HashMap<>();

  @Inject(method = "format", at = @At("RETURN"), cancellable = true, remap = false)
  private void format(BigDecimal amount, boolean condensed, Locale locale, CallbackInfoReturnable<Component> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorCurrency self = (ImpactorCurrency) (Object) this;
      String id = getCurrencyId(self);
      if (id == null) return;
      Currency currency = Currencies.getCurrency(id);
      if (currency != null) {
        String formatted = currency.format(amount, locale);
        cir.setReturnValue(Component.text(formatted));
      }
    }
  }

  @Unique private String getCurrencyId(ImpactorCurrency currency) {
    if (currency == null) return null;
    return currencyIdCache.computeIfAbsent(currency, c -> c.key().value().replace("impactor:", ""));
  }

  @Inject(method = "symbol", at = @At("RETURN"), cancellable = true, remap = false)
  private void symbol(CallbackInfoReturnable<Component> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorCurrency self = (ImpactorCurrency) (Object) this;
      String id = getCurrencyId(self);
      if (id == null) return;
      Currency currency = Currencies.getCurrency(id);
      if (currency != null) {
        cir.setReturnValue(Component.text(currency.getSymbol()));
      }
    }
  }
}
