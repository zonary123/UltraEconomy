package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.models.Account;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mongodb.client.model.Filters.eq;

public class MongoDBClient extends DatabaseClient {

  private MongoClient mongoClient;
  private MongoDatabase database;
  private MongoCollection<Document> accountCollection;

  // Cache for quick access
  public static final Cache<UUID, Account> accounts = Caffeine.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .maximumSize(10_000)
    .removalListener((key, value, cause) -> {
      CobbleUtils.LOGGER.info("Account with UUID " + key + " removed from cache due to " + cause);
    })
    .build();

  @Override
  public void connect(DataBaseConfig config) {
    mongoClient = MongoClients.create(config.getUrl());
    database = mongoClient.getDatabase(config.getDatabase());
    accountCollection = database.getCollection("accounts");
    CobbleUtils.LOGGER.info("Connected to MongoDB database at " + config.getUrl());
  }

  @Override
  public void disconnect() {
    if (mongoClient != null) {
      mongoClient.close();
      CobbleUtils.LOGGER.info("Disconnected from MongoDB database.");
    }
  }

  @Override
  public boolean isConnected() {
    return mongoClient != null;
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = accounts.getIfPresent(uuid);
    if (cached != null) return cached;

    Document doc = accountCollection.find(eq("uuid", uuid.toString())).first();
    Account account;

    if (doc != null) {
      String playerName = doc.getString("player_name");
      Map<String, BigDecimal> balances = new HashMap<>();
      Document balanceDoc = (Document) doc.get("balances");
      if (balanceDoc != null) {
        for (String key : balanceDoc.keySet()) {
          balances.put(key, new BigDecimal(balanceDoc.getString(key)));
        }
      }
      account = new Account(uuid, playerName, balances);
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        CobbleUtils.LOGGER.info("Creating new account for " + player.getName().getString());
        account = new Account(player);
        saveOrUpdateAccount(account);
      } else {
        CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid + ", account creation failed.");
        return null;
      }
    }

    accounts.put(uuid, account);
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    Document doc = new Document()
      .append("uuid", account.getPlayerUUID().toString())
      .append("player_name", account.getPlayerName());

    Map<String, String> balanceMap = new HashMap<>();
    for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
      balanceMap.put(entry.getKey(), entry.getValue().toPlainString());
    }
    doc.append("balances", balanceMap);

    accountCollection.replaceOne(eq("uuid", account.getPlayerUUID().toString()), doc,
      new com.mongodb.client.model.ReplaceOptions().upsert(true));
    accounts.put(account.getPlayerUUID(), account);
  }

  private void updateBalanceField(UUID uuid, String currency, BigDecimal newAmount) {
    accountCollection.updateOne(eq("uuid", uuid.toString()),
      new Document("$set", new Document("balances." + currency, newAmount.toPlainString())));
  }

  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    boolean result = account.addBalance(currency, amount);
    if (result) {
      updateBalanceField(uuid, currency, account.getBalance(currency));
      accounts.put(uuid, account); // update cache
    }
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    boolean result = account.removeBalance(currency, amount);
    if (result) {
      updateBalanceField(uuid, currency, account.getBalance(currency));
      accounts.put(uuid, account); // update cache
    }
    return result;
  }

  @Override
  public BigDecimal getBalance(UUID uuid, String currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getAccount(uuid);
    BigDecimal result = account.setBalance(currency, amount);
    updateBalanceField(uuid, currency, result);
    accounts.put(uuid, account);
    return result;
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(String currency, int page) {
    int pageSize = 10;
    int skip = (page - 1) * pageSize;

    List<Account> topAccounts = new java.util.ArrayList<>();
    AtomicInteger rankOffset = new AtomicInteger(skip + 1);

    // Ordenamos por balance descendente
    accountCollection.find()
      .sort(new Document("balances." + currency, -1))
      .skip(skip)
      .limit(pageSize)
      .forEach(newDoc -> {
        UUID uuid = UUID.fromString(newDoc.getString("uuid"));
        String playerName = newDoc.getString("player_name");
        Map<String, BigDecimal> balances = new HashMap<>();
        Document balanceDoc = (Document) newDoc.get("balances");
        if (balanceDoc != null) {
          for (String key : balanceDoc.keySet()) {
            balances.put(key, new BigDecimal(balanceDoc.getString(key)));
          }
        }
        Account account = new Account(uuid, playerName, balances);

        // Asignamos el rank según la posición en la página
        account.setRank(rankOffset.getAndIncrement());

        // Actualizamos cache
        accounts.put(uuid, account);

        topAccounts.add(account);
      });

    return topAccounts;
  }

}
