package com.yagiztufek.matchmaking.matchmaking;

import java.util.Map;
import java.util.Optional;

import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.events.EventPublisher;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.match.MatchService;
import com.yagiztufek.matchmaking.match.MatchService.GroupOutcome;
import com.yagiztufek.matchmaking.queue.MatchmakingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Forms as many matches as the queues allow. Safe to run concurrently on any number
 * of instances: Redis hands each queued player to exactly one caller.
 */
@Component
public class Matchmaker {

    private static final Logger log = LoggerFactory.getLogger(Matchmaker.class);

    private final MatchmakingQueue queue;
    private final MatchService matchService;
    private final EventPublisher events;

    public Matchmaker(MatchmakingQueue queue, MatchService matchService, EventPublisher events) {
        this.queue = queue;
        this.matchService = matchService;
        this.events = events;
    }

    /** @return the number of matches created */
    public int formMatches(long tournamentId) {
        int created = 0;
        Optional<Map<Country, Long>> group;
        while ((group = queue.popMatch(tournamentId)).isPresent()) {
            GroupOutcome outcome;
            try {
                outcome = matchService.createMatch(tournamentId, group.get());
            } catch (RuntimeException e) {
                // Nothing was saved, so give the players their place in line back.
                queue.requeue(tournamentId, group.get());
                throw e;
            }

            switch (outcome) {
                case GroupOutcome.Created c -> {
                    events.publish(Events.MATCH_CREATED, tournamentId,
                            new Events.MatchCreated(c.matchId(), tournamentId, c.playerIds()));
                    log.info("Tournament {}: created match {} with players {}", tournamentId, c.matchId(), c.playerIds());
                    created++;
                }
                case GroupOutcome.Stale s -> {
                    log.warn("Tournament {}: dropped stale queue entries from group {}", tournamentId, group.get());
                    queue.requeue(tournamentId, s.stillQueued());
                }
            }
        }
        return created;
    }
}
