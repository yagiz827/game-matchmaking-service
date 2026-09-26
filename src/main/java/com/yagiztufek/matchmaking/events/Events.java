package com.yagiztufek.matchmaking.events;

import java.time.Instant;
import java.util.List;

import com.yagiztufek.matchmaking.common.Country;

/**
 * Kafka topics and their JSON payloads. Every event is keyed by tournament id, so all
 * events of one tournament go to the same partition and are consumed in order.
 */
public final class Events {

    public static final String PLAYER_JOINED = "matchmaking.player-joined";
    public static final String MATCH_CREATED = "matchmaking.match-created";
    public static final String MATCH_COMPLETED = "matchmaking.match-completed";

    private Events() {
    }

    public record PlayerJoined(long tournamentId, long playerId, Country country, Instant joinedAt) {
    }

    public record MatchCreated(long matchId, long tournamentId, List<Long> playerIds) {
    }

    public record MatchCompleted(long matchId, long tournamentId, List<Standing> standings) {
    }

    public record Standing(long playerId, Country country, int placement, int points, long coinsAwarded) {
    }
}
