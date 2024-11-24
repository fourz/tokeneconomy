
package org.fourz.tokeneconomy.util;

import java.text.DecimalFormat;
import org.fourz.tokeneconomy.TokenEconomy;

public class CurrencyFormatter {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("#,##0");
    
    public static String format(double amount, TokenEconomy plugin) {
        String formattedAmount;
        if (amount % 1 == 0) {
            formattedAmount = WHOLE_FORMAT.format(amount);
        } else {
            formattedAmount = DECIMAL_FORMAT.format(amount);
        }
        return String.format("%s %s", formattedAmount,
            amount == 1 ? plugin.getConfigLoader().getCurrencyNameSingular() 
                      : plugin.getConfigLoader().getCurrencyNamePlural());
    }
}