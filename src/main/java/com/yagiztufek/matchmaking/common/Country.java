package com.yagiztufek.matchmaking.common;

/**
 * Every match contains exactly one player from each country, in this order.
 */
public enum Country {
    TR("Turkey"),
    US("United States"),
    UK("United Kingdom"),
    FR("France"),
    DE("Germany");

    private final String displayName;

    Country(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
