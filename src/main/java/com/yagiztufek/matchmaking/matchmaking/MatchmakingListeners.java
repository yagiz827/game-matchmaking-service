package com.yagiztufek.matchmaking.matchmaking;

import com.yagiztufek.matchmaking.events.EventPublisher;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.leaderboard.LeaderboardService;
import com.yagiztufek.matchmaking.match.MatchService;
import com.yagiztufek.matchmaking.queue.MatchmakingQueue;
import com.yagiztufek.matchmaking.tournament.TournamentEntry;
import com.yagiztufek.matchmaking.tournament.TournamentEntryRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka consumers. Delivery is at-least-once, so every handler is idempotent:
 * processing the same event twice has the same effect as processing it once.
 */
@Component
public class MatchmakingListeners {

    private final TournamentEntryRepository entries;
    private final MatchmakingQueue queue;
    private final Matchmaker matchmaker;
    private final MatchService matchService;
    private final LeaderboardService leaderboard;
    private final EventPublisher events;
    private final ObjectMapper json;

    public MatchmakingListeners(TournamentEntryRepository entries, MatchmakingQueue queue, Matchmaker matchmaker,
                                MatchService matchService, LeaderboardService leaderboard,
                                EventPublisher events, ObjectMapper json) {
        this.entries = entries;
        this.queue = queue;
        this.matchmaker = matchmaker;
        this.matchService = matchService;
        this.leaderboard = leaderboard;
        this.events = events;
        this.json = json;
    }

    @KafkaListener(topics = Events.PLAYER_JOINED)
    public void onPlayerJoined(String payload) {
        Events.PlayerJoined event = json.readValue(payload, Events.PlayerJoined.class);

        // Only queue players the database still considers waiting; a redelivered event
        // for an already matched player must not put them back in line.
        entries.findByTournamentIdAndPlayerId(event.tournamentId(), event.playerId())
                .filter(TournamentEntry::isQueued)
                .ifPresent(entry -> queue.enqueue(event.tournamentId(), event.country(), event.playerId()));

        matchmaker.formMatches(event.tournamentId());
    }

    @KafkaListener(topics = Events.MATCH_CREATED)
    public void onMatchCreated(String payload) {
        Events.MatchCreated event = json.readValue(payload, Events.MatchCreated.class);

        Events.MatchCompleted result = matchService.play(event.matchId());
        for (Events.Standing standing : result.standings()) {
            // One entry per player per tournament, so the match points are the tournament score.
            leaderboard.recordScore(result.tournamentId(), standing.country(), standing.playerId(), standing.points());
        }
        events.publish(Events.MATCH_COMPLETED, result.tournamentId(), result);
    }
}
