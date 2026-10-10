package org.fourz.tokeneconomy.Placeholder;

import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Resolves {@code %tokeneconomy_*%} keys against {@link BalanceSnapshotCache} (#2109).
 *
 * <p>Holds no PlaceholderAPI types, so it loads and tests without PlaceholderAPI present. It reads
 * the cache only - no database access on the requesting thread.</p>
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@code balance}, {@code balance_formatted}, {@code balance_symbol}, {@code balance_short}</li>
 *   <li>{@code rank} - position in the cached leaderboard</li>
 *   <li>{@code currency}, {@code currency_plural}, {@code currency_symbol}</li>
 *   <li>{@code top_<n>_name}, {@code top_<n>_uuid}, {@code top_<n>_balance} and its
 *       {@code _formatted}, {@code _symbol}, {@code _short} forms</li>
 * </ul>
 *
 * <p>Answers: the economy refusing gives {@link PlaceholderSettings#unavailable()} for every
 * balance, rank and top key - never 0, which would tell a scoreboard the player is broke. An
 * unknown key, a missing player, a balance not loaded yet, a player outside the leaderboard and a
 * leaderboard row that does not exist give {@link PlaceholderSettings#fallback()}. Currency keys
 * come from config and work in both states. No method returns null or throws.</p>
 */
public final class PlaceholderResolver {

    private static final String TOP_PREFIX = "top_";
    private static final String BALANCE = "balance";

    private PlaceholderResolver() {}

    /**
     * @param params   the text between {@code %tokeneconomy_} and {@code %}
     * @param playerId the requesting player, or null (holograms, NPC names)
     */
    public static String resolve(String params, UUID playerId, BalanceSnapshotCache cache,
                                 PlaceholderSettings settings) {
        PlaceholderSettings s = settings != null ? settings
                : new PlaceholderSettings(null, null, null, null, null);
        try {
            if (params == null || params.isBlank() || cache == null) return s.fallback();
            String key = params.trim().toLowerCase(Locale.ROOT);

            switch (key) {
                case "currency":        return s.currencySingular();
                case "currency_plural": return s.currencyPlural();
                case "currency_symbol": return s.currencySymbol();
                case "rank":            return rank(playerId, cache, s);
                default:
                    break;
            }
            if (key.startsWith(BALANCE)) {
                String form = key.substring(BALANCE.length());
                if (!isBalanceForm(form)) return s.fallback();
                if (playerId == null) return s.fallback();
                if (!cache.isAvailable()) return s.unavailable();
                OptionalDouble balance = cache.cachedBalance(playerId);
                return balance.isPresent() ? formatBalance(form, balance.getAsDouble(), s) : s.fallback();
            }
            if (key.startsWith(TOP_PREFIX)) {
                return top(key, cache, s);
            }
            return s.fallback();
        } catch (RuntimeException e) {
            return s.fallback();
        }
    }

    private static String rank(UUID playerId, BalanceSnapshotCache cache, PlaceholderSettings s) {
        if (playerId == null) return s.fallback();
        if (!cache.isAvailable()) return s.unavailable();
        int position = cache.rank(playerId);
        return position > 0 ? Integer.toString(position) : s.fallback();
    }

    /** {@code top_<n>_name|uuid|balance[_formatted|_symbol|_short]}, n 1-based. */
    private static String top(String key, BalanceSnapshotCache cache, PlaceholderSettings s) {
        int numberEnd = key.indexOf('_', TOP_PREFIX.length());
        if (numberEnd <= TOP_PREFIX.length()) return s.fallback();
        int n;
        try {
            n = Integer.parseInt(key.substring(TOP_PREFIX.length(), numberEnd));
        } catch (NumberFormatException e) {
            return s.fallback();
        }
        String field = key.substring(numberEnd + 1);
        boolean balanceField = field.startsWith(BALANCE) && isBalanceForm(field.substring(BALANCE.length()));
        if (!field.equals("name") && !field.equals("uuid") && !balanceField) return s.fallback();
        if (n < 1) return s.fallback();

        if (!cache.isAvailable()) return s.unavailable();
        List<BalanceSnapshotCache.TopEntry> board = cache.top();
        if (n > board.size()) return s.fallback();
        BalanceSnapshotCache.TopEntry row = board.get(n - 1);
        if (field.equals("name")) return row.name();
        if (field.equals("uuid")) return row.uuid().toString();
        return formatBalance(field.substring(BALANCE.length()), row.balance(), s);
    }

    private static boolean isBalanceForm(String form) {
        return form.isEmpty() || form.equals("_formatted") || form.equals("_symbol") || form.equals("_short");
    }

    private static String formatBalance(String form, double amount, PlaceholderSettings s) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return s.fallback();
        return switch (form) {
            case "_formatted" -> CurrencyFormatter.plain(amount, s.currencySingular(), s.currencyPlural());
            case "_symbol" -> CurrencyFormatter.plainSymbol(amount, s.currencySymbol());
            case "_short" -> BalanceFormat.shortForm(amount);
            default -> BalanceFormat.raw(amount);
        };
    }
}
