package com.kingpixel.ultraeconomy.config;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.MigrationConfig;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 23/09/2025 20:55
 */
@Data
public class Config {
  private static final String FILE_NAME = "config.json";
  private boolean debug;
  private boolean notifications;
  private boolean queueMessages;
  private DurationValue commandCooldown;
  private DurationValue retentionBackUps;
  private DurationValue betweenMessagesDelay;
  private String lang;
  private List<String> commands;
  private DataBaseConfig database;
  private MigrationConfig migration;
  private int limitTopPlayers;
  private int adjustmentShortName;
  private DurationValue balTopCooldown;
  /**
   * TTL (in seconds) for the local account cache.
   * <p>
   * Cross-server (shared MySQL/MongoDB): set to 30-60 for near-real-time sync.
   * Single-server: set to 0 to disable TTL (cache indefinitely, best performance).
   * </p>
   */
  private int cacheTtlSeconds;

  /**
   * Web security configuration: rate limiting, auto-ban, security headers.
   */
  private boolean web;
  private WebSecurityConfig webSecurity;

  public Config() {
    debug = false;
    web = false;
    notifications = true;
    queueMessages = false;
    commandCooldown = DurationValue.parse("500ms");
    retentionBackUps = DurationValue.parse("7d");
    betweenMessagesDelay = DurationValue.parse("1s");
    lang = "en_us";
    commands = List.of("money", "balance", "bal", "eco", "ultraeconomy");
    database = new DataBaseConfig();
    database.setType(DataBaseType.SQLITE);
    database.setUrl("jdbc:sqlite:./config/ultraeconomy/ultraeconomy.db");
    migration = new MigrationConfig();
    limitTopPlayers = 10;
    adjustmentShortName = 3;
    balTopCooldown = DurationValue.parse("10s");
    cacheTtlSeconds = 60;
    webSecurity = new WebSecurityConfig();
  }

  public void init() {
    Path filePath = UltraEconomy.getPath().resolve(FILE_NAME);
    try {
      UltraEconomy.config = UtilsFile.readOrCreate(filePath, Config.class, Config::new);
      UtilsFile.write(filePath, UltraEconomy.config);
    } catch (IOException e) {
      UltraEconomy.LOGGER.error("Error loading config file");
      e.printStackTrace();
      UltraEconomy.config = new Config();
    }
  }

  public void writeConfig() {
    Path filePath = UltraEconomy.getPath().resolve(FILE_NAME);
    try {
      UtilsFile.write(filePath, this);
    } catch (IOException e) {
      UltraEconomy.LOGGER.error("Error writing config file");
      e.printStackTrace();
    }
  }
}
