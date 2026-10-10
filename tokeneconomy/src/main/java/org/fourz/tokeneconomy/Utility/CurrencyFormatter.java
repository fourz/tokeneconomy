package org.fourz.tokeneconomy.Utility;

import java.text.DecimalFormat;
import org.bukkit.ChatColor;

public class CurrencyFormatter {
    // DecimalFormat is not thread-safe, and placeholders (#2109) format from async chat and
    // scoreboard threads as well as the main thread - one instance per thread.
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##"));

    public static String format(double amount, String singular, String plural) {
        return ChatColor.GOLD + plain(amount, singular, plural);
    }

    public static String format(double amount, String symbol) {
        return ChatColor.GOLD + plainSymbol(amount, symbol);
    }

    /** {@code 1,234.5 Wizbucks} - the text of {@code /balance} without its colour code. */
    public static String plain(double amount, String singular, String plural) {
        String name = amount == 1 ? singular : plural;
        return DECIMAL_FORMAT.get().format(amount) + " " + name;
    }

    /** {@code 1,234.5W} - the text of {@code /balance <player>} without its colour code. */
    public static String plainSymbol(double amount, String symbol) {
        return DECIMAL_FORMAT.get().format(amount) + symbol;
    }
}
