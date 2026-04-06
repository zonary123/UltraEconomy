package com.kingpixel.ultraeconomy.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.database.TransactionType;
import com.kingpixel.ultraeconomy.models.Transaction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI menu for viewing a player's transaction history.
 */
public class TransactionMenu {
  private static final int ROWS = 6;
  private static final int ITEMS_PER_PAGE = 45;
  private static final DateTimeFormatter DATE_FORMAT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  /**
   * Open the transaction menu for a target player.
   *
   * @param viewer     the admin/player viewing the menu
   * @param targetUUID the UUID of the player whose transactions to show
   * @param targetName the name of the target player (for the title)
   * @param page       the page number (1-based)
   */
  public static void open(ServerPlayerEntity viewer, UUID targetUUID, String targetName, int page) {
    UltraEconomy.runAsync(() -> {
      // We fetch more than a page to detect if there's a next page
      int fetchLimit = (page * ITEMS_PER_PAGE) + 1;
      List<Transaction> allTransactions = DatabaseFactory.INSTANCE.getTransactions(targetUUID, fetchLimit);

      if (allTransactions.isEmpty()) {
        viewer.sendMessage(
          AdventureTranslator.toNative(UltraEconomy.lang.getTransactionMenuEmpty()), false);
        return;
      }

      int startIndex = (page - 1) * ITEMS_PER_PAGE;
      int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allTransactions.size());
      boolean hasNextPage = allTransactions.size() > page * ITEMS_PER_PAGE;
      boolean hasPrevPage = page > 1;

      List<Transaction> pageTransactions = allTransactions.subList(startIndex, endIndex);

      ChestTemplate template = ChestTemplate.builder(ROWS).build();
      fillTransactionButtons(template, pageTransactions);
      addNavigationButtons(template, viewer, targetUUID, targetName, page, hasPrevPage, hasNextPage);

      String title = UltraEconomy.lang.getTransactionMenuTitle()
        .replace("%player%", targetName);

      GooeyPage pageMenu = GooeyPage.builder()
        .template(template)
        .title(AdventureTranslator.toNative(title))
        .build();

      UltraEconomy.server.execute(() -> UIManager.openUIForcefully(viewer, pageMenu));
    });
  }

  private static void fillTransactionButtons(ChestTemplate template, List<Transaction> transactions) {
    int slot = 0;
    for (Transaction tx : transactions) {
      String entryName = getEntryName(tx.getType());

      String processedText = tx.isProcessed()
        ? UltraEconomy.lang.getTransactionMenuProcessedYes()
        : UltraEconomy.lang.getTransactionMenuProcessedNo();

      String dateStr = tx.getTimestamp() != null
        ? DATE_FORMAT.format(tx.getTimestamp())
        : "N/A";

      List<String> loreStrings = new ArrayList<>();
      for (String line : UltraEconomy.lang.getTransactionMenuEntryLore()) {
        loreStrings.add(line
          .replace("%currency%", tx.getCurrency() != null ? tx.getCurrency() : "N/A")
          .replace("%amount%", tx.getAmount() != null ? tx.getAmount().toPlainString() : "0")
          .replace("%date%", dateStr)
          .replace("%processed%", processedText)
        );
      }

      GooeyButton button = GooeyButton.builder()
        .display(getIcon(tx.getType()))
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(entryName))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(loreStrings)))
        .build();

      template.set(slot / 9, slot % 9, button);
      slot++;
    }
  }

  private static void addNavigationButtons(ChestTemplate template, ServerPlayerEntity viewer,
                                           UUID targetUUID, String targetName,
                                           int page, boolean hasPrevPage, boolean hasNextPage) {
    if (hasPrevPage) {
      GooeyButton prevButton = GooeyButton.builder()
        .display(new ItemStack(Items.ARROW))
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("&aPrevious Page"))
        .onClick(action -> open(viewer, targetUUID, targetName, page - 1))
        .build();
      template.set(5, 0, prevButton);
    }

    GooeyButton closeButton = GooeyButton.builder()
      .display(new ItemStack(Items.BARRIER))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("&cClose"))
      .onClick(action -> UIManager.closeUI(viewer))
      .build();
    template.set(5, 4, closeButton);

    if (hasNextPage) {
      GooeyButton nextButton = GooeyButton.builder()
        .display(new ItemStack(Items.ARROW))
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("&aNext Page"))
        .onClick(action -> open(viewer, targetUUID, targetName, page + 1))
        .build();
      template.set(5, 8, nextButton);
    }
  }

  private static String getEntryName(TransactionType type) {
    if (type == null) return "&7? UNKNOWN";
    return switch (type) {
      case DEPOSIT -> UltraEconomy.lang.getTransactionMenuEntryDeposit();
      case WITHDRAW -> UltraEconomy.lang.getTransactionMenuEntryWithdraw();
      case SET -> UltraEconomy.lang.getTransactionMenuEntrySet();
      case TRANSFER -> UltraEconomy.lang.getTransactionMenuEntryTransfer();
    };
  }

  private static ItemStack getIcon(TransactionType type) {
    if (type == null) return new ItemStack(Items.PAPER);
    return switch (type) {
      case DEPOSIT -> new ItemStack(Items.EMERALD);
      case WITHDRAW -> new ItemStack(Items.REDSTONE);
      case SET -> new ItemStack(Items.COMPARATOR);
      case TRANSFER -> new ItemStack(Items.ENDER_PEARL);
    };
  }
}

