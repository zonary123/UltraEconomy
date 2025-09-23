package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:32
 */
public class ReloadCommand {
  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("reload")
        .requires(source -> source.hasPermissionLevel(2))
        .executes(context -> {
          context.getSource().sendMessage(
            Text.literal("§a[UltraEconomy] Reloading configuration...")
          );
          UltraEconomy.load();
          return 1;
        })
    );
  }
}
