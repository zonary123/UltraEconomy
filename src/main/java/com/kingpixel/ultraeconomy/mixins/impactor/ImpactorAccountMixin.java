package com.kingpixel.ultraeconomy.mixins.impactor;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.EconomyTransferTransaction;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;
import net.impactdev.impactor.api.economy.transactions.details.EconomyTransactionType;
import net.impactdev.impactor.core.economy.accounts.ImpactorAccount;
import net.impactdev.impactor.core.economy.transactions.ImpactorEconomyTransaction;
import net.impactdev.impactor.core.economy.transactions.ImpactorEconomyTransferTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Mixin(ImpactorAccount.class)
public abstract class ImpactorAccountMixin {

  @Unique
  private final Map<Currency, com.kingpixel.ultraeconomy.models.Currency> currencyIdCache = new HashMap<>();

  @Unique
  private String getCurrencyId(Currency currency) {
    return currencyIdCache.computeIfAbsent(currency, c -> UltraEconomyApi.getCurrency(c.key().value())).getId();
  }

  @Inject(method = "deposit(Ljava/math/BigDecimal;)Lnet/impactdev/impactor/api/economy/transactions" +
    "/EconomyTransaction;", at = @At("HEAD"), cancellable = true, remap = false)
  private void depositAsync(BigDecimal amount, CallbackInfoReturnable<EconomyTransaction> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorAccount self = (ImpactorAccount) (Object) this;
      var builder = ImpactorEconomyTransaction.builder()
        .account(self)
        .amount(amount)
        .type(EconomyTransactionType.DEPOSIT);
      if (UltraEconomyApi.deposit(self.owner(), getCurrencyId(self.currency()), amount)) {
        cir.setReturnValue(builder.result(EconomyResultType.SUCCESS).build());
      } else {
        cir.setReturnValue(builder.result(EconomyResultType.FAILED).build());
      }
    }
  }


  @Inject(method = "withdraw(Ljava/math/BigDecimal;)" +
    "Lnet/impactdev/impactor/api/economy/transactions/EconomyTransaction;", at = @At("HEAD"), cancellable = true, remap = false)
  private void withdraw(BigDecimal amount,
                        CallbackInfoReturnable<EconomyTransaction> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorAccount self = (ImpactorAccount) (Object) this;
      var builder = ImpactorEconomyTransaction.builder()
        .account(self)
        .amount(amount)
        .type(EconomyTransactionType.WITHDRAW);
      if (UltraEconomyApi.withdraw(self.owner(), getCurrencyId(self.currency()), amount)) {
        cir.setReturnValue(builder.result(EconomyResultType.SUCCESS).build());
      } else {
        cir.setReturnValue(builder.result(EconomyResultType.FAILED).build());
      }
    }
  }

  @Inject(method = "transfer(Lnet/impactdev/impactor/api/economy/accounts/Account;Ljava/math/BigDecimal;)" +
    "Lnet/impactdev/impactor/api/economy/transactions/EconomyTransferTransaction;", at = {@At("HEAD")}, cancellable = true, remap = false)
  private void transfer(Account target, BigDecimal amount, CallbackInfoReturnable<EconomyTransferTransaction> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorAccount self = (ImpactorAccount) (Object) this;
      var builder = ImpactorEconomyTransferTransaction.builder()
        .from(self)
        .to(target)
        .amount(amount);
      if (UltraEconomyApi.transfer(self.owner(), target.owner(), getCurrencyId(self.currency()), amount)) {
        cir.setReturnValue(builder.result(EconomyResultType.SUCCESS).build());
      } else {
        cir.setReturnValue(builder.result(EconomyResultType.FAILED).build());
      }
    }
  }

  @Inject(method = "set(Ljava/math/BigDecimal;)Lnet/impactdev/impactor/api/economy/transactions/EconomyTransaction;", at = @At("HEAD"), cancellable = true, remap = false)
  private void set(BigDecimal amount, CallbackInfoReturnable<EconomyTransaction> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorAccount self = (ImpactorAccount) (Object) this;
      UltraEconomyApi.setBalance(self.owner(), getCurrencyId(self.currency()), amount);
      cir.setReturnValue(EconomyTransaction.compose()
        .account(self)
        .amount(amount)
        .type(EconomyTransactionType.SET)
        .build());
    }
  }

  @Inject(method = "save", at = @At("HEAD"), remap = false)
  private void save(CallbackInfo ci) {
    if (UltraEconomy.migrationDone) {
      UltraEconomyApi.saveAccount(UltraEconomyApi.getAccount(((ImpactorAccount) (Object) this).owner()));
    }
  }

  @Inject(method = "balance", at = @At("HEAD"), cancellable = true, remap = false)
  private void balance(CallbackInfoReturnable<BigDecimal> cir) {
    if (UltraEconomy.migrationDone) {
      ImpactorAccount self = (ImpactorAccount) (Object) this;
      var account = UltraEconomyApi.getAccount(self.owner());
      BigDecimal balance = BigDecimal.ZERO;
      if (account != null) balance = UltraEconomyApi.getBalance(self.owner(), getCurrencyId(self.currency()));
      cir.setReturnValue(balance);
    }
  }


}
