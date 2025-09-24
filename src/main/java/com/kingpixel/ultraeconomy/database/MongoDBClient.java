package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MongoDBClient extends DatabaseClient {

  private MongoClient mongoClient;
  private MongoDatabase database;
  private MongoCollection<Document> accountsCollection;
  private MongoCollection<Document> transactionsCollection;

  private ScheduledExecutorService transactionExecutor;
  private boolean runningTransactions = false;

  public static final Cache<UUID, Account> ACCOUNT_CACHE = Caffeine.newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .maximumSize(10_000)
    .removalListener((key, value, cause) -> {
      if (cause.equals(RemovalCause.REPLACED)) return;
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info("Account with UUID " + key + " removed from cache due to " + cause);
      }
      if (value != null) {
        DatabaseFactory.INSTANCE.saveOrUpdateAccount((Account) value);
      }
    })
    .build();

  @Override
  public void connect(DataBaseConfig config) {
    try {
      mongoClient = MongoClients.create(
        MongoClientSettings.builder()
          .applyConnectionString(new ConnectionString(config.getUrl()))
          .applicationName("UltraEconomy-MongoDB")
          .build()
      );
      database = mongoClient.getDatabase(config.getDatabase());

      accountsCollection = database.getCollection("accounts");
      transactionsCollection = database.getCollection("transactions");

      // asegurar índices
      ensureIndexes();

      // iniciar executor
      transactionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Mongo-Transaction-Worker");
        t.setDaemon(true);
        return t;
      });
      runningTransactions = true;
      transactionExecutor.scheduleAtFixedRate(this::checkAndApplyTransactions, 0, 2, TimeUnit.SECONDS);

      CobbleUtils.LOGGER.info("Connected to MongoDB at " + config.getUrl());
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("❌ Could not connect to MongoDB: " + e.getMessage());
      mongoClient = null;
      database = null;
    }
  }

  private void ensureIndexes() {
    try {
      Set<String> existingIndexes = new HashSet<>();
      for (Document index : accountsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("uuid_1")) {
        accountsCollection.createIndex(new Document("uuid", 1));
      }

      existingIndexes.clear();
      for (Document index : transactionsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("account_uuid_1")) {
        transactionsCollection.createIndex(new Document("account_uuid", 1));
      }
      if (!existingIndexes.contains("currency_id_1")) {
        transactionsCollection.createIndex(new Document("currency_id", 1));
      }
      if (!existingIndexes.contains("processed_1")) {
        transactionsCollection.createIndex(new Document("processed", 1));
      }

      CobbleUtils.LOGGER.info("Indexes verified/created successfully.");
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("Error ensuring MongoDB indexes: " + e.getMessage());
    }
  }


  @Override
  public void disconnect() {
    if (transactionExecutor != null) {
      runningTransactions = false;
      transactionExecutor.shutdownNow();
    }
    if (mongoClient != null) {
      mongoClient.close();
      CobbleUtils.LOGGER.info("Disconnected from MongoDB.");
    }
  }

  @Override
  public void invalidate(UUID playerUUID) {
    ACCOUNT_CACHE.invalidate(playerUUID);
  }

  @Override
  public boolean isConnected() {
    return mongoClient != null;
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = ACCOUNT_CACHE.getIfPresent(uuid);
    if (cached != null) return cached;

    Document doc = accountsCollection.find(Filters.eq("uuid", uuid.toString())).first();
    Account account;
    if (doc != null) {
      String playerName = doc.getString("player_name");
      Map<String, BigDecimal> balances = new HashMap<>();
      Document balanceDoc = doc.get("balances", Document.class);
      if (balanceDoc != null) {
        for (String key : balanceDoc.keySet()) {
          balances.put(key, new BigDecimal(balanceDoc.getString(key)));
        }
      }
      account = new Account(uuid, playerName, balances);
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        account = new Account(player);
        saveOrUpdateAccount(account);
      } else {
        CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid);
        return null;
      }
    }

    ACCOUNT_CACHE.put(uuid, account);
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    Document balancesDoc = new Document();
    account.getBalances().forEach((k, v) -> balancesDoc.put(k, v.toPlainString()));

    Document doc = new Document("uuid", account.getPlayerUUID().toString())
      .append("player_name", account.getPlayerName())
      .append("balances", balancesDoc);

    accountsCollection.replaceOne(
      Filters.eq("uuid", account.getPlayerUUID().toString()),
      doc,
      new ReplaceOptions().upsert(true)
    );
  }

  private void addTransaction(UUID uuid, String currency, BigDecimal amount, TransactionType type, boolean processed) {
    Document tx = new Document("account_uuid", uuid.toString())
      .append("currency_id", currency)
      .append("amount", amount.toPlainString())
      .append("type", type.name())
      .append("processed", processed)
      .append("timestamp", Date.from(Instant.now()));
    transactionsCollection.insertOne(tx);
  }

  private void checkAndApplyTransactions() {
    if (!runningTransactions) return;

    try (MongoCursor<Document> cursor = transactionsCollection.find(Filters.eq("processed", false)).iterator()) {
      while (cursor.hasNext()) {
        Document tx = cursor.next();
        UUID uuid = UUID.fromString(tx.getString("account_uuid"));
        Account account = ACCOUNT_CACHE.getIfPresent(uuid);
        if (account == null) continue;

        String currency = tx.getString("currency_id");
        BigDecimal amount = new BigDecimal(tx.getString("amount"));
        TransactionType type = TransactionType.valueOf(tx.getString("type"));

        switch (type) {
          case DEPOSIT -> account.addBalance(currency, amount);
          case WITHDRAW -> account.removeBalance(currency, amount);
          case SET -> account.setBalance(currency, amount);
        }

        saveOrUpdateAccount(account);
        ACCOUNT_CACHE.put(uuid, account);

        // marcar procesado
        transactionsCollection.updateOne(
          Filters.eq("_id", tx.getObjectId("_id")),
          Updates.set("processed", true)
        );
      }
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("Error processing transactions");
      e.printStackTrace();
    }
  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = false;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, false);
    } else {
      result = account.addBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, true);
    }
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = false;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, false);
    } else {
      result = account.removeBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
    }
    return result;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
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
  public BigDecimal getBalance(UUID uuid, String currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(String currency, int page) {
    List<Account> topAccounts = new ArrayList<>();
    int pageSize = UltraEconomy.config.getLimitTopPlayers();
    int skip = (page - 1) * pageSize;

    FindIterable<Document> docs = accountsCollection.find()
      .sort(new Document("balances." + currency, -1))
      .skip(skip)
      .limit(pageSize);

    for (Document doc : docs) {
      UUID uuid = UUID.fromString(doc.getString("uuid"));
      String playerName = doc.getString("player_name");

      Map<String, BigDecimal> balances = new HashMap<>();
      Document balanceDoc = doc.get("balances", Document.class);
      if (balanceDoc != null && balanceDoc.containsKey(currency)) {
        balances.put(currency, new BigDecimal(balanceDoc.getString(currency)));
      }

      Account account = new Account(uuid, playerName, balances);
      topAccounts.add(account);
      ACCOUNT_CACHE.put(uuid, account);
    }

    return topAccounts;
  }

  public Account getCachedAccount(UUID uuid) {
    return ACCOUNT_CACHE.getIfPresent(uuid);
  }
}
