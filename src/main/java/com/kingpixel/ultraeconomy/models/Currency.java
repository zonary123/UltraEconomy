package com.kingpixel.ultraeconomy.models;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import lombok.Data;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos
 * Optimized Currency model with short amount support
 */
@Data
public class Currency {
  transient
  private String id;
  private boolean primary;
  private boolean transferable;
  private byte decimals;
  private BigDecimal defaultBalance;
  private String symbol;
  private String format;
  private String singular;
  private String plural;
  private String[] SUFFIXES;

  transient
  private Cache<BigDecimal, String> formatCache;
  transient
  private Cache<BigDecimal, Text> formatTextCache;

  public Currency() {
    this.format = "<symbol>&6<short_amount> <name>";
    this.singular = "Dollar";
    this.plural = "Dollars";
    this.SUFFIXES = new String[]{"", "K", "M", "B", "T"};
  }

  public Currency(boolean primary, byte decimals, String symbol) {
    super();
    this.primary = primary;
    this.transferable = true;
    this.decimals = decimals;
    this.defaultBalance = BigDecimal.ZERO;
    this.symbol = symbol;
  }

  public void init() {
    formatCache = Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(1_000)
      .build();
    formatTextCache = Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(1_000)
      .build();
  }

  public String format(BigDecimal value) {
    return formatCache.get(value, v -> replace(v, Locale.US));
  }

  public String format(BigDecimal value, Locale locale) {
    return formatCache.get(value, v -> replace(v, locale));
  }


  private String replace(BigDecimal value, Locale locale) {
    String amountStr = value.setScale(decimals, RoundingMode.DOWN)
      .stripTrailingZeros()
      .toPlainString();

    String nameStr = value.compareTo(BigDecimal.ONE) == 0 ? singular : plural;

    StringBuilder sb = new StringBuilder(
      format.length() + symbol.length() + amountStr.length() + nameStr.length()
    );

    for (int i = 0; i < format.length(); i++) {
      char c = format.charAt(i);

      if (c == '<') {
        if (format.startsWith("<symbol>", i)) {
          sb.append(this.symbol);
          i += "<symbol>".length() - 1;
          continue;
        }
        if (format.startsWith("<amount>", i)) {
          sb.append(amountStr);
          i += "<amount>".length() - 1;
          continue;
        }
        if (format.startsWith("<short_amount>", i)) {
          sb.append(formatAmount(value));
          i += "<short_amount>".length() - 1;
          continue;
        }
        if (format.startsWith("<name>", i)) {
          sb.append(nameStr);
          i += "<name>".length() - 1;
          continue;
        }

      }
      sb.append(c);
    }

    return sb.toString();
  }

  /**
   * Format amount with suffixes (K, M, B, T, etc.)
   *
   * @param value the amount to format
   *
   * @return the formatted amount with suffix
   */
  private String formatAmount(BigDecimal value) {
    return formatAmount(value, Locale.US);
  }

  /**
   * Format amount with suffixes (K, M, B, T, etc.) using a specific locale
   *
   * @param value  the amount to format
   * @param locale the locale to use for formatting
   *
   * @return the formatted amount with suffix
   */
  private String formatAmount(BigDecimal value, Locale locale) {
    BigDecimal thousand = BigDecimal.valueOf(1000);
    int suffixIndex = 0;

    // Reducir el número hasta que sea menor que 1000 o lleguemos al último sufijo
    while (value.compareTo(thousand) >= 0 && suffixIndex < SUFFIXES.length - 1) {
      value = value.divide(thousand, 2, RoundingMode.DOWN); // división con redondeo inmediato
      suffixIndex++;
    }

    // Usar NumberFormat para formatear decimales de forma segura y con separador de miles
    NumberFormat nf = NumberFormat.getNumberInstance(locale);
    nf.setMaximumFractionDigits(Math.max(decimals, UltraEconomy.config.getAdjustmentShortName()));
    nf.setMinimumFractionDigits(0);
    nf.setGroupingUsed(false); // sin separadores de miles, ya que es un valor reducido

    return nf.format(value) + SUFFIXES[suffixIndex];
  }


  /**
   * Format the value and return it as a Text component
   *
   * @param value the value to format
   *
   * @return the formatted value as Text
   */
  public Text formatText(BigDecimal value) {
    return formatTextCache.get(value, v -> AdventureTranslator.toNative(format(v)));
  }

  /**
   * Format the value with a specific locale and return it as a Text component
   *
   * @param value  the value to format
   * @param locale the locale to use for formatting
   *
   * @return the formatted value as Text
   */
  public Text formatText(BigDecimal value, Locale locale) {
    return formatTextCache.get(value, v -> AdventureTranslator.toNative(format(v, locale)));
  }
}
