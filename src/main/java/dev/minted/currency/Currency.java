package dev.minted.currency;

import java.util.Locale;

/**
 * Represents a currency in the multi-currency system.
 * Each currency has its own name, symbol, and exchange rate to the base currency.
 */
public final class Currency {

    private final String id;
    private final String name;
    private final String singular;
    private final String symbol;
    private final double exchangeRate; // Rate to base currency (1 base = X this currency)
    private final boolean isBase;
    private final int sortOrder;

    public Currency(String id, String name, String singular, String symbol,
                    double exchangeRate, boolean isBase, int sortOrder) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.name = name;
        this.singular = singular;
        this.symbol = symbol;
        this.exchangeRate = exchangeRate;
        this.isBase = isBase;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSingular() {
        return singular;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getExchangeRate() {
        return exchangeRate;
    }

    public boolean isBase() {
        return isBase;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * Converts an amount from this currency to the base currency.
     */
    public double toBase(double amount) {
        if (isBase) return amount;
        return amount / exchangeRate;
    }

    /**
     * Converts an amount from base currency to this currency.
     */
    public double fromBase(double baseAmount) {
        if (isBase) return baseAmount;
        return baseAmount * exchangeRate;
    }

    /**
     * Converts an amount from this currency to another currency.
     */
    public double convertTo(double amount, Currency target) {
        double baseAmount = toBase(amount);
        return target.fromBase(baseAmount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Currency currency = (Currency) o;
        return id.equals(currency.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}