package org.fourz.tokeneconomy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

public class TokenEconomyVaultAdapter implements Economy {

    private final TokenEconomy plugin;
    private final ConfigLoader configLoader;

    public TokenEconomyVaultAdapter(TokenEconomy plugin) {
        this.plugin = plugin;
        this.configLoader = plugin.getConfigLoader();
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "TokenEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public String currencyNameSingular() {
        return configLoader.getCurrencyNameSingular();
    }

    @Override
    public String currencyNamePlural() {
        return configLoader.getCurrencyNamePlural();
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return CurrencyFormatter.format(amount, configLoader.getCurrencyNameSingular(), configLoader.getCurrencyNamePlural());
    }

    // ─── Account existence ───────────────────────────────────────────────────

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return plugin.getDataConnector().playerExistsByUUID(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player); // global economy, world ignored
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName); // global economy, world ignored
    }

    // ─── Account creation ────────────────────────────────────────────────────

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (hasAccount(player)) {
            return true; // already exists
        }
        plugin.getDataConnector().setPlayerBalance(player.getUniqueId(), 0.0);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player); // global economy, world ignored
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName); // global economy, world ignored
    }

    // ─── Balance queries ─────────────────────────────────────────────────────

    @Override
    public double getBalance(OfflinePlayer player) {
        return plugin.getDataConnector().getPlayerBalanceByUUID(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player); // global economy, world ignored
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName); // global economy, world ignored
    }

    // ─── Has-enough checks ───────────────────────────────────────────────────

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount); // global economy, world ignored
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount); // global economy, world ignored
    }

    // ─── Withdrawals ─────────────────────────────────────────────────────────

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        EconomyResponse invalid = validateAmount(player, amount);
        if (invalid != null) return invalid;
        boolean success = plugin.getDataConnector().changePlayerBalance(player.getUniqueId(), -amount);
        double balance = getBalance(player);
        return success
            ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
            : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient balance");
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    // ─── Deposits ────────────────────────────────────────────────────────────

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        EconomyResponse invalid = validateAmount(player, amount);
        if (invalid != null) return invalid;
        plugin.getDataConnector().changePlayerBalance(player.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    // ─── Bank stubs (hasBankSupport() = false) ───────────────────────────────

    @Override public EconomyResponse bankBalance(String name) { return notImplemented(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse createBank(String name, String player) { return notImplemented(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse deleteBank(String name) { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, String player) { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, String player) { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notImplemented(); }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    private EconomyResponse validateAmount(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be positive");
        }
        return null;
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not implemented");
    }
}
