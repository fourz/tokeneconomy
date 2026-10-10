# TokenEconomy

TokenEconomy is a modern, efficient Minecraft economy plugin built for Spigot/Paper servers. It provides a comprehensive token-based economy system with Vault integration, multiple storage backends, and a robust command interface.

## Features

- **Multi-Storage Backend Support**
  - SQLite (default) for simple setups
  - MySQL for shared economies across multiple servers
  - Automatic migration between storage types

- **Vault Integration**
  - Full compatibility with other plugins requiring economy systems
  - Standardized economy API implementation
  - Seamless integration with existing server ecosystems

- **Comprehensive Command System**
  - Player balance management (`/balance`, `/pay`)
  - Administrative controls (`/economy add`, `/economy set`)
  - Leaderboard functionality (`/top`)
  - Debug tools for troubleshooting

- **Flexible Configuration**
  - Customizable currency names and symbols
  - Configurable messages and permissions
  - Storage type selection and database settings

- **Performance Optimized**
  - Efficient database operations with prepared statements
  - Connection pooling and retry mechanisms
  - Minimal server impact design

## Installation

1. **Download** the latest release from the [Releases](https://github.com/fourz/tokeneconomy/releases) page
2. **Install Vault** - TokenEconomy requires Vault to function properly
3. **Place** the `tokeneconomy-1.0-SNAPSHOT.jar` file into your server's `plugins/` folder
4. **Restart** the server to generate configuration files
5. **Configure** settings in `plugins/TokenEconomy/config.yml` as needed

## Quick Start

### Basic Commands

#### Player Commands

- `/balance` or `/bal` - Check your current balance
- `/pay <player> <amount>` - Send tokens to another player
- `/top` - View the richest players leaderboard

#### Admin Commands

- `/economy add <player> <amount>` - Give tokens to a player
- `/economy set <player> <amount>` - Set a player's balance
- `/economy debug` - View plugin diagnostic information

### Configuration

The main configuration file is located at `plugins/TokenEconomy/config.yml`:

```yaml
economy:  
  currencyNameSingular: token
  currencyNamePlural: tokens
  currencySymbol: 'T'

storage:
  type: sqlite  # or mysql
  mysql:
    host: localhost
    port: 3306
    database: tokeneconomy
    username: root
    password: ''
```

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `tokeneconomy.*` | Access to all commands | false |
| `tokeneconomy.balance` | Check balance | true |
| `tokeneconomy.pay` | Pay other players | false |
| `tokeneconomy.set` | Set player balances | false |
| `tokeneconomy.add` | Add to player balances | false |
| `tokeneconomy.top` | View leaderboards | true |

## PlaceholderAPI

When [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed and enabled, TokenEconomy registers the internal expansion `tokeneconomy` (no eCloud download). Without PlaceholderAPI the plugin enables normally and logs `PlaceholderAPI not enabled - %tokeneconomy_*% placeholders not registered.` (#2109)

| Placeholder | Example | Notes |
|-------------|---------|-------|
| `%tokeneconomy_balance%` | `1234.5` | Raw number: at most 2 decimals, no grouping, no symbol |
| `%tokeneconomy_balance_formatted%` | `1,234.5 Wizbucks` | Same text as `/balance`, without the colour code |
| `%tokeneconomy_balance_symbol%` | `1,234.5W` | Same text as `/balance <player>`, without the colour code |
| `%tokeneconomy_balance_short%` | `1.2k` | `999`, `1k`, `999.9k`, `3.4M`, `2B`, `1T`; truncates, never rounds up |
| `%tokeneconomy_rank%` | `3` | Position in the cached top list; fallback when outside it |
| `%tokeneconomy_currency%` | `Wizbuck` | `economy.currencyNameSingular` |
| `%tokeneconomy_currency_plural%` | `Wizbucks` | `economy.currencyNamePlural` |
| `%tokeneconomy_currency_symbol%` | `W` | `economy.currencySymbol` |
| `%tokeneconomy_top_<n>_name%` | `FZBunny` | Name of leaderboard row `n` (1-based), same naming as `/eco top` |
| `%tokeneconomy_top_<n>_uuid%` | `7f2123f3-...` | UUID of row `n` |
| `%tokeneconomy_top_<n>_balance%` | `2069` | Also `_balance_formatted`, `_balance_symbol`, `_balance_short` |

**What a placeholder returns when there is no value:**

| Situation | Returns |
|-----------|---------|
| Economy database unavailable (refusing store) | `placeholders.unavailable` (default `-`) for every balance, rank and top key. Never `0`. |
| Unknown key, no player (hologram), player not online on this server, balance not loaded yet, rank outside the top list, top row `n` past the end | `placeholders.fallback` (default empty) |

**No database access on the placeholder thread.** Values come from an in-memory cache. The cache holds the online players' balances and the top `placeholders.topSize` rows. It is read on a background thread on join, after every balance change made on this server, after `/eco reload`, and every `placeholders.refreshSeconds`. Changes made on another server sharing the economy appear after the next refresh. A failed read keeps the previous value. `rank` and `top_*` update on the timer only.

```yaml
placeholders:
  fallback: ''
  unavailable: '-'
  topSize: 100        # read at enable only
  refreshSeconds: 30  # read at enable only
```

All four keys are optional: an existing `config.yml` without them uses these defaults.

## Storage Configuration

### SQLite (Default)

No additional configuration required. Database file is automatically created in the plugin data folder.

### MySQL

Configure the MySQL section in `config.yml`:

```yaml
storage:
  type: mysql
  mysql:
    host: your-mysql-host
    port: 3306
    database: your_database
    username: your_username
    password: your_password
    useSSL: false
    tablePrefix: tokeneconomy_
```

## API Usage

TokenEconomy provides an API for other plugins to interact with player balances:

```java
// Get the TokenEconomy API
TokenEconomyAPI api = TokenEconomyAPI.getInstance();

// Check a player's balance
UUID playerUUID = player.getUniqueId();
double balance = api.getBalance(playerUUID);

// Deposit tokens
boolean success = api.deposit(playerUUID, 100.0);

// Withdraw tokens  
boolean success = api.withdraw(playerUUID, 50.0);

// Check if player has enough tokens
boolean hasEnough = api.has(playerUUID, 25.0);
```

## Development

### Building from Source

1. **Clone** the repository:

```bash
git clone https://github.com/fourz/tokeneconomy.git
cd tokeneconomy
```

1. **Build** using Maven:

```bash
mvn clean package
```

The compiled JAR will be in `tokeneconomy/target/tokeneconomy-1.0-SNAPSHOT.jar`.

### Project Structure

```text
tokeneconomy/
├── src/main/java/org/fourz/tokeneconomy/
│   ├── TokenEconomy.java           # Main plugin class
│   ├── TokenEconomyAPI.java        # Public API
│   ├── TokenEconomyVaultAdapter.java # Vault integration
│   ├── ConfigLoader.java           # Configuration management
│   ├── Command/                    # Command implementations
│   │   ├── EconomyCommand.java     # Main economy command
│   │   ├── BalanceCommand.java     # Balance checking
│   │   ├── PayCommand.java         # Player-to-player transfers
│   │   └── ...
│   ├── Data/                       # Data storage layer
│   │   ├── DataConnector.java      # Storage abstraction
│   │   ├── SQLiteDataStore.java    # SQLite implementation
│   │   └── MySQLDataStore.java     # MySQL implementation
│   ├── service/                    # RVNKCore service layer
│   │   ├── IEconomyService.java   # Service interface (registered with ServiceRegistry)
│   │   └── EconomyServiceImpl.java # Implementation wrapping DataConnector
│   └── Utility/
│       └── CurrencyFormatter.java  # Currency display formatting
└── src/main/resources/
    ├── config.yml                  # Default configuration
    └── plugin.yml                  # Plugin metadata
```

### Architecture

- **Modular Design**: Separated concerns with distinct classes for commands, data storage, and configuration
- **Storage Abstraction**: `DataStore` interface allows multiple backend implementations
- **Command Pattern**: Each command is a separate class extending `BaseCommand`
- **Configuration Management**: Centralized config loading with validation and migration support

## RVNKCore Integration

TokenEconomy optionally integrates with RVNKCore for cross-plugin service access via ServiceRegistry. The plugin works fully standalone without RVNKCore.

### Service Interface

When RVNKCore is available, `IEconomyService` is registered with the ServiceRegistry:

```java
// Access from another plugin via ServiceRegistry
IEconomyService economyService = serviceRegistry.getService(IEconomyService.class);
CompletableFuture<Double> balance = economyService.getBalance(playerUUID);
CompletableFuture<Boolean> result = economyService.deposit(playerUUID, 100.0);
```

**Available Methods**:
- `getBalance(UUID)` - Get player balance
- `deposit(UUID, double)` - Add to balance
- `withdraw(UUID, double)` - Remove from balance
- `setBalance(UUID, double)` - Set absolute balance
- `getTopBalances(int)` - Get leaderboard

### Integration Status

- **Level**: Minimal (service registration only)
- **Pattern**: Reflection-based registration (no hard dependency)
- **Full integration** (Repository, DTOs, CommandManager): Deferred until active development resumes

## Compatibility

- **Minecraft Versions**: 1.21+
- **Server Software**: Spigot, Paper, and forks
- **Dependencies**: Vault (required), RVNKCore (optional), PlaceholderAPI (optional)
- **Java Version**: Java 17+

## Support & Contributing

- **Issues**: Report bugs on the [GitHub Issues](https://github.com/fourz/tokeneconomy/issues) page
- **Discussions**: Join discussions on the [GitHub Discussions](https://github.com/fourz/tokeneconomy/discussions) page
- **Contributing**: See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for contribution guidelines

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Credits

Developed and maintained by [fourz](https://github.com/fourz) and contributors.
