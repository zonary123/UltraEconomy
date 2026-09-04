---
lang: en
---

# 💰 UltraEconomy

### 🧱 Advanced Multi-Currency Economy System for Minecraft (Fabric 1.21.1)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-00aa00?style=for-the-badge&logo=minecraft)
![Java](https://img.shields.io/badge/Java-21+-red?style=for-the-badge&logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**UltraEconomy** is a high-performance, multi-currency economy system designed for modern Minecraft servers. Built on Fabric, it features multi-database persistence, automated backups, an in-game graphical interface (GUI) for logs and backups, and a built-in web server with a secure HTTP API.

---

## ⚙️ Key Features

- 💱 **Multi-Currency Support**  
  Define as many currencies as you want! Each currency is highly configurable (custom symbols, formatting patterns, singular/plural names, and short suffix formatting).
- 🗄️ **Multi-Database Compatibility**  
  Supports **SQLite** (default), **MongoDB**, **MySQL**, and generic **SQL** databases for cross-server setups.
- 🌐 **Built-in Web UI & HTTP API**  
  Includes a built-in Jetty web server to host a dashboard or query economy data externally. Features rate-limiting, IP auto-banning, and bearer-token authentication.
- 🔁 **Economy Migration Support**  
  Easily migrate balances from other mods or plugins (e.g., Impactor) directly into UltraEconomy.
- 📦 **Automated Backup & Restore**  
  Automated hourly backups with configurable retention policies and a visual in-game restore manager.
- 🧩 **Placeholders Integration**  
  Compatible with the standard Placeholder API to display player stats on menus, scoreboards, and tab lists.
- ⚡ **High-Performance Caching**  
  Uses Caffeine Cache to minimize database operations and guarantee sub-millisecond response times.
- 🪟 **Rich In-Game GUIs**  
  Includes dedicated GUI menus to view transaction logs, browse/restore backups, and view the balance leaderboards.

---

## 📁 Configuration Structure

The configuration files are created under `config/ultraeconomy/`.

### 1. Main Configuration (`config.json`)
```json
{
  "debug": false,
  "notifications": true,
  "queueMessages": false,
  "commandCooldown": "500ms",
  "retentionBackUps": "7d",
  "betweenMessagesDelay": "1s",
  "lang": "en_us",
  "commands": [
    "money",
    "balance",
    "bal",
    "eco",
    "ultraeconomy"
  ],
  "database": {
    "type": "SQLITE",
    "url": "jdbc:sqlite:./config/ultraeconomy/ultraeconomy.db",
    "user": "",
    "password": "",
    "poolSize": 10
  },
  "migration": {
    "active": false,
    "migrations": [
      {
        "economyId": "IMPACTOR",
        "migrationFromCurrencyId": "impactor:dollars",
        "migrationToCurrencyId": "dollars"
      }
    ]
  },
  "limitTopPlayers": 10,
  "adjustmentShortName": 3,
  "balTopCooldown": "10s",
  "cacheTtlSeconds": 60,
  "web": false,
  "webSecurity": {
    "enabled": true,
    "port": 8080,
    "apiToken": "YOUR-RANDOM-API-TOKEN-HERE",
    "apiRateLimit": 20,
    "apiBurstCapacity": 40,
    "staticRateLimit": 60,
    "staticBurstCapacity": 120,
    "banThreshold": 10,
    "banDurationMinutes": 15,
    "trustProxy": false,
    "idleTimeoutSeconds": 30,
    "maxRequestBodyBytes": 8192
  }
}
```

### 2. Currencies Config (`currencys/<currency_id>.json`)
Each currency file lives in `config/ultraeconomy/currencys/`. For example, `dollars.json`:
```json
{
  "primary": true,
  "transferable": true,
  "defaultBalance": 500.00,
  "decimals": 2,
  "symbol": "$",
  "format": "<symbol>&6<amount> <name>",
  "singular": "Dollar",
  "plural": "Dollars",
  "suffixes": ["", "K", "M", "B", "T"],
  "currencyIds": [
    "impactor:dollars"
  ]
}
```

---

## 💬 Commands & Permissions

### Player Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/<cmd> [player]` | Check your balance or another player's balance | `ultraeconomy.command.balance`<br>`ultraeconomy.command.balance.other` |
| `/pay <currency> <amount> <player>` | Pay another player a specific amount | `ultraeconomy.command.pay` |
| `/baltop [currency] [page]` | View the leaderboard for a currency in chat | `ultraeconomy.command.baltop` |
| `/baltopmenu [currency]` | Open the leaderboard in a GUI menu | `ultraeconomy.command.baltopmenu` |
| `/withdraw <amount> [currency]` | Withdraw money from your account | `ultraeconomy.command.withdraw` |
| `/deposit <amount> [currency]` | Deposit money to your account | `ultraeconomy.command.deposit` |
| `/set <amount> [currency]` | Set your own balance | `ultraeconomy.command.set` |
| `/reset [currency]` | Reset your balance to default | `ultraeconomy.command.reset` |

### Admin Commands (Permission: `ultraeconomy.admin` / OP 2)
*   `/eco admin reload` - Reloads all configuration and currency files.
*   `/eco admin deposit <player> <amount> [currency]` - Deposits currency into a player's account.
*   `/eco admin withdraw <player> <amount> [currency]` - Withdraws currency from a player's account.
*   `/eco admin set <player> <amount> [currency]` - Sets a player's balance.
*   `/eco admin reset <player> [currency]` - Resets a player's balance.
*   `/eco admin transactions <player>` - Opens the GUI to view a player's transaction history. (Permission: `ultraeconomy.admin.transactions`)
*   `/eco admin backup <create/restore/list>` - Manage database backups. `/list` opens a GUI menu in-game. (Permission: `ultraeconomy.admin.backup`)

---

## 🧩 Placeholders

Use these Placeholders (via **Placeholder API**) to display economy stats:

*   `%ultraeconomy:balance <currency_id>%` - Returns the formatted balance (e.g. `$1,250.50 Dollars`).
*   `%ultraeconomy:symbol <currency_id>%` - Returns the currency symbol (e.g. `$`).
*   `%ultraeconomy:amount <currency_id>%` - Returns the raw formatted amount without the symbol (e.g. `1,250.50`).
*   `%ultraeconomy:short_amount <currency_id>%` - Returns the shortened amount with suffixes (e.g. `1.2K`, `5.4M`).

---

## 🧠 Developer API

Add **UltraEconomy** to your dependencies to interact with the economy programmatically. 

### API Usage Example

```java
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.UUID;

public class EconomyExample {
    
    public void handleEconomy(ServerPlayerEntity player, UUID targetPlayerUUID) {
        UUID playerId = player.getUuid();
        String currencyId = "dollars";

        // 1. Get a player's balance
        BigDecimal balance = UltraEconomyApi.getBalance(playerId, currencyId);

        // 2. Check if player has enough money
        boolean hasEnough = UltraEconomyApi.hasEnoughBalance(playerId, currencyId, new BigDecimal("100.00"));

        if (hasEnough) {
            // 3. Withdraw money
            boolean successWithdraw = UltraEconomyApi.withdraw(playerId, currencyId, new BigDecimal("100.00"));
            
            // 4. Deposit money
            boolean successDeposit = UltraEconomyApi.deposit(targetPlayerUUID, currencyId, new BigDecimal("100.00"));
        }

        // 5. Transfer directly between player accounts (handles thread-safety and deadlocks internally)
        boolean successTransfer = UltraEconomyApi.transfer(playerId, targetPlayerUUID, currencyId, new BigDecimal("50.00"));

        // 6. Set balance directly
        UltraEconomyApi.setBalance(playerId, currencyId, new BigDecimal("1000.00"));

        // 7. Get currency object and format values using the player's client language locale
        Currency currency = UltraEconomyApi.getCurrency(currencyId);
        String formatted = currency.format(new BigDecimal("15000.50"), UltraEconomyApi.getLocale(player)); // Output example: "$15,000.50 Dollars"
        
        // 8. Access the raw Account model
        Account account = UltraEconomyApi.getAccount(playerId);
        if (account != null) {
            System.out.println("Player account name: " + account.getPlayerName());
        }
    }
}
```
