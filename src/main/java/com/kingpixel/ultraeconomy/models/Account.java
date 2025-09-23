package com.kingpixel.ultraeconomy.models;

import lombok.Data;
import lombok.ToString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@ToString
public class Account {
  private final UUID playerUUID;
  private final String playerName;
  private final Map<String, BigDecimal> balances = new HashMap<>();

  public Account(ServerPlayerEntity player) {
    this.playerUUID = player.getUuid();
    this.playerName = player.getName().getString();
  }

  public BigDecimal getBalance(String currency) {
    return balances.getOrDefault(currency, BigDecimal.ZERO);
  }

  public synchronized boolean addBalance(String currency, BigDecimal amount) {
    BigDecimal result = balances.computeIfAbsent(currency, k -> BigDecimal.ZERO).add(amount);
    balances.put(currency, result);
    return true;
  }

  public synchronized void removeBalance(String currency, BigDecimal amount) {
    BigDecimal result = balances.computeIfAbsent(currency, k -> BigDecimal.ZERO).subtract(amount);
    balances.put(currency, result);
  }

  public synchronized void setBalance(String currency, BigDecimal amount) {
    balances.put(currency, amount);
  }
}
