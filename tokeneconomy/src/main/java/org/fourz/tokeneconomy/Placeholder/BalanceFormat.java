package org.fourz.tokeneconomy.Placeholder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Number formats for the {@code %tokeneconomy_*%} placeholders (#2109) that {@code /balance} does
 * not already define. The named and symbol forms come from
 * {@link org.fourz.tokeneconomy.Utility.CurrencyFormatter}, so they match the command text.
 *
 * <p>Pure functions, no Bukkit types. Callers pass finite values; a NaN or infinite balance is
 * rejected with {@link IllegalArgumentException} so the resolver can answer its fallback.</p>
 */
public final class BalanceFormat {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final String[] UNITS = {"", "k", "M", "B", "T"};

    private BalanceFormat() {}

    /**
     * Plain number for scoreboards and maths: at most two decimals (the {@code /balance} precision),
     * no grouping, no symbol, locale-independent. {@code 1234.5}, {@code 1000}, {@code 0}.
     */
    public static String raw(double amount) {
        requireFinite(amount);
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros();
        return unsignedZero(value).toPlainString();
    }

    /**
     * Compact form: {@code 999}, {@code 1k}, {@code 1.2k}, {@code 999.9k}, {@code 3.4M}.
     *
     * <p>Truncates to one decimal and never rounds up, so a short value never shows more money than
     * the player holds: 999,999 is {@code 999.9k}, not {@code 1000k} or {@code 1M}. Negative values
     * keep their sign.</p>
     */
    public static String shortForm(double amount) {
        requireFinite(amount);
        BigDecimal scaled = BigDecimal.valueOf(Math.abs(amount));
        int unit = 0;
        while (unit < UNITS.length - 1 && scaled.compareTo(THOUSAND) >= 0) {
            scaled = scaled.divide(THOUSAND);
            unit++;
        }
        scaled = unsignedZero(scaled.setScale(1, RoundingMode.DOWN).stripTrailingZeros());
        String sign = amount < 0 && scaled.signum() != 0 ? "-" : "";
        return sign + scaled.toPlainString() + UNITS[unit];
    }

    private static BigDecimal unsignedZero(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value;
    }

    private static void requireFinite(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("not a finite balance: " + amount);
        }
    }
}
