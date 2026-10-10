package org.fourz.tokeneconomy.Placeholder;

import org.fourz.tokeneconomy.Utility.CurrencyFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Number formats behind %tokeneconomy_balance%, _short, _formatted and _symbol (#2109). */
class BalanceFormatTest {

    @Test
    void rawIsAPlainLocaleFreeNumberWithAtMostTwoDecimals() {
        assertEquals("0", BalanceFormat.raw(0));
        assertEquals("999", BalanceFormat.raw(999));
        assertEquals("1000", BalanceFormat.raw(1000), "no grouping, no exponent");
        assertEquals("999999", BalanceFormat.raw(999_999));
        assertEquals("1234.5", BalanceFormat.raw(1234.5));
        assertEquals("0.3", BalanceFormat.raw(0.1 + 0.2), "floating-point noise is trimmed");
        assertEquals("12.35", BalanceFormat.raw(12.345678));
        assertEquals("-50.25", BalanceFormat.raw(-50.25));
        assertEquals("0", BalanceFormat.raw(-0.001), "a rounded-away negative is not -0");
        assertEquals("1000000000", BalanceFormat.raw(1e9));
    }

    @Test
    void shortFormEdgeValues() {
        assertEquals("0", BalanceFormat.shortForm(0));
        assertEquals("999", BalanceFormat.shortForm(999));
        assertEquals("1k", BalanceFormat.shortForm(1000));
        assertEquals("1.2k", BalanceFormat.shortForm(1234));
        assertEquals("1.9k", BalanceFormat.shortForm(1999), "truncates, never rounds up");
        assertEquals("999.9k", BalanceFormat.shortForm(999_999), "never shows 1M for less than 1M");
        assertEquals("1M", BalanceFormat.shortForm(1_000_000));
        assertEquals("3.4M", BalanceFormat.shortForm(3_456_789));
        assertEquals("2B", BalanceFormat.shortForm(2e9));
        assertEquals("1500T", BalanceFormat.shortForm(1.5e15), "T is the largest unit");
        assertEquals("12.3", BalanceFormat.shortForm(12.34));
        assertEquals("999.9", BalanceFormat.shortForm(999.99));
    }

    @Test
    void shortFormKeepsTheSignOfNegatives() {
        assertEquals("-999", BalanceFormat.shortForm(-999));
        assertEquals("-1k", BalanceFormat.shortForm(-1000));
        assertEquals("-1.5k", BalanceFormat.shortForm(-1500));
        assertEquals("-999.9k", BalanceFormat.shortForm(-999_999));
        assertEquals("0", BalanceFormat.shortForm(-0.04), "a truncated-away negative is not -0");
    }

    @Test
    void nonFiniteValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BalanceFormat.raw(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> BalanceFormat.shortForm(Double.POSITIVE_INFINITY));
    }

    @Test
    void plainFormsMatchTheBalanceCommandWithoutItsColour() {
        assertEquals("1,234.5 Wizbucks", CurrencyFormatter.plain(1234.5, "Wizbuck", "Wizbucks"));
        assertEquals("1 Wizbuck", CurrencyFormatter.plain(1, "Wizbuck", "Wizbucks"));
        assertEquals("1,234.5W", CurrencyFormatter.plainSymbol(1234.5, "W"));
        assertTrue(CurrencyFormatter.format(1234.5, "Wizbuck", "Wizbucks").endsWith("1,234.5 Wizbucks"),
                "/balance output is unchanged: colour + the plain text");
    }
}
