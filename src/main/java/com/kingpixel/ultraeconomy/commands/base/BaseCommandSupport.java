package com.kingpixel.ultraeconomy.commands.base;

import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.CommandFeedback;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import java.util.UUID;

/**
 * Shared validation/resolution helpers for base economy commands.
 * Allows console and player execution with optional player target argument.
 */
final class BaseCommandSupport {
  static final String KEY_AMOUNT = "amount";
  static final String KEY_CURRENCY = "currency";
  static final String KEY_PLAYER = "player";

  private BaseCommandSupport() {
    /* Utility class */
  }

  /**
   * Resolves the target player UUID, allowing console execution if player is specified.
   */
  static UUID resolveTargetAllowConsole(CommandContext<ServerCommandSource> context) {
    try {
      String target = StringArgumentType.getString(context, KEY_PLAYER);
      UUID playerUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
      if (playerUUID == null || !UltraEconomyApi.existsPlayerWithName(target)) {
        CommandFeedback.sendError(context.getSource(), UltraEconomy.lang.getMessagePlayerNotFound());
        return null;
      }
      return playerUUID;
    } catch (IllegalArgumentException e) {
      var executor = context.getSource().getPlayer();
      if (executor == null) {
        CommandFeedback.sendError(context.getSource(),
          "Must specify a player when executing from console. Usage: /<command> <amount> [currency] <player>");
        return null;
      }
      return executor.getUuid();
    }
  }
}

