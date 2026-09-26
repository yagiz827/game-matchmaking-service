package com.yagiztufek.matchmaking;

import java.time.Duration;

import com.yagiztufek.matchmaking.config.MatchmakingProperties;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestData {

    public static final MatchmakingProperties PROPERTIES = new MatchmakingProperties(
            20, 1_000, 5_000, 10_000, 5_000,
            new MatchmakingProperties.Reconcile(Duration.ofSeconds(30), Duration.ofSeconds(60)));

    private TestData() {
    }

    /** Sets the database-generated id on an entity built in a unit test. */
    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
