package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:32
 */
public class ReloadCommand {
  public static void put(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("reload")
        .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.admin.reload", 2))
        .executes(context -> {
          UltraEconomy.load();
          context.getSource().sendMessage(
            AdventureTranslator.toNative(UltraEconomy.lang.getMessageReloaded())
          );
          return 1;
        })
    );
  }
}
