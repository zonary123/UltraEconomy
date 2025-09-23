package com.kingpixel.ultraeconomy.config;

import com.google.gson.Gson;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import lombok.Data;

import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
@Data
public class Lang {
  private static final String PATH = UltraEconomy.PATH + "/lang/";
  private String prefix;
  private HiperMessage messageBalance;
  private String messageBalTopHeader = "&6--- &eTop %number% richest players &6---";
  private String messageBalTopLine = "&e%rank%. &6%player%: &a%balance%";
  private String messageBalTopFooter = "&6------------------------------";
  private String messageBalTopEmpty = "&cNo players found.";
  private String messagePaySender = "&aYou have paid &6%amount% &ato &6%player%&a.";
  private String messagePayReceiver = "&aYou have received &6%amount% &afrom &6%player%&a.";
  private HiperMessage messagePayYourself = new HiperMessage("c:&cYou can't pay yourself.", null);
  private String messageNoMoney = "&cYou don't have enough money.";
  private String messageInvalidAmount = "&cInvalid amount.";
  private String messagePlayerNotFound = "&cPlayer not found.";
  private String messageCurrencyNotFound = "&cCurrency not found.";
  private String messageOnlyNumbers = "&cYou can only use numbers.";
  private String messageSetBalance = "&aYou have set &6%player%'s &abalance to &6%amount%&a.";
  private String messageAddBalance = "&aYou have added &6%amount% &ato &6%player%'s &abalance.";

  public Lang() {
    prefix = "&6[&eUltraEconomy&6] &r";
    messageBalance = new HiperMessage("c:" + prefix + "Your balance is: &a%balance%", null);
  }

  public void init() {
    String filename = UltraEconomy.config.getLang() + ".json";
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(PATH, filename, (el) -> {
      Gson gson = Utils.newGson();
      UltraEconomy.lang = gson.fromJson(el, Lang.class);
      String data = gson.toJson(UltraEconomy.lang);
      Utils.writeFileAsync(PATH, filename, data);
    });
    if (!futureRead.join()) {
      CobbleUtils.LOGGER.info("Creating new config file at " + PATH + "/" + filename);
      Gson gson = Utils.newGson();
      UltraEconomy.lang = this;
      String data = gson.toJson(UltraEconomy.lang);
      Utils.writeFileAsync(PATH, filename, data);
    }
  }
}
