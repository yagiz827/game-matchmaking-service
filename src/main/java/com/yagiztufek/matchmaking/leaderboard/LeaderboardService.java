package com.yagiztufek.matchmaking.leaderboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.player.Player;
import com.yagiztufek.matchmaking.player.PlayerRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Tournament leaderboards as Redis sorted sets: one overall and one per country.
 *
 * <p>Scores are written with ZADD (set to the absolute value from PostgreSQL) rather than
 * ZINCRBY, so replaying the same event after a retry can't double-count points.
 */
@Service
public class LeaderboardService {

    private final StringRedisTemplate redis;
    private final PlayerRepository players;

    public LeaderboardService(StringRedisTemplate redis, PlayerRepository players) {
        this.redis = redis;
        this.players = players;
    }

    public void recordScore(long tournamentId, Country country, long playerId, int score) {
        String member = Long.toString(playerId);
        redis.opsForZSet().add(overallKey(tournamentId), member, score);
        redis.opsForZSet().add(countryKey(tournamentId, country), member, score);
    }

    /** @param country null for the overall leaderboard */
    public List<Row> top(long tournamentId, Country country, int limit) {
        String key = country == null ? overallKey(tournamentId) : countryKey(tournamentId, country);
        Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tuples.stream().map(t -> Long.parseLong(t.getValue())).toList();
        Map<Long, Player> byId = players.findAllById(ids).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        List<Row> rows = new ArrayList<>();
        int rank = 1;
        for (TypedTuple<String> tuple : tuples) {
            Player p = byId.get(Long.parseLong(tuple.getValue()));
            if (p != null) {
                rows.add(new Row(rank++, p.getId(), p.getUsername(), p.getCountry(),
                        tuple.getScore() == null ? 0 : tuple.getScore().intValue()));
            }
        }
        return rows;
    }

    static String overallKey(long tournamentId) {
        return "lb:{t" + tournamentId + "}:all";
    }

    static String countryKey(long tournamentId, Country country) {
        return "lb:{t" + tournamentId + "}:country:" + country.name();
    }

    public record Row(int rank, long playerId, String username, Country country, int score) {
    }
}
