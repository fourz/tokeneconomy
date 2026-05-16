package org.fourz.tokeneconomy.Data;

/**
 * Base class for DataStore implementations — provides the shared table()
 * helper so neither MySQLDataStore nor SQLiteDataStore need their own copy.
 */
public abstract class AbstractDataStore implements DataStore {

    private final String tablePrefix;

    protected AbstractDataStore(String tablePrefix) {
        this.tablePrefix = tablePrefix != null ? tablePrefix : "";
    }

    /** Returns the prefixed table name (e.g. "token_economy" from prefix "token_" + "economy"). */
    protected String table(String baseName) {
        return tablePrefix.isEmpty() ? baseName : tablePrefix + baseName;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }
}
