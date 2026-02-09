package com.kingpixel.ultraeconomy.mixins.beconomy;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import org.beconomy.api.BlanketEconomyAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/10/2025 6:06
 */

@Mixin(BlanketEconomyAPI.class)
public abstract class BeconomyServiceMixin {
  @Inject(method = "addBalance", at = @At("HEAD"), cancellable = true, remap = false)
  private void addBalance(UUID playerId, BigDecimal amount, String currencyType, CallbackInfo ci) {
    if (UltraEconomy.migrationDone) {
      UltraEconomyApi.deposit(playerId, currencyType, amount);
      ci.cancel();
    }
  }

  @Inject(method = "subtractBalance", at = @At("HEAD"), cancellable = true, remap = false)
  private void subtractBalance(UUID playerId, BigDecimal amount, String currencyType, CallbackInfoReturnable<Boolean> cir) {
    if (UltraEconomy.migrationDone) {
      cir.setReturnValue(UltraEconomyApi.withdraw(playerId, currencyType, amount));
    }
  }

  @Inject(method = "getBalance", at = @At("HEAD"), cancellable = true, remap = false)
  private void getBalance(UUID playerId, String currencyType, CallbackInfoReturnable<BigDecimal> cir) {
    if (UltraEconomy.migrationDone) {
      cir.setReturnValue(UltraEconomyApi.getBalance(playerId, currencyType));
    }
  }

  @Inject(method = "transfer", at = @At("HEAD"), cancellable = true, remap = false)
  private void transfer(UUID fromPlayerId, UUID toPlayerId, BigDecimal amount, String currencyType, CallbackInfoReturnable<Boolean> cir) {
    if (UltraEconomy.migrationDone) {
      cir.setReturnValue(UltraEconomyApi.transfer(fromPlayerId, toPlayerId, currencyType, amount));
    }
  }

  @Inject(method = "setBalance", at = @At("HEAD"), cancellable = true, remap = false)
  private void setBalance(UUID playerId, BigDecimal amount, String currencyType, CallbackInfo ci) {
    if (UltraEconomy.migrationDone) {
      UltraEconomyApi.setBalance(playerId, currencyType, amount);
      ci.cancel();
    }
  }

  @Inject(method = "hasEnoughFunds", at = @At("HEAD"), cancellable = true, remap = false)
  private void hasEnoughFunds(UUID playerId, BigDecimal amount, String currencyType, CallbackInfoReturnable<Boolean> cir) {
    if (UltraEconomy.migrationDone) {
      cir.setReturnValue(UltraEconomyApi.hasEnoughBalance(playerId, currencyType, amount));
    }
  }
}
