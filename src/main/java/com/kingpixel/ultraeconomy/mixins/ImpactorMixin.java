package com.kingpixel.ultraeconomy.mixins;

import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.core.economy.ImpactorEconomyService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(ImpactorEconomyService.class)
public abstract class ImpactorMixin {

  // account(Currency, UUID)
  @Inject(
    method = "account(Lnet/impactdev/impactor/api/economy/currency/Currency;Ljava/util/UUID;)Ljava/util/concurrent/CompletableFuture;",
    at = @At("RETURN"),
    cancellable = true,
    remap = false
  )
  private void accountCurrencyUuid(Currency currency, UUID uuid, CallbackInfoReturnable<CompletableFuture<Account>> cir) {
    /*if (UltraEconomy.migrationDone) {
      try {
        Account account = cir.getReturnValue().get();
        if (account == null) {
          CobbleUtils.LOGGER.error("Account is null for UUID: " + uuid + " and currency: " + currency.key().toString());
          return;
        }
        cir.setReturnValue(CompletableFuture.completedFuture(new AccountWrapper((ImpactorAccount) account)));
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }*/
  }
}
