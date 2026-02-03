-- TokenEconomy MySQL Schema
-- Version: 1.0.0
-- Compatible with MySQL 8.0+

-- Player balance storage (replaces legacy 'economy' table)
CREATE TABLE IF NOT EXISTS {{prefix}}token_balances (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    player_name VARCHAR(16),
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_balance (balance DESC),
    INDEX idx_player_name (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Transaction history for auditing and analytics
CREATE TABLE IF NOT EXISTS {{prefix}}token_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_uuid CHAR(36),
    receiver_uuid CHAR(36),
    amount DECIMAL(19,4) NOT NULL,
    transaction_type ENUM('TRANSFER', 'DEPOSIT', 'WITHDRAW', 'ADMIN', 'SYSTEM') NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender_uuid),
    INDEX idx_receiver (receiver_uuid),
    INDEX idx_type (transaction_type),
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Schema version tracking
CREATE TABLE IF NOT EXISTS {{prefix}}schema_version (
    version INT NOT NULL PRIMARY KEY,
    description VARCHAR(255),
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert initial schema version
INSERT IGNORE INTO {{prefix}}schema_version (version, description) VALUES (1, 'Initial TokenEconomy schema');
