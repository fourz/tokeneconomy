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

## Compatibility

- **Minecraft Versions**: 1.21+
- **Server Software**: Spigot, Paper, and forks
- **Dependencies**: Vault (required)
- **Java Version**: Java 17+

## Support & Contributing

- **Issues**: Report bugs on the [GitHub Issues](https://github.com/fourz/tokeneconomy/issues) page
- **Discussions**: Join discussions on the [GitHub Discussions](https://github.com/fourz/tokeneconomy/discussions) page
- **Contributing**: See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for contribution guidelines

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Credits

Developed and maintained by [fourz](https://github.com/fourz) and contributors.
