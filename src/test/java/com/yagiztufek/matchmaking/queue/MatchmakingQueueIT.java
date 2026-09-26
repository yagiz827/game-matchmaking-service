package com.yagiztufek.matchmaking.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.yagiztufek.matchmaking.common.Country;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Lua scripts against a real Redis. The concurrency test is the key one:
 * many matchmakers popping at once must never hand the same player to two matches.
 */
@Testcontainers(disabledWithoutDocker = true)
class MatchmakingQueueIT {

    private static final long TOURNAMENT = 1L;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private MatchmakingQueue queue;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        queue = new MatchmakingQueue(redis);
    }

    @Test
    void noMatchUntilEveryCountryHasSomeoneWaiting() {
        queue.enqueue(TOURNAMENT, Country.TR, 1);
        queue.enqueue(TOURNAMENT, Country.US, 2);
        queue.enqueue(TOURNAMENT, Country.UK, 3);
        queue.enqueue(TOURNAMENT, Country.FR, 4);

        assertThat(queue.popMatch(TOURNAMENT)).isEmpty();
        assertThat(queue.queueSizes(TOURNAMENT).values()).containsExactly(1L, 1L, 1L, 1L, 0L);

        queue.enqueue(TOURNAMENT, Country.DE, 5);

        assertThat(queue.popMatch(TOURNAMENT)).contains(Map.of(
                Country.TR, 1L, Country.US, 2L, Country.UK, 3L, Country.FR, 4L, Country.DE, 5L));
        assertThat(queue.queueSizes(TOURNAMENT).values()).containsOnly(0L);
    }

    @Test
    void aPlayerCanOnlyBeQueuedOnce() {
        assertThat(queue.enqueue(TOURNAMENT, Country.TR, 1)).isTrue();
        assertThat(queue.enqueue(TOURNAMENT, Country.TR, 1)).isFalse();

        assertThat(queue.queueSizes(TOURNAMENT).get(Country.TR)).isEqualTo(1L);
    }

    @Test
    void longestWaitingPlayersAreMatchedFirst() {
        fillQueues(3);

        Map<Country, Long> first = queue.popMatch(TOURNAMENT).orElseThrow();

        assertThat(first).containsEntry(Country.TR, playerId(Country.TR, 0))
                .containsEntry(Country.DE, playerId(Country.DE, 0));
    }

    @Test
    void requeuedPlayersKeepTheirPlaceInLine() {
        fillQueues(2);
        Map<Country, Long> group = queue.popMatch(TOURNAMENT).orElseThrow();

        queue.requeue(TOURNAMENT, group);

        assertThat(queue.popMatch(TOURNAMENT)).contains(group);
    }

    @Test
    void queuesAreIsolatedPerTournament() {
        fillQueues(1);

        assertThat(queue.popMatch(2L)).isEmpty();
        assertThat(queue.popMatch(TOURNAMENT)).isPresent();
    }

    @Test
    void concurrentMatchmakersNeverShareAPlayer() throws Exception {
        int playersPerCountry = 200;
        int workers = 16;
        fillQueues(playersPerCountry);

        Collection<Map<Country, Long>> matches = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    Optional<Map<Country, Long>> match;
                    while ((match = queue.popMatch(TOURNAMENT)).isPresent()) {
                        matches.add(match.get());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(matches).hasSize(playersPerCountry);
        List<Long> everyone = matches.stream().flatMap(m -> m.values().stream()).toList();
        assertThat(everyone).hasSize(playersPerCountry * Country.values().length).doesNotHaveDuplicates();
        assertThat(matches).allSatisfy(m -> m.forEach(
                (country, playerId) -> assertThat(playerId / 10_000).isEqualTo(country.ordinal())));
        assertThat(queue.queueSizes(TOURNAMENT).values()).containsOnly(0L);
    }

    private void fillQueues(int perCountry) {
        for (int i = 0; i < perCountry; i++) {
            for (Country country : Country.values()) {
                queue.enqueue(TOURNAMENT, country, playerId(country, i));
            }
        }
    }

    /** Encodes the country in the id so tests can check which queue a player came from. */
    private static long playerId(Country country, int index) {
        return country.ordinal() * 10_000L + index;
    }
}
