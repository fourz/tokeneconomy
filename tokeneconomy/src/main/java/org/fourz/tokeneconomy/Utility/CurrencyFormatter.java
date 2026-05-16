package org.fourz.tokeneconomy.Utility;

import java.text.DecimalFormat;
import org.bukkit.ChatColor;

public class CurrencyFormatter {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");

    public static String format(double amount, String singular, String plural) {
        String name = amount == 1 ? singular : plural;
        return ChatColor.GOLD + DECIMAL_FORMAT.format(amount) + " " + name;
    }

    public static String format(double amount, String symbol) {
        return ChatColor.GOLD + DECIMAL_FORMAT.format(amount) + symbol;
    }
}