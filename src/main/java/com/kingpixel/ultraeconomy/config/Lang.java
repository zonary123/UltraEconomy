package com.kingpixel.ultraeconomy.config;

import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.cobbleutils.Model.messages.HiperMessageBuilder;
import com.kingpixel.cobbleutils.Model.messages.MessageType;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.gui.BalTopMenu;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
@Data
public class Lang {
  private static final String DIR = UltraEconomy.PATH + "/lang/";
  private String prefix;

  // Mensajes de balance
  private HiperMessage messageBalance = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FFD700>💰 Balance: <#00FFAA>%balance% <#FFD700>coins")
    .build();

  private HiperMessage messageSetBalance = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#00FFAA>✅ Your balance has been updated: <#FFDD55>%amount% <#00FFAA>coins.")
    .build();

  private HiperMessage messageDeposit = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#00FFAA>💰 You have received a deposit of <#FFDD55>%amount% <#00FFAA>into your account.")
    .build();

  private HiperMessage messageWithdraw = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#FF5555>💸 You have withdrawn <#FFAA33>%amount% <#FF5555>from your account.")
    .build();

  private HiperMessage messageCurrencyNotTransferable = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>⚠️ This currency cannot be transferred.")
    .build();

  // Mensajes de pagos
  private HiperMessage messagePaySuccessSender = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#00FFAA>✅ You have paid <#FFDD55>%amount% <#00FFAA>to <#33FFFF>%player%")
    .build();

  private HiperMessage messagePaySuccessReceiver = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#00FFAA>💰 You have received <#FFDD55>%amount% <#00FFAA>from <#33FFFF>%player%")
    .build();

  private HiperMessage messagePayYourself = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ You cannot pay yourself.")
    .build();

  private HiperMessage messageNoMoney = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ You don't have enough coins!")
    .build();

  private HiperMessage messagePlayerNotFound = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ Player not found.")
    .build();

  private HiperMessage messageInvalidAmount = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ Invalid amount.")
    .build();

  private HiperMessage messageUnknownCurrency = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ Unknown currency.")
    .build();

  // Mensajes de comandos
  private String messageReloaded = "%prefix%<#00FF00>✅ Configuration reloaded successfully.";
  private String messageOnlyPlayers = "%prefix%<#FF4444>❌ Only players can use this command.";
  private String messageBalanceNotFound = "%prefix%<#FF4444>❌ Balance not found.";
  private String messageCurrencyNotFound = "%prefix%<#FF4444>❌ Currency not found: %currency%";
  private String messageBackupCreated = "%prefix%<#00FF00>✅ Backup created successfully.";
  private String messageBackupRestored = "%prefix%<#00FF00>✅ Backup restored successfully.";
  private String messageBackupNotFound = "%prefix%<#FF4444>❌ Backup not found.";

  // Backup Menu GUI
  private String backupMenuTitle = "<#FFAA00>📦 Backups";
  private String backupMenuEntryName = "&e%date%";
  private List<String> backupMenuEntryLore = List.of(
    "&7UUID: &f%uuid%",
    "&7Accounts: &a%accounts%",
    "&7Transactions: &b%transactions%",
    "",
    "&eClick to restore this backup"
  );
  private String backupMenuEmpty = "%prefix%<#FF5555>No backups found.";
  private String backupMenuRestoreConfirm = "%prefix%<#FFAA00>⚠ Are you sure? Click again to confirm restore.";

  // Transaction Menu GUI
  private String transactionMenuTitle = "<#FFAA00>📜 Transactions — %player%";
  private String transactionMenuEntryDeposit = "&a⬆ DEPOSIT";
  private String transactionMenuEntryWithdraw = "&c⬇ WITHDRAW";
  private String transactionMenuEntrySet = "&e⚙ SET";
  private String transactionMenuEntryTransfer = "&b↔ TRANSFER";
  private List<String> transactionMenuEntryLore = List.of(
    "&7Currency: &f%currency%",
    "&7Amount: &f%amount%",
    "&7Date: &f%date%",
    "&7Processed: %processed%"
  );
  private String transactionMenuEmpty = "%prefix%<#FF5555>No transactions found for this player.";
  private String transactionMenuProcessedYes = "&a✔ Yes";
  private String transactionMenuProcessedNo = "&c✘ No";

  // Mensajes BalTop
  private String messageBalTopHeader = "%prefix%<#FFAA00>--- <#FFD700>Top %number% Richest Players <#FFAA00>---";
  private String messageBalTopLine = "%prefix%<#FFD700>%rank%. <#FFDD55>%player%: <#00FFAA>%balance% <#FFAA00>coins";
  private String messageBalTopFooter = "%prefix%<#FFAA00>------------------------------";
  private String messageBalTopEmpty = "%prefix%<#FF5555>No players found.";

  // BalTop Menu GUI
  private String messageBalTopLore = "&7Balance: &e%balance%";
  private BalTopMenu balTopMenu = new BalTopMenu();

  public Lang() {
    prefix = "<#FFAA00>[<#FFD700>UltraEconomy<#FFAA00>] <#FFFFFF>";
  }

  public void init() {
    String filename = UltraEconomy.config.getLang() + ".json";
    Path dirPath = Utils.getAbsolutePath(DIR).toPath();
    Path filePath = dirPath.resolve(filename);
    try {
      java.nio.file.Files.createDirectories(dirPath);
      UltraEconomy.lang = UtilsFile.readOrCreate(filePath, Lang.class, Lang::new);
      UtilsFile.write(filePath, UltraEconomy.lang);
    } catch (IOException e) {
      UltraEconomy.LOGGER.error("Error loading language file: " + filename);
      e.printStackTrace();
      UltraEconomy.lang = new Lang();
    }
  }
}
