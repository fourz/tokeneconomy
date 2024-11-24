package org.fourz.tokeneconomy.util;

import java.lang.foreign.SymbolLookup;
import java.text.DecimalFormat;
import org.fourz.tokeneconomy.TokenEconomy;



public class CurrencyFormatter {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    
    public static String format(double amount, TokenEconomy plugin) {
        String currencyName = amount == 1 ? 
            plugin.getConfigLoader().getCurrencyNameSingular() :
            plugin.getConfigLoader().getCurrencyNamePlural();
            
        return DECIMAL_FORMAT.format(amount) + " " + currencyName;
    }

    public static String format(double amount, TokenEconomy plugin, boolean useSymbol) {
        String currencySymbol = plugin.getConfigLoader().getCurrencySymbol();            
        return DECIMAL_FORMAT.format(amount) + " " + currencySymbol;
    }
}