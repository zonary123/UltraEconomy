package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.util.mongodb.MongoDBManager;
import com.kingpixel.cobbleutils.util.mongodb.MongoDBService;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.exceptions.DatabaseConnectionException;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.BackupInfo;
import com.kingpixel.ultraeconomy.models.Currency;
import com.kingpixel.ultraeconomy.models.Transaction;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MongoDBClient extends DatabaseClient {
  // Nombres de las colecciones
  private static final String TRANSACTIONS_COLLECTION = "transactions";
  private static final String ACCOUNTS_COLLECTION = "accounts";
  private static final String BACKUPS_COLLECTION = "backups";

  public static final String FIELD_UUID = "uuid";
  public static final String FIELD_PLAYER_NAME = "player_name";
  public static final String FIELD_BALANCES = "balances";
  private static final String FIELD_ACCOUNT_UUID = "account_uuid";
  private static final String FIELD_CURRENCY_ID = "currency_id";
  private static final String FIELD_AMOUNT = "amount";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_PROCESSED = "processed";
  private static final String FIELD_BACKUP_UUID = "uuid";

  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
  /**
   * Incremented on every connect()/disconnect() cycle.
   * Each scheduled task captures its own sessionId and exits early if the value no longer matches,
   * preventing stale tasks from processing transactions after a reload.
   */
  private final AtomicLong sessionId = new AtomicLong(0);


  private MongoCollection<Document> accountsCollection;
  private MongoCollection<Document> transactionsCollection;
  private MongoCollection<Document> backupsCollection;

  private volatile boolean runningTransactions = false;

  @Override
  public synchronized void connect(DataBaseConfig config) {
    if (connected.get()) {
      UltraEconomy.LOGGER.warn("MongoDB already connected, ignoring connect()");
      return;
    }

    shuttingDown.set(false);

    try {
      MongoDBManager manager = getManager();
      if (manager == null || !manager.isConnected()) {
        throw new DatabaseConnectionException(config.getType().name());
      }

      accountsCollection = manager.getCollection(getDatabase(), ACCOUNTS_COLLECTION);
      transactionsCollection = manager.getCollection(getDatabase(), TRANSACTIONS_COLLECTION);
      backupsCollection = manager.getCollection(getDatabase(), BACKUPS_COLLECTION);

      ensureIndexes();

      runningTransactions = true;
      connected.set(true);

      long mySession = sessionId.incrementAndGet();
      UltraEconomy.getAsyncContext().scheduleAtFixedRate(
        () -> {
          if (sessionId.get() != mySession) return;
          safeCheckAndApplyTransactions();
        },
        0, 5, TimeUnit.SECONDS
      );

      UltraEconomy.LOGGER.info("Connected to MongoDB");

    } catch (Exception e) {
      connected.set(false);
      UltraEconomy.LOGGER.error("Could not connect to MongoDB", e);
      if (e instanceof DatabaseConnectionException dce) {
        throw dce;
      }
      throw new DatabaseConnectionException(config.getType().name(), e);
    }
  }

  private MongoDBManager getManager() {
    return MongoDBService.getOrCreateManager(UltraEconomy.config.getDatabase());
  }

  private String getDatabase() {
    return UltraEconomy.config.getDatabase().getDatabase();
  }

  private void safeCheckAndApplyTransactions() {
    if (!connected.get() || shuttingDown.get()) {
      UltraEconomy.LOGGER.debug("Skipping transaction processing, not connected or shutting down.");
      return;
    }

    try {
      checkAndApplyTransactions();
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Transaction worker crashed", e);
    }
  }


  private void ensureIndexes() {
    Set<String> existingIndexes = new HashSet<>();
    for (Document index : accountsCollection.listIndexes()) {
      existingIndexes.add(index.get("name", String.class));
    }

    if (!existingIndexes.contains("uuid_1")) {
      accountsCollection.createIndex(new Document(FIELD_UUID, 1));
    }

    if (transactionsCollection != null) {
      existingIndexes.clear();
      for (Document index : transactionsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("account_uuid_1")) {
        transactionsCollection.createIndex(new Document(FIELD_ACCOUNT_UUID, 1));
      }
      if (!existingIndexes.contains("currency_id_1")) {
        transactionsCollection.createIndex(new Document(FIELD_CURRENCY_ID, 1));
      }
      if (!existingIndexes.contains("processed_1")) {
        transactionsCollection.createIndex(new Document(FIELD_PROCESSED, 1));
      }
    }

    UltraEconomy.LOGGER.info("Indexes verified/created successfully.");
  }

  @Override
  public synchronized void disconnect() {
    if (!connected.get()) return;
    runningTransactions = false;
    shuttingDown.set(true);

    sessionId.incrementAndGet();
    connected.set(false);

    UltraEconomy.LOGGER.info("Disconnected from MongoDB safely");
  }


  @Override
  public void invalidate(UUID playerUUID) {
    DatabaseFactory.ACCOUNTS.invalidate(playerUUID);
  }

  @Override
  public boolean isConnected() {
    return getManager().isConnected();
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = DatabaseFactory.ACCOUNTS.getIfPresent(uuid);
    if (cached != null) return cached;

    Document doc = accountsCollection.find(Filters.eq(FIELD_UUID, uuid.toString())).first();
    Account account;
    if (doc != null) {
      account = Account.fromDocument(doc);
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        account = new Account(player);
        saveOrUpdateAccount(account);
      } else {
        UltraEconomy.LOGGER.warn("Could not find player with UUID " + uuid);
        return null;
      }
    }
    DatabaseFactory.ACCOUNTS.put(uuid, account);
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    UltraEconomy.runAsync(() -> saveAccount(account));
  }

  @Override
  public void saveOrUpdateAccountSync(Account account) {
    saveAccount(account);
  }

  private void saveAccount(Account account) {
    if (account == null) {
      UltraEconomy.LOGGER.warn("Tried to save a null account.");
      return;
    }
    Document accountDoc = account.toDocument();

    accountsCollection.replaceOne(
      Filters.eq(FIELD_UUID, account.getPlayerUUID().toString()),
      accountDoc,
      new ReplaceOptions().upsert(true)
    );
    account.markClean();
  }

  public void addTransaction(UUID uuid, Currency currency, BigDecimal amount, TransactionType type, boolean processed) {
    UltraEconomy.runAsync(() -> {
      Transaction transaction = new Transaction(uuid, currency.getId(), amount, type, processed, Instant.now());
      transactionsCollection.insertOne(transaction.toDocument());
    });
  }

  @Override
  public CompletableFuture<?> createBackUp() {
    return UltraEconomy.runAsync(() -> {
      try {
        List<Document> accounts = new ArrayList<>();
        for (Document doc : accountsCollection.find()) {
          accounts.add(doc);
        }

        List<Document> transactions = new ArrayList<>();
        for (Document doc : transactionsCollection.find()) {
          transactions.add(doc);
        }

        Document backup = new Document("created_at", Date.from(Instant.now()))
          .append("uuid", UUID.randomUUID().toString())
          .append("accounts", accounts)
          .append("transactions", transactions);

        backupsCollection.insertOne(backup);

        if (UltraEconomy.config.isDebug()) {
          UltraEconomy.LOGGER.info("MongoDB backup created successfully");
        }

      } catch (Exception e) {
        UltraEconomy.LOGGER.error("Error creating MongoDB backup", e);
      }
      cleanOldBackUps();
    });
  }

  protected void cleanOldBackUps() {
    try {
      long millis = UltraEconomy.config.getRetentionBackUps().toMillis();

      Instant limit = Instant.now().minus(millis, TimeUnit.MILLISECONDS.toChronoUnit());
      Date limitDate = Date.from(limit);

      long deleted = backupsCollection.deleteMany(
        Filters.lt("created_at", limitDate)
      ).getDeletedCount();

      if (deleted > 0 && UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.info("Deleted " + deleted + " old backups");
      }
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Error cleaning old backups", e);
    }
  }

  @Override
  public List<BackupInfo> getBackups() {
    List<BackupInfo> backups = new ArrayList<>();
    try {
      for (Document doc : backupsCollection.find().sort(new Document("created_at", -1))) {
        try {
          @SuppressWarnings("unchecked")
          List<Document> accounts = doc.getList("accounts", Document.class);
          @SuppressWarnings("unchecked")
          List<Document> transactions = doc.getList("transactions", Document.class);
          Date createdAt = doc.getDate("created_at");

          BackupInfo info = BackupInfo.builder()
            .backupUUID(UUID.fromString(doc.getString("uuid")))
            .createdAt(createdAt != null ? createdAt.toInstant() : Instant.now())
            .accountCount(accounts != null ? accounts.size() : 0)
            .transactionCount(transactions != null ? transactions.size() : 0)
            .build();
          backups.add(info);
        } catch (Exception e) {
          UltraEconomy.LOGGER.warn("Could not read backup document", e);
        }
      }
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Error listing MongoDB backups", e);
    }
    return backups;
  }

  @Override
  public List<Account> getAccounts(int limit, int page) {
    return accountsCollection.find().limit(limit)
      .skip((Math.max(page - 1, 0)) * limit)
      .map(Account::fromDocument)
      .into(new ArrayList<>());
  }

  @Override
  public List<Transaction> getTransactions(
    UUID uuid,
    int limit
  ) {
    var filter = Filters.and(
      Filters.eq(FIELD_ACCOUNT_UUID, uuid.toString()),
      Filters.gte(FIELD_AMOUNT, new Decimal128(BigDecimal.ONE))
    );

    return transactionsCollection.find(filter)
      .sort(Sorts.descending("timestamp"))
      .limit(limit)
      .map(Transaction::fromDocument)
      .into(new ArrayList<>());
  }

  @Override
  public Account getAccountByName(String name) {
    var filter = Filters.eq(FIELD_PLAYER_NAME, name);
    Document doc = accountsCollection.find(filter).first();
    if (doc != null) {
      Account account = Account.fromDocument(doc);
      DatabaseFactory.ACCOUNTS.put(account.getPlayerUUID(), account);
      return account;
    }
    return null;
  }

  @Override
  public void loadBackUp(UUID backupUUID) {
    UltraEconomy.runAsync(() -> {
      try {
        Document backup = backupsCollection.find(
          Filters.eq(FIELD_BACKUP_UUID, backupUUID.toString())
        ).first();

        if (backup == null) {
          UltraEconomy.LOGGER.warn("Backup not found: " + backupUUID);
          return;
        }

        List<Document> accounts = backup.getList("accounts", Document.class);
        List<Document> transactions = backup.getList("transactions", Document.class);

        if (accounts == null || transactions == null) {
          throw new IllegalStateException("Backup corrupted");
        }


        String accTmp = "accounts_restore_tmp";
        String txTmp = "transactions_restore_tmp";

        getManager().getCollection(getDatabase(), accTmp).drop();
        getManager().getCollection(getDatabase(), txTmp).drop();

        MongoCollection<Document> tmpAccounts = getManager().getCollection(getDatabase(), accTmp);
        MongoCollection<Document> tmpTx = getManager().getCollection(getDatabase(), txTmp);

        tmpAccounts.insertMany(accounts);
        tmpTx.insertMany(transactions);

        tmpAccounts.renameCollection(
          new MongoNamespace(getDatabase(), ACCOUNTS_COLLECTION),
          new RenameCollectionOptions().dropTarget(true)
        );

        tmpTx.renameCollection(
          new MongoNamespace(getDatabase(), TRANSACTIONS_COLLECTION),
          new RenameCollectionOptions().dropTarget(true)
        );

        DatabaseFactory.ACCOUNTS.invalidateAll();

        UltraEconomy.LOGGER.info("Backup restored successfully: " + backupUUID);

      } catch (Exception e) {
        UltraEconomy.LOGGER.error("Error restoring backup: " + backupUUID, e);
      }
    });
  }


  private void checkAndApplyTransactions() {
    if (shuttingDown.get()) {
      UltraEconomy.LOGGER.debug("Skipping transaction processing, shutting down.");
      return;
    }
    if (!runningTransactions) {
      UltraEconomy.LOGGER.debug("Skipping transaction processing, not running.");
      return;
    }
    if (UltraEconomy.server == null) {
      UltraEconomy.LOGGER.debug("Skipping transaction processing, server not ready.");
      return;
    }

    var players = UltraEconomy.server.getPlayerManager().getPlayerList();
    List<String> uuids = players.stream()
      .map(Entity::getUuidAsString)
      .toList();

    if (uuids.isEmpty()) return;

    try {
      List<Document> pending = transactionsCollection.find(
        Filters.and(
          Filters.eq(FIELD_PROCESSED, false),
          Filters.in(FIELD_ACCOUNT_UUID, uuids)
        )
      ).limit(100).into(new ArrayList<>());

      for (Document tx : pending) {
        if (shuttingDown.get()) break;

        UUID uuid = UUID.fromString(tx.getString(FIELD_ACCOUNT_UUID));
        ServerPlayerEntity player = UltraEconomy.server.getPlayerManager().getPlayer(uuid);
        Account account = getCachedAccount(uuid);
        if (account == null || player == null || player.isDisconnected()) {
          continue;
        }

        String currencyId = tx.getString(FIELD_CURRENCY_ID);
        Currency currency = Currencies.getCurrency(currencyId);
        BigDecimal amount;
        Object rawAmount = tx.get(FIELD_AMOUNT);
        switch (rawAmount) {
          case String s -> amount = new BigDecimal(s);
          case Decimal128 d -> amount = d.bigDecimalValue();
          case Integer i -> amount = BigDecimal.valueOf(i);
          case Long l -> amount = BigDecimal.valueOf(l);
          case Double d -> amount = BigDecimal.valueOf(d);
          case Float f -> amount = BigDecimal.valueOf(f);
          default -> {
            UltraEconomy.LOGGER.error("Unknown amount type in transaction: {}", tx.toJson());
            continue;
          }
        }

        TransactionType type;
        try {
          type = TransactionType.valueOf(tx.getString("type"));
        } catch (IllegalArgumentException ex) {
          UltraEconomy.LOGGER.error("Invalid transaction type: {}", tx.toJson(), ex);
          continue;
        }

        // Step 1: apply the balance change in memory
        boolean applied;
        BigDecimal previousBalance = account.getBalance(currency);
        switch (type) {
          case DEPOSIT -> applied = account.addBalance(currency, amount);
          case WITHDRAW -> applied = account.removeBalance(currency, amount);
          case SET -> {
            account.setBalance(currency, amount);
            applied = true;
          }
          default -> {
            UltraEconomy.LOGGER.warn("Unhandled transaction type: {}", type);
            continue;
          }
        }

        if (!applied) {
          if (UltraEconomy.config.isDebug()) {
            UltraEconomy.LOGGER.warn("Insufficient funds for pending WITHDRAW: {}", tx.toJson());
          }
          // Mark as processed so we don't retry a permanently-failing withdraw
          transactionsCollection.updateOne(
            Filters.eq("_id", tx.getObjectId("_id")),
            Updates.set(FIELD_PROCESSED, true)
          );
          continue;
        }

        // Step 2: persist the account synchronously before marking the tx processed
        saveAccount(account);

        // Step 3: conditional mark-as-processed — only succeeds if ANOTHER server hasn't
        // already processed this tx (optimistic concurrency for cross-server deployments).
        var updateResult = transactionsCollection.updateOne(
          Filters.and(
            Filters.eq("_id", tx.getObjectId("_id")),
            Filters.eq(FIELD_PROCESSED, false)
          ),
          Updates.set(FIELD_PROCESSED, true)
        );

        if (updateResult.getModifiedCount() == 0) {
          // Concurrent processing detected: another server processed this tx first.
          // Compensate by undoing the balance change that was already applied.
          UltraEconomy.LOGGER.warn("[CrossServer] Concurrent tx detected ({}), compensating balance.", tx.getObjectId("_id"));
          switch (type) {
            case DEPOSIT -> account.removeBalance(currency, amount);
            case WITHDRAW -> account.addBalance(currency, amount);
            case SET -> account.setBalance(currency, previousBalance); // Restore previous value
            default -> { /* unreachable */ }
          }
          saveAccount(account);
        } else if (UltraEconomy.config.isDebug()) {
          UltraEconomy.LOGGER.info("Processed transaction: {}", tx.toJson());
        }
      }
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Error processing transactions", e);
    }
  }


  @Override
  public List<Account> getTopBalances(Currency currency, int page, int playersPerPage) {
    List<Account> topAccounts = new ArrayList<>();
    int skip = (page - 1) * playersPerPage;

    try {
      String balanceField = FIELD_BALANCES + "." + currency.getId();
      var cursor = accountsCollection.find()
        .sort(new Document(balanceField, -1))
        .skip(skip)
        .limit(playersPerPage + 1);

      int rank = skip + 1;
      for (Document doc : cursor) {
        Account account = Account.fromDocument(doc);
        account.setRank(rank++);
        topAccounts.add(account);
      }
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("Error fetching top balances for " + currency.getId(), e);
    }

    return topAccounts;
  }

  @Override
  public boolean existPlayerWithUUID(UUID uuid) {
    Document doc = accountsCollection.find(Filters.eq(FIELD_UUID, uuid.toString())).first();
    return doc != null;
  }

  @Override
  public boolean addBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = true;
    if (account == null) {
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.warn("Account not found in cache for UUID: " + uuid + ", queuing transaction.");
      }
      addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, false);
    } else {
      if (UltraEconomy.config.isDebug()) {
        UltraEconomy.LOGGER.info("Account found in cache for UUID: " + uuid + ", adding balance.");
      }
      result = account.addBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, true);
    }
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      // Player not cached — load from DB to verify balance before withdrawing.
      // Blindly queueing a WITHDRAW would return true without checking funds.
      account = getAccount(uuid);
      if (account == null) return false;
    }
    boolean result = account.removeBalance(currency, amount);
    if (result) addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
    return result;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.SET, false);
    } else {
      account.setBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.SET, true);
    }
    return amount;
  }

  @Override
  public BigDecimal getBalance(UUID uuid, Currency currency) {
    Account account = getAccount(uuid);
    if (account == null) return null;
    return account.getBalance(currency);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    if (account == null) return false;
    return account.hasEnoughBalance(currency, amount);
  }
}
