package com.yagiztufek.matchmaking;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.yagiztufek.matchmaking.common.Country;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test through the HTTP API with real PostgreSQL, Redis and Kafka containers.
 */
@SpringBootTest(properties = {
        "matchmaking.min-level=1",
        "matchmaking.reconcile.interval=2s",
        "matchmaking.reconcile.stale-after=5s"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MatchmakingFlowIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mvc;

    @Test
    void oneFromEachCountryFormsAMatchThatIsPlayedInTheBackground() throws Exception {
        long tournament = createTournament();
        Map<Country, Long> players = new EnumMap<>(Country.class);
        for (Country country : Country.values()) {
            long player = createPlayer(country);
            players.put(country, player);
            mvc.perform(post("/api/v1/tournaments/{id}/entries", tournament)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"playerId\": %d}".formatted(player)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("QUEUED"));
        }

        await().atMost(TIMEOUT).untilAsserted(() -> {
            for (long player : players.values()) {
                mvc.perform(get("/api/v1/tournaments/{id}/entries/{player}", tournament, player))
                        .andExpect(jsonPath("$.status").value("FINISHED"));
            }
        });

        String board = mvc.perform(get("/api/v1/tournaments/{id}/leaderboard", tournament))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> scores = JsonPath.read(board, "$[*].score");
        assertThat(scores).containsExactly(4, 3, 2, 1, 0);

        long winner = ((Number) JsonPath.read(board, "$[0].playerId")).longValue();
        mvc.perform(get("/api/v1/players/{id}", winner))
                .andExpect(jsonPath("$.coins").value(5_000 - 1_000 + 10_000));

        long matchId = ((Number) JsonPath.read(entryJson(tournament, winner), "$.matchId")).longValue();
        mvc.perform(get("/api/v1/matches/{id}", matchId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.standings.length()").value(5))
                .andExpect(jsonPath("$.standings[0].placement").value(1));
    }

    @Test
    void playersWaitUntilEveryCountryIsRepresented() throws Exception {
        long tournament = createTournament();
        Country[] allButGermany = {Country.TR, Country.US, Country.UK, Country.FR};
        for (Country country : allButGermany) {
            enter(tournament, createPlayer(country));
        }

        // The consumer queues them asynchronously; wait until all four are in Redis.
        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/v1/tournaments/{id}/queue", tournament))
                .andExpect(jsonPath("$.TR").value(1))
                .andExpect(jsonPath("$.FR").value(1))
                .andExpect(jsonPath("$.DE").value(0)));

        long german = createPlayer(Country.DE);
        enter(tournament, german);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat((String) JsonPath.read(entryJson(tournament, german), "$.status")).isEqualTo("FINISHED"));
    }

    @Test
    void enteringTwiceIsRejected() throws Exception {
        long tournament = createTournament();
        long player = createPlayer(Country.TR);
        enter(tournament, player);

        mvc.perform(post("/api/v1/tournaments/{id}/entries", tournament)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": %d}".formatted(player)))
                .andExpect(status().isConflict());
    }

    private long createTournament() throws Exception {
        String body = mvc.perform(post("/api/v1/tournaments"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createPlayer(Country country) throws Exception {
        String username = country.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"country\": \"%s\"}".formatted(username, country)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void enter(long tournament, long player) throws Exception {
        mvc.perform(post("/api/v1/tournaments/{id}/entries", tournament)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": %d}".formatted(player)))
                .andExpect(status().isAccepted());
    }

    private String entryJson(long tournament, long player) throws Exception {
        return mvc.perform(get("/api/v1/tournaments/{id}/entries/{player}", tournament, player))
                .andReturn().getResponse().getContentAsString();
    }
}
