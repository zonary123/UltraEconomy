package com.kingpixel.ultraeconomy.config;

import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Currency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:37
 */
public class Currencies {
  private static final String DIR = UltraEconomy.PATH + "/currencys/";
  private static final Map<String, Currency> CURRENCY_MAP = new ConcurrentHashMap<>();
  public static String[] CURRENCY_IDS;
  public static Currency DEFAULT_CURRENCY;

  public static void init() {
    CURRENCY_MAP.clear();
    Path dirPath = Utils.getAbsolutePath(DIR).toPath();
    try {
      Files.createDirectories(dirPath);
    } catch (IOException e) {
      UltraEconomy.LOGGER.error("Error creating currencies directory");
      e.printStackTrace();
      return;
    }

    List<Path> files = UtilsFile.getAllJsonFiles(dirPath);
    if (files.isEmpty()) {
      Currency currency = new Currency(true, (byte) 2, "$", new ArrayList<>(
        List.of("impactor:dollars")
      ));
      currency.setId("dollars");
      currency.fix();
      CURRENCY_MAP.put(currency.getId(), currency);
      writeCurrency(currency);

      Currency currency2 = new Currency(false, (byte) 2, "t", new ArrayList<>(
        List.of("impactor:tokens")
      ));
      currency2.setId("tokens");
      CURRENCY_MAP.put(currency2.getId(), currency2);
      writeCurrency(currency2);
    } else {
      for (Path file : files) {
        try {
          Currency currency = UtilsFile.read(file, Currency.class);
          String fileName = file.getFileName().toString();
          currency.setId(fileName.replace(".json", ""));
          currency.fix();
          CURRENCY_MAP.put(currency.getId(), currency);
          writeCurrency(currency);
        } catch (Exception e) {
          UltraEconomy.LOGGER.error("Error loading currency file: " + file.getFileName());
          e.printStackTrace();
        }
      }
    }

    Map<String, Currency> aliases = new HashMap<>();
    for (Currency currency : CURRENCY_MAP.values()) {
      if (currency.getCurrencyIds() != null) {
        for (String alias : currency.getCurrencyIds()) {
          aliases.put(alias, currency);
        }
      }
    }
    CURRENCY_MAP.putAll(aliases);

    CURRENCY_MAP.forEach((k, v) -> {
      v.init();
      if (v.isPrimary()) {
        DEFAULT_CURRENCY = v;
      }
    });

    if (DEFAULT_CURRENCY == null && !CURRENCY_MAP.isEmpty()) {
      DEFAULT_CURRENCY = CURRENCY_MAP.values().iterator().next();
      DEFAULT_CURRENCY.setPrimary(true);
      writeCurrency(DEFAULT_CURRENCY);
    }

    Set<String> idCurrency = new HashSet<>();
    CURRENCY_MAP.values().forEach(c -> idCurrency.add(c.getId()));
    CURRENCY_IDS = idCurrency.toArray(new String[0]);
  }

  public static Map<String, Currency> getCurrencyMap() {
    return CURRENCY_MAP;
  }

  private static void writeCurrency(Currency currency) {
    Path filePath = Utils.getAbsolutePath(DIR).toPath().resolve(currency.getId() + ".json");
    UtilsFile.writeAsync(filePath, currency);
  }

  public static Currency getCurrency(String currency) {
    var curr = CURRENCY_MAP.get(currency);
    if (curr == null) curr = DEFAULT_CURRENCY;
    return curr;
  }
}
