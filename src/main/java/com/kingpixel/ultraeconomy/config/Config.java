package com.kingpixel.ultraeconomy.config;

import com.google.gson.Gson;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.MigrationConfig;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 20:55
 */
@Data
public class Config {
  private boolean debug;
  private String lang;
  private List<String> commands;
  private DataBaseConfig database;
  private MigrationConfig migration;
  private int limitTopPlayers;
  private int adjustmentShortName;
  private DurationValue balTopCooldown;

  public Config() {
    lang = "en_us";
    commands = List.of("money", "balance", "bal", "eco", "ultraeconomy");
    database = new DataBaseConfig();
    database.setType(DataBaseType.SQLITE);
    database.setUrl("jdbc:sqlite:ultraeconomy.db");
    migration = new MigrationConfig();
    limitTopPlayers = 10;
    adjustmentShortName = 3;
    balTopCooldown = DurationValue.parse("10s");
  }

  public void init() {
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(UltraEconomy.PATH, "config.json", (el) -> {
      Gson gson = Utils.newGson();
      UltraEconomy.config = gson.fromJson(el, Config.class);
      String data = gson.toJson(UltraEconomy.config);
      Utils.writeFileAsync(UltraEconomy.PATH, "config.json", data);
    });
    if (!(Boolean) futureRead.join()) {
      CobbleUtils.LOGGER.info("Creating new config file at " + UltraEconomy.PATH + "/config.json");
      Gson gson = Utils.newGson();
      UltraEconomy.config = this;
      String data = gson.toJson(UltraEconomy.config);
      Utils.writeFileAsync(UltraEconomy.PATH, "config.json", data);
    }


  }
}
