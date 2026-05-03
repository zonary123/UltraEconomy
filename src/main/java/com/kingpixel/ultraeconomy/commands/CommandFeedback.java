package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Small adapter for command feedback that works from both players and the console.
 */
public final class CommandFeedback {
  private CommandFeedback() {
    /* Utility class */
  }

  public static void sendError(ServerCommandSource source, String rawMessage) {
    source.sendError(AdventureTranslator.toNative(withPrefix(rawMessage)));
  }

  public static void sendError(ServerCommandSource source, HiperMessage message) {
    ServerPlayerEntity player = source.getPlayer();
    if (player != null) {
      message.sendMessage(player, UltraEconomy.lang.getPrefix(), false);
      return;
    }

    sendError(source, message.getRawMessage());
  }

  public static void sendFeedback(ServerCommandSource source, String rawMessage) {
    source.sendFeedback(() -> AdventureTranslator.toNative(withPrefix(rawMessage)), false);
  }

  public static void sendMessage(ServerCommandSource source, HiperMessage message, String rawMessage) {
    ServerPlayerEntity player = source.getPlayer();
    if (player != null) {
      message.sendMessage(player, rawMessage, UltraEconomy.lang.getPrefix(), false);
      return;
    }

    sendFeedback(source, rawMessage);
  }

  private static String withPrefix(String rawMessage) {
    return rawMessage.replace("%prefix%", UltraEconomy.lang.getPrefix());
  }
}

