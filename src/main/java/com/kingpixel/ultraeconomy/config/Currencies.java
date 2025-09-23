package com.kingpixel.ultraeconomy.config;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Currency;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:37
 */
public class Currencies {
  private static String PATH = UltraEconomy.PATH + "/currencys/";
  public static final Map<String, Currency> CURRENCIES = new HashMap<>();
  public static String[] CURRENCY_IDS = new String[0];
  public static Currency DEFAULT_CURRENCY;

  public static void init() {
    CURRENCIES.clear();
    var folder = Utils.getAbsolutePath(PATH);
    folder.mkdirs();
    var files = Utils.getFiles(folder);
    if (files.isEmpty()) {
      Currency currency = new Currency(true, (byte) 2, "$");
      currency.setId("dollar");
      CURRENCIES.put(currency.getId(), currency);
      writeCurrency(currency);
      Currency currency2 = new Currency(false, (byte) 2, "€");
      currency2.setId("euro");
      CURRENCIES.put(currency2.getId(), currency2);
      writeCurrency(currency2);
    } else {
      for (var file : files) {
        try {
          var currency = Utils.newGson().fromJson(
            Utils.readFileSync(file), Currency.class
          );
          currency.setId(file.getName().replace(".json", ""));
          CURRENCIES.put(currency.getId(), currency);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    CURRENCIES.forEach((k, v) -> {
      v.init();
      if (v.isPrimary()) {
        DEFAULT_CURRENCY = v;
      }
    });
    CURRENCY_IDS = CURRENCIES.keySet().toArray(new String[0]);
  }

  private static void writeCurrency(Currency currency) {
    String data = Utils.newGson().toJson(currency);
    Utils.writeFileAsync(PATH, currency.getId() + ".json", data);
  }

  public static Currency getCurrency(String currency) {
    Currency c = CURRENCIES.get(currency);
    if (c == null) {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Currency not found: " + currency + ", using default currency: " + DEFAULT_CURRENCY.getId());
      }
      return DEFAULT_CURRENCY;
    }
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Currency found: " + c);
    }
    return c;
  }
}
