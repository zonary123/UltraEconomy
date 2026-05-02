package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.gui.BackupMenu;
import com.kingpixel.ultraeconomy.models.BackupInfo;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 17/12/2025 2:12
 */
public class BackUpCommands {

  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("backup")
        .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.admin.backup", 2))
        .then(createBackUp())
        .then(restoreBackUp())
        .then(listBackUps())
    );
  }

  private static ArgumentBuilder<ServerCommandSource, ?> restoreBackUp() {
    return CommandManager.literal("restore")
      .then(
        CommandManager.argument("backupName", StringArgumentType.string())
          .suggests((context, builder) -> {
            List<BackupInfo> backups = DatabaseFactory.INSTANCE.getBackups();
            for (BackupInfo info : backups) {
              builder.suggest(info.getBackupUUID().toString(),
                net.minecraft.text.Text.literal(info.getFormattedDate() + " (" + info.getAccountCount() + " accounts)"));
            }
            return builder.buildFuture();
          })
          .executes(context -> {
            String backupName = StringArgumentType.getString(context, "backupName");
            try {
              UUID backupUUID = UUID.fromString(backupName);
              DatabaseFactory.INSTANCE.loadBackUp(backupUUID);
              context.getSource().sendMessage(
                AdventureTranslator.toNative(UltraEconomy.lang.getMessageBackupRestored()));
            } catch (IllegalArgumentException e) {
              context.getSource().sendMessage(
                AdventureTranslator.toNative(UltraEconomy.lang.getMessageBackupNotFound()));
            }
            return 1;
          })
      );
  }

  private static ArgumentBuilder<ServerCommandSource, ?> listBackUps() {
    return CommandManager.literal("list")
      .executes(context -> {
        ServerCommandSource source = context.getSource();

        // If executed by a player, open the GUI menu
        if (source.getEntity() instanceof ServerPlayerEntity player) {
          BackupMenu.open(player, 1);
          return 1;
        }

        // Console fallback: list backups in chat
        UltraEconomy.runAsync(() -> {
          List<BackupInfo> backups = DatabaseFactory.INSTANCE.getBackups();
          if (backups.isEmpty()) {
            source.sendMessage(
              AdventureTranslator.toNative(UltraEconomy.lang.getBackupMenuEmpty()));
            return;
          }
          source.sendMessage(AdventureTranslator.toNative(
            UltraEconomy.lang.getPrefix() + "Backups (" + backups.size() + "):"));
          for (BackupInfo info : backups) {
            source.sendMessage(AdventureTranslator.toNative(
              "  " + info.getFormattedDate() + " | UUID: " + info.getBackupUUID()
                + " | Accounts: " + info.getAccountCount()
                + " | Transactions: " + info.getTransactionCount()
            ));
          }
        });
        return 1;
      });
  }

  private static ArgumentBuilder<ServerCommandSource, ?> createBackUp() {
    return CommandManager.literal("create")
      .executes(context -> {
        DatabaseFactory.INSTANCE.createBackUp();
        context.getSource().sendMessage(
          AdventureTranslator.toNative(UltraEconomy.lang.getMessageBackupCreated()));
        return 1;
      });
  }

}
