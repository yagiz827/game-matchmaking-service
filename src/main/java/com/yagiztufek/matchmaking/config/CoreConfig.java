package com.yagiztufek.matchmaking.config;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time and randomness are beans so tests can make them deterministic.
 */
@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator randomGenerator() {
        // java.util.Random is thread-safe, unlike most RandomGenerator implementations
        return new Random();
    }
}
