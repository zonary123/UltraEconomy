package com.kingpixel.ultraeconomy.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.BackupInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * GUI menu for listing and restoring backups.
 */
public class BackupMenu {
  private static final int ROWS = 6;
  private static final int ITEMS_PER_PAGE = 45;

  /**
   * Tracks players awaiting confirmation before a rollback executes.
   * TTL of 30 s: if no second click arrives in time, the pending state clears automatically
   * so the player can't accidentally confirm a restore on a future menu open.
   */
  private static final Cache<UUID, Boolean> pendingConfirm = Caffeine.newBuilder()
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build();

  public static void open(ServerPlayerEntity player, int page) {
    UltraEconomy.runAsync(() -> {
      List<BackupInfo> allBackups = DatabaseFactory.INSTANCE.getBackups();

      if (allBackups.isEmpty()) {
        player.sendMessage(
          AdventureTranslator.toNative(UltraEconomy.lang.getBackupMenuEmpty()), false);
        return;
      }

      int startIndex = (page - 1) * ITEMS_PER_PAGE;
      int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allBackups.size());
      boolean hasNextPage = endIndex < allBackups.size();
      boolean hasPrevPage = page > 1;

      List<BackupInfo> pageBackups = allBackups.subList(startIndex, endIndex);

      ChestTemplate template = ChestTemplate.builder(ROWS).build();

      // Fill backup entries
      int slot = 0;
      for (BackupInfo info : pageBackups) {
        String name = UltraEconomy.lang.getBackupMenuEntryName()
          .replace("%date%", info.getFormattedDate());

        List<String> loreStrings = new ArrayList<>();
        for (String line : UltraEconomy.lang.getBackupMenuEntryLore()) {
          loreStrings.add(line
            .replace("%uuid%", info.getBackupUUID().toString())
            .replace("%accounts%", String.valueOf(info.getAccountCount()))
            .replace("%transactions%", String.valueOf(info.getTransactionCount()))
          );
        }

        ItemStack icon = new ItemStack(Items.WRITABLE_BOOK);

        GooeyButton button = GooeyButton.builder()
          .display(icon)
          .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(loreStrings)))
          .onClick(action -> {
            UUID playerUUID = player.getUuid();
            if (pendingConfirm.getIfPresent(playerUUID) != null) {
              // Second click — confirmed, execute restore
              pendingConfirm.invalidate(playerUUID);
              UIManager.closeUI(player);
              DatabaseFactory.INSTANCE.loadBackUp(info.getBackupUUID());
              player.sendMessage(
                AdventureTranslator.toNative(UltraEconomy.lang.getMessageBackupRestored()), false);
            } else {
              // First click — ask for confirmation; TTL handles expiry automatically
              pendingConfirm.put(playerUUID, true);
              player.sendMessage(
                AdventureTranslator.toNative(UltraEconomy.lang.getBackupMenuRestoreConfirm()), false);
            }
          })
          .build();

        int row = slot / 9;
        int col = slot % 9;
        template.set(row, col, button);
        slot++;
      }

      if (hasPrevPage) {
        ItemStack prevIcon = new ItemStack(Items.ARROW);
        GooeyButton prevButton = GooeyButton.builder()
          .display(prevIcon)
          .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(UltraEconomy.lang.getMenuPreviousPage()))
          .onClick(action -> open(player, page - 1))
          .build();
        template.set(5, 0, prevButton);
      }

      // Close button
      ItemStack closeIcon = new ItemStack(Items.BARRIER);
      GooeyButton closeButton = GooeyButton.builder()
        .display(closeIcon)
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(UltraEconomy.lang.getMenuClose()))
        .onClick(action -> {
          pendingConfirm.invalidate(player.getUuid());
          UIManager.closeUI(player);
        })
        .build();
      template.set(5, 4, closeButton);

      if (hasNextPage) {
        ItemStack nextIcon = new ItemStack(Items.ARROW);
        GooeyButton nextButton = GooeyButton.builder()
          .display(nextIcon)
          .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(UltraEconomy.lang.getMenuNextPage()))
          .onClick(action -> open(player, page + 1))
          .build();
        template.set(5, 8, nextButton);
      }

      GooeyPage pageMenu = GooeyPage.builder()
        .template(template)
        .title(AdventureTranslator.toNative(UltraEconomy.lang.getBackupMenuTitle()))
        .onClose(action -> pendingConfirm.invalidate(player.getUuid()))
        .build();

      UltraEconomy.server.execute(() -> UIManager.openUIForcefully(player, pageMenu));
    });
  }
}

