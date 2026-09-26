package com.yagiztufek.matchmaking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Game rules and background-job timing, bound from the {@code matchmaking.*} properties.
 */
@ConfigurationProperties("matchmaking")
public record MatchmakingProperties(
        int minLevel,
        long entryFee,
        long startingCoins,
        long firstPlaceReward,
        long secondPlaceReward,
        Reconcile reconcile) {

    /**
     * @param interval   how often the reconciliation job runs
     * @param staleAfter how long an entry or match may sit unprocessed before the job repairs it
     */
    public record Reconcile(Duration interval, Duration staleAfter) {
    }
}
