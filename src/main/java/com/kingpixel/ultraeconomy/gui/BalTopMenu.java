package com.kingpixel.ultraeconomy.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 27/09/2025 17:06
 */
@Data
public class BalTopMenu {
  private String title;
  private int rows;
  private int playersPerPage;
  private Rectangle rectangle;
  private ItemModel prevPageItem;
  private ItemModel closeItem;
  private ItemModel nextPageItem;

  public BalTopMenu() {
    this.title = "Balance Top";
    this.rows = 6;
    this.playersPerPage = 45;
    this.rectangle = new Rectangle(rows);
    this.prevPageItem = new ItemModel(45, "minecraft:arrow", "&aPrevious Page", List.of("&7Go to the previous page"), 0);
    this.closeItem = new ItemModel(49, "minecraft:barrier", "&cClose", List.of("&7Close the menu"), 0);
    this.nextPageItem = new ItemModel(53, "minecraft:arrow", "&aNext Page", List.of("&7Go to the next page"), 0);
  }

  public void open(ServerPlayerEntity player, int page, Currency currency) {
    UltraEconomy.runAsync(() -> {
      ChestTemplate template = ChestTemplate.builder(rows).build();

      List<Account> accounts = DatabaseFactory.INSTANCE.getTopBalances(currency, page, playersPerPage);

      boolean hasNextPage = accounts.size() > playersPerPage;

      List<Account> accountsPage = accounts.subList(0, Math.min(playersPerPage, accounts.size()));

      List<GooeyButton> buttons = new ArrayList<>();
      for (Account account : accountsPage) {
        buttons.add(account.getButton(currency));
      }
      rectangle.apply(template, buttons);

      if (page > 1) {
        prevPageItem.applyTemplate(template, prevPageItem.getButton(action -> open(player, page - 1, currency), 1, TimeUnit.SECONDS,
          1));
      }

      closeItem.applyTemplate(template, closeItem.getButton(action -> UIManager.closeUI(player), 1, TimeUnit.SECONDS,
        1));

      if (hasNextPage) {
        nextPageItem.applyTemplate(template, nextPageItem.getButton(action -> open(player, page + 1, currency), 1, TimeUnit.SECONDS,
          1));
      }

      GooeyPage pageMenu = GooeyPage.builder()
        .template(template)
        .title(AdventureTranslator.toNative(title))
        .build();


      UltraEconomy.server.execute(() -> UIManager.openUIForcefully(player, pageMenu));
    });
  }

}
