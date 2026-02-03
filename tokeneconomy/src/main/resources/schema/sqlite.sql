-- TokenEconomy SQLite Schema
-- Version: 1.0.0
-- Compatible with SQLite 3+

-- Player balance storage (replaces legacy 'economy' table)
CREATE TABLE IF NOT EXISTS {{prefix}}token_balances (
    player_uuid TEXT NOT NULL PRIMARY KEY,
    player_name TEXT,
    balance REAL NOT NULL DEFAULT 0.0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for token_balances
CREATE INDEX IF NOT EXISTS idx_balance ON {{prefix}}token_balances(balance DESC);
CREATE INDEX IF NOT EXISTS idx_player_name ON {{prefix}}token_balances(player_name);

-- Transaction history for auditing and analytics
CREATE TABLE IF NOT EXISTS {{prefix}}token_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_uuid TEXT,
    receiver_uuid TEXT,
    amount REAL NOT NULL,
    transaction_type TEXT NOT NULL CHECK(transaction_type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAW', 'ADMIN', 'SYSTEM')),
    description TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for token_transactions
CREATE INDEX IF NOT EXISTS idx_sender ON {{prefix}}token_transactions(sender_uuid);
CREATE INDEX IF NOT EXISTS idx_receiver ON {{prefix}}token_transactions(receiver_uuid);
CREATE INDEX IF NOT EXISTS idx_type ON {{prefix}}token_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_created ON {{prefix}}token_transactions(created_at DESC);

-- Schema version tracking
CREATE TABLE IF NOT EXISTS {{prefix}}schema_version (
    version INTEGER NOT NULL PRIMARY KEY,
    description TEXT,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Insert initial schema version (use INSERT OR IGNORE for SQLite)
INSERT OR IGNORE INTO {{prefix}}schema_version (version, description) VALUES (1, 'Initial TokenEconomy schema');
