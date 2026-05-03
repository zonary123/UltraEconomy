package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.CommandFeedback;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shared validation/resolution helpers for admin economy commands.
 */
final class AdminCommandSupport {
  static final String KEY_AMOUNT = "amount";
  static final String KEY_CURRENCY = "currency";
  static final String KEY_PLAYER = "player";

  private AdminCommandSupport() {
  }


  static UUID resolveTarget(CommandContext<ServerCommandSource> context) {
    String target = StringArgumentType.getString(context, KEY_PLAYER);
    UUID playerUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
    if (playerUUID == null || !UltraEconomyApi.existsPlayerWithName(target)) {
      CommandFeedback.sendError(context.getSource(), UltraEconomy.lang.getMessagePlayerNotFound());
      return null;
    }

    return playerUUID;
  }

  static BigDecimal getAmount(CommandContext<ServerCommandSource> context) {
    return BigDecimal.valueOf(FloatArgumentType.getFloat(context, KEY_AMOUNT));
  }

  static boolean isPositiveAmount(CommandContext<ServerCommandSource> context, BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) > 0) return true;

    CommandFeedback.sendError(context.getSource(), UltraEconomy.lang.getMessageInvalidAmount());
    return false;
  }

  static boolean isNonNegativeAmount(CommandContext<ServerCommandSource> context, BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) >= 0) return true;

    CommandFeedback.sendError(context.getSource(), UltraEconomy.lang.getMessageInvalidAmount());
    return false;
  }
}

