package com.yagiztufek.matchmaking.matchmaking;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.yagiztufek.matchmaking.config.MatchmakingProperties;
import com.yagiztufek.matchmaking.events.EventPublisher;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.match.Match;
import com.yagiztufek.matchmaking.match.MatchParticipant;
import com.yagiztufek.matchmaking.match.MatchParticipantRepository;
import com.yagiztufek.matchmaking.match.MatchRepository;
import com.yagiztufek.matchmaking.queue.MatchmakingQueue;
import com.yagiztufek.matchmaking.tournament.TournamentEntry;
import com.yagiztufek.matchmaking.tournament.TournamentEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Safety net for the two places where a database write and a Kafka publish can't be
 * atomic. If the process dies between them, this job finds the leftover state in
 * PostgreSQL and finishes the work:
 * <ul>
 *   <li>entries still QUEUED but never pushed to Redis are enqueued again;</li>
 *   <li>matches still CREATED because the event was lost are republished.</li>
 * </ul>
 * Both repairs are idempotent, so running the job on several instances is harmless.
 */
@Component
public class ReconciliationJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);
    private static final int BATCH_SIZE = 500;

    private final TournamentEntryRepository entries;
    private final MatchRepository matches;
    private final MatchParticipantRepository participants;
    private final MatchmakingQueue queue;
    private final Matchmaker matchmaker;
    private final EventPublisher events;
    private final MatchmakingProperties properties;
    private final Clock clock;

    public ReconciliationJob(TournamentEntryRepository entries, MatchRepository matches,
                             MatchParticipantRepository participants, MatchmakingQueue queue,
                             Matchmaker matchmaker, EventPublisher events,
                             MatchmakingProperties properties, Clock clock) {
        this.entries = entries;
        this.matches = matches;
        this.participants = participants;
        this.queue = queue;
        this.matchmaker = matchmaker;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::run, properties.reconcile().interval());
    }

    void run() {
        Instant cutoff = clock.instant().minus(properties.reconcile().staleAfter());
        try {
            requeueWaitingEntries(cutoff);
            republishUnplayedMatches(cutoff);
        } catch (RuntimeException e) {
            // Keep the schedule alive; the next run will try again.
            log.error("Reconciliation run failed", e);
        }
    }

    private void requeueWaitingEntries(Instant cutoff) {
        Set<Long> tournaments = new LinkedHashSet<>();
        for (TournamentEntry entry : entries.findByStatusAndJoinedAtBefore(
                TournamentEntry.Status.QUEUED, cutoff, Limit.of(BATCH_SIZE))) {
            if (queue.enqueue(entry.getTournamentId(), entry.getCountry(), entry.getPlayerId())) {
                log.warn("Re-queued entry {} (player {}) missing from Redis", entry.getId(), entry.getPlayerId());
            }
            tournaments.add(entry.getTournamentId());
        }
        tournaments.forEach(matchmaker::formMatches);
    }

    private void republishUnplayedMatches(Instant cutoff) {
        for (Match match : matches.findByStatusAndCreatedAtBefore(Match.Status.CREATED, cutoff, Limit.of(BATCH_SIZE))) {
            List<Long> playerIds = participants.findByMatchIdOrderByPlayerId(match.getId()).stream()
                    .map(MatchParticipant::getPlayerId).toList();
            events.publish(Events.MATCH_CREATED, match.getTournamentId(),
                    new Events.MatchCreated(match.getId(), match.getTournamentId(), playerIds));
            log.warn("Republished unplayed match {}", match.getId());
        }
    }
}
