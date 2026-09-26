package com.yagiztufek.matchmaking.config;

import com.yagiztufek.matchmaking.events.Events;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic playerJoinedTopic() {
        return TopicBuilder.name(Events.PLAYER_JOINED).partitions(PARTITIONS).build();
    }

    @Bean
    NewTopic matchCreatedTopic() {
        return TopicBuilder.name(Events.MATCH_CREATED).partitions(PARTITIONS).build();
    }

    @Bean
    NewTopic matchCompletedTopic() {
        return TopicBuilder.name(Events.MATCH_COMPLETED).partitions(PARTITIONS).build();
    }

    /**
     * Failed messages are retried with exponential backoff (e.g. while Redis restarts);
     * after that they go to a dead-letter topic instead of blocking the partition.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(15_000L);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), backOff);
    }
}
