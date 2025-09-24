package com.kingpixel.ultraeconomy.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.config.Currencies;
import lombok.Data;
import lombok.ToString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
@ToString
public class Account {
  private long rank;
  private UUID playerUUID;
  private String playerName;
  private final Map<String, BigDecimal> balances;

  public Account(ServerPlayerEntity player) {
    this.playerUUID = player.getUuid();
    this.playerName = player.getGameProfile().getName();
    this.balances = defaultBalances();
  }

  public Account(UUID uuid, Map<String, BigDecimal> balances) {
    ServerPlayerEntity player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
    if (player != null) this.playerName = player.getGameProfile().getName();
    this.playerUUID = uuid;
    this.balances = new ConcurrentHashMap<>(balances);
    fix();
  }

  public Account(UUID uuid, String playerName, Map<String, BigDecimal> balances) {
    this.playerUUID = uuid;
    this.playerName = playerName;
    this.balances = new ConcurrentHashMap<>(balances);
    fix();
  }

  public BigDecimal getBalance(String currency) {
    return balances.getOrDefault(currency, BigDecimal.ZERO);
  }

  public boolean addBalance(String currency, BigDecimal amount) {
    balances.merge(currency, amount, BigDecimal::add);
    return true;
  }

  public boolean removeBalance(String currency, BigDecimal amount) {
    balances.merge(currency, amount, BigDecimal::subtract);
    return true;
  }

  public BigDecimal setBalance(String currency, BigDecimal amount) {
    balances.put(currency, amount);
    return amount;
  }

  public boolean hasEnoughBalance(String currency, BigDecimal amount) {
    return getBalance(currency).compareTo(amount) >= 0;
  }

  public void fix() {
    Currencies.CURRENCIES.forEach((k, v) ->
      balances.putIfAbsent(k, v.getDefaultBalance()));
  }

  private Map<String, BigDecimal> defaultBalances() {
    Map<String, BigDecimal> defaults = new ConcurrentHashMap<>();
    Currencies.CURRENCIES.forEach((k, v) -> defaults.put(k, v.getDefaultBalance()));
    return defaults;
  }
}
