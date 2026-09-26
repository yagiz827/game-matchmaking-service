package com.yagiztufek.matchmaking.queue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.yagiztufek.matchmaking.common.Country;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Per-tournament waiting queues in Redis: one list per country plus a set of
 * queued player ids for de-duplication.
 *
 * <p>Every multi-step operation is a Lua script, which Redis executes atomically,
 * so any number of service instances can share the queues without locks.
 *
 * <p>Keys use a {@code {t<id>}} hash tag so all keys of one tournament land on the
 * same Redis Cluster slot, which multi-key scripts require.
 */
@Component
public class MatchmakingQueue {

    private static final RedisScript<Long> ENQUEUE =
            RedisScript.of(new ClassPathResource("scripts/enqueue.lua"), Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> POP_MATCH =
            RedisScript.of(new ClassPathResource("scripts/pop_match.lua"), List.class);
    private static final RedisScript<Long> REQUEUE =
            RedisScript.of(new ClassPathResource("scripts/requeue.lua"), Long.class);

    private final StringRedisTemplate redis;

    public MatchmakingQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return true if queued, false if the player was already waiting */
    public boolean enqueue(long tournamentId, Country country, long playerId) {
        Long added = redis.execute(ENQUEUE,
                List.of(queueKey(tournamentId, country), queuedSetKey(tournamentId)),
                Long.toString(playerId));
        return added != null && added == 1L;
    }

    /**
     * Atomically removes one player per country, or nothing if any country's queue is empty.
     *
     * @return player ids keyed by country, in {@link Country} order
     */
    public Optional<Map<Country, Long>> popMatch(long tournamentId) {
        List<String> keys = new ArrayList<>();
        for (Country country : Country.values()) {
            keys.add(queueKey(tournamentId, country));
        }
        keys.add(queuedSetKey(tournamentId));

        List<?> popped = redis.execute(POP_MATCH, keys);
        if (popped == null || popped.isEmpty()) {
            return Optional.empty();
        }
        Map<Country, Long> players = new LinkedHashMap<>();
        Country[] countries = Country.values();
        for (int i = 0; i < countries.length; i++) {
            players.put(countries[i], Long.parseLong(popped.get(i).toString()));
        }
        return Optional.of(players);
    }

    /** Puts players back at the front of their queues so they keep their place in line. */
    public void requeue(long tournamentId, Map<Country, Long> players) {
        if (players.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        players.forEach((country, playerId) -> {
            keys.add(queueKey(tournamentId, country));
            args.add(Long.toString(playerId));
        });
        keys.add(queuedSetKey(tournamentId));
        redis.execute(REQUEUE, keys, args.toArray());
    }

    public Map<Country, Long> queueSizes(long tournamentId) {
        Map<Country, Long> sizes = new EnumMap<>(Country.class);
        for (Country country : Country.values()) {
            Long size = redis.opsForList().size(queueKey(tournamentId, country));
            sizes.put(country, size == null ? 0L : size);
        }
        return sizes;
    }

    static String queueKey(long tournamentId, Country country) {
        return "mm:{t" + tournamentId + "}:queue:" + country.name();
    }

    static String queuedSetKey(long tournamentId) {
        return "mm:{t" + tournamentId + "}:queued";
    }
}
