package com.yagiztufek.matchmaking.events;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes events as JSON and waits for the broker's acknowledgement, so a caller
 * only continues once the event is durably stored (acks=all).
 */
@Component
public class EventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    public void publish(String topic, long tournamentId, Object event) {
        String payload = json.writeValueAsString(event);
        try {
            kafka.send(topic, Long.toString(tournamentId), payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException(topic, e);
        }
    }

    public static class EventPublishException extends RuntimeException {

        EventPublishException(String topic, Throwable cause) {
            super("Failed to publish to " + topic, cause);
        }
    }
}
