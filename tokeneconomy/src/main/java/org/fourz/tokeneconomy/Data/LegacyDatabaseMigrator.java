package org.fourz.tokeneconomy.Data;

import java.io.File;
import java.sql.*;
import java.util.logging.Logger;

/**
 * Handles the one-time migration of the old economy database file location.
 *
 * <p>Earlier versions stored economy/database.db one level above the plugin data folder.
 * This migrator moves it to the current location on first startup when the
 * {@code storage.migrate_old_economy} config flag is true.
 */
public class LegacyDatabaseMigrator {

    private final File targetDbFile;
    private final Logger logger;

    public LegacyDatabaseMigrator(File targetDbFile, Logger logger) {
        this.targetDbFile = targetDbFile;
        this.logger = logger;
    }

    /**
     * Attempts to move the legacy database file into the current plugin data folder.
     * Safe to call even when no legacy file exists — logs and returns cleanly.
     */
    public void migrate() {
        File oldDbFile = new File(targetDbFile.getParentFile().getParentFile(), "economy/database.db");
        if (!oldDbFile.exists()) {
            logger.info("No legacy economy database found — skipping migration.");
            return;
        }
        logRecordCount(oldDbFile);
        if (targetDbFile.exists()) {
            logger.info("Target database already exists — skipping legacy file move.");
            return;
        }
        if (oldDbFile.renameTo(targetDbFile)) {
            tryDelete(oldDbFile);
            logger.info("Legacy economy database migrated successfully.");
        } else {
            logger.warning("Failed to move legacy economy database.");
        }
    }

    private void logRecordCount(File dbFile) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM economy")) {
            logger.info("Legacy database contains " + rs.getInt(1) + " records.");
        } catch (SQLException e) {
            logger.warning("Could not read legacy database record count: " + e.getMessage());
        }
    }

    private void tryDelete(File file) {
        if (!file.delete()) {
            logger.warning("Could not delete legacy database file at " + file.getPath());
        }
    }
}
