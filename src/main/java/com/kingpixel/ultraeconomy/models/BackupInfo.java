package com.kingpixel.ultraeconomy.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a database backup.
 *
 * @author Carlos Varas Alonso
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupInfo {
  private UUID backupUUID;
  private Instant createdAt;
  private int accountCount;
  private int transactionCount;

  /**
   * Formatted date string for display in GUI / chat.
   */
  public String getFormattedDate() {
    java.time.ZonedDateTime zdt = createdAt.atZone(java.time.ZoneId.systemDefault());
    return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt);
  }
}

