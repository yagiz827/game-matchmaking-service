package com.yagiztufek.matchmaking.match;

import java.time.Instant;
import java.util.List;

import com.yagiztufek.matchmaking.common.Country;

public record MatchView(long id, long tournamentId, Match.Status status, Instant createdAt,
                        Instant completedAt, List<Row> standings) {

    /** Placement, points and coins are null until the match has been played. */
    public record Row(long playerId, String username, Country country,
                      Integer placement, Integer points, Long coinsAwarded) {
    }
}
