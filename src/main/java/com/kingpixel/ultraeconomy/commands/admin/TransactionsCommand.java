package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.gui.TransactionMenu;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/**
 * Admin command to view a player's transaction history in a GUI.
 * Usage: /eco transactions <player>
 * Accepts both online and offline players.
 */
public class TransactionsCommand {

  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("transactions")
        .requires(source -> source.hasPermissionLevel(2))
        .then(
          CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE
            .suggestPlayerName("target", List.of("ultraeconomy.admin.transactions"), 2)
            .executes(TransactionsCommand::execute)
        )
    );
  }

  private static int execute(CommandContext<ServerCommandSource> context) {
    ServerCommandSource source = context.getSource();

    if (!(source.getEntity() instanceof ServerPlayerEntity viewer)) {
      source.sendMessage(
        AdventureTranslator.toNative(UltraEconomy.lang.getMessageOnlyPlayers()));
      return 0;
    }

    String targetName = StringArgumentType.getString(context, "target");
    UUID targetUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(targetName);

    if (targetUUID == null) {
      UltraEconomy.lang.getMessagePlayerNotFound().sendMessage(
        viewer, UltraEconomy.lang.getPrefix(), false);
      return 0;
    }

    TransactionMenu.open(viewer, targetUUID, targetName, 1);
    return 1;
  }
}

