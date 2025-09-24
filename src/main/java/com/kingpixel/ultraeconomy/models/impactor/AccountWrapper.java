package com.kingpixel.ultraeconomy.models.impactor;

import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.EconomyTransferTransaction;
import net.impactdev.impactor.api.economy.transactions.details.EconomyTransactionType;
import net.impactdev.impactor.core.economy.accounts.ImpactorAccount;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wrapper para cuentas de Impactor, permitiendo sobreescribir o extender
 * el comportamiento tras la migración.
 */
public class AccountWrapper implements Account {

  private final ImpactorAccount delegate;

  public AccountWrapper(ImpactorAccount delegate) {
    this.delegate = delegate;
  }

  @Override
  public @NotNull Currency currency() {
    return delegate.currency();
  }


  private String getCurrencyId() {
    return currency().key().toString().replace("impactor:", "");
  }

  @Override
  public @NotNull UUID owner() {
    return delegate.owner();
  }

  @Override
  public boolean virtual() {
    return delegate.virtual();
  }

  @Override
  public @NotNull BigDecimal balance() {
    return UltraEconomyApi.getBalance(this.owner(), getCurrencyId());
  }

  @Override
  public @NotNull EconomyTransaction set(BigDecimal amount) {
    UltraEconomyApi.setBalance(this.owner(), getCurrencyId(), amount);
    return EconomyTransaction.compose()
      .account(this)
      .amount(amount)
      .type(EconomyTransactionType.SET)
      .build();
  }

  @Override
  public @NotNull EconomyTransaction withdraw(BigDecimal amount) {
    UltraEconomyApi.withdraw(this.owner(), getCurrencyId(), amount);
    return EconomyTransaction.compose()
      .account(this)
      .amount(amount)
      .type(EconomyTransactionType.WITHDRAW)
      .build();
  }

  @Override
  public @NotNull EconomyTransaction deposit(BigDecimal amount) {
    UltraEconomyApi.deposit(this.owner(), getCurrencyId(), amount);
    return EconomyTransaction.compose()
      .account(this)
      .amount(amount)
      .type(EconomyTransactionType.DEPOSIT)
      .build();
  }

  @Override
  public @NotNull EconomyTransferTransaction transfer(Account to, BigDecimal amount) {
    Account target = (to instanceof AccountWrapper) ? to : new AccountWrapper((ImpactorAccount) to);
    UltraEconomyApi.transfer(this.owner(), target.owner(), getCurrencyId(), amount);
    return EconomyTransferTransaction.compose()
      .from(this)
      .to(target)
      .amount(amount)
      .build();
  }

  @Override
  public @NotNull EconomyTransaction reset() {
    return delegate.reset();
  }
}
