package com.kingpixel.ultraeconomy.models;

import com.kingpixel.ultraeconomy.database.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 *
 * @author Carlos Varas Alonso - 17/12/2025 23:16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
  private UUID accountUUID;
  private String currency;
  private BigDecimal amount;
  private TransactionType type;
  private UUID transferedToAccountUUID;
  private boolean processed;
  private Instant timestamp;
  private String reason;


  // MONGODB
  private static final String FIELD_ACCOUNT_UUID = "account_uuid";
  private static final String FIELD_CURRENCY_ID = "currency_id";
  private static final String FIELD_AMOUNT = "amount";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_PROCESSED = "processed";
  private static final String FIELD_BACKUP_UUID = "uuid";
  private static final String FIELD_REASON = "reason";
  private static final String FIELD_TRANSFERRED_TO_ACCOUNT_UUID = "transferred_to_account_uuid";


  public Transaction(UUID accountUUID, String currency, BigDecimal amount, TransactionType type, boolean processed) {
    this.accountUUID = accountUUID;
    this.currency = currency;
    this.amount = amount;
    this.type = type;
    this.processed = processed;
    this.timestamp = Instant.now();
  }

  public Transaction(UUID uuid, String currency, BigDecimal amount, TransactionType type, boolean processed,
                     Instant now) {
    this.accountUUID = uuid;
    this.currency = currency;
    this.amount = amount;
    this.type = type;
    this.processed = processed;
    this.timestamp = now;
  }

  public static Transaction fromDocument(Document doc) {
    UUID accountUUID = UUID.fromString(doc.getString(FIELD_ACCOUNT_UUID));
    String currency = doc.getString(FIELD_CURRENCY_ID);
    BigDecimal amount = doc.get(FIELD_AMOUNT, Decimal128.class).bigDecimalValue();
    TransactionType type = TransactionType.valueOf(doc.getString(FIELD_TYPE));
    boolean processed = doc.getBoolean(FIELD_PROCESSED);
    Instant timestamp = doc.containsKey("timestamp") ?
      doc.getDate("timestamp").toInstant() : Instant.now();
    String reason = doc.containsKey(FIELD_REASON) ? doc.getString(FIELD_REASON) : null;
    String transferredToAccountUUIDStr = doc.getString(FIELD_TRANSFERRED_TO_ACCOUNT_UUID);
    UUID transferedToAccountUUID = transferredToAccountUUIDStr != null ?
      UUID.fromString(transferredToAccountUUIDStr) : null;
    return Transaction.builder()
      .accountUUID(accountUUID)
      .currency(currency)
      .amount(amount)
      .type(type)
      .processed(processed)
      .timestamp(timestamp)
      .reason(reason)
      .transferedToAccountUUID(transferedToAccountUUID)
      .build();
  }

  public Document toDocument() {
    return new Document(FIELD_ACCOUNT_UUID, accountUUID.toString())
      .append(FIELD_CURRENCY_ID, currency)
      .append(FIELD_AMOUNT, new Decimal128(amount))
      .append(FIELD_TYPE, type.name())
      .append(FIELD_PROCESSED, processed)
      .append(FIELD_REASON, reason)
      .append(FIELD_TRANSFERRED_TO_ACCOUNT_UUID, transferedToAccountUUID != null ? transferedToAccountUUID.toString() : null)
      .append("timestamp", Date.from(timestamp != null ? timestamp : Instant.now()));
  }
}
