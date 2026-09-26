package com.yagiztufek.matchmaking.tournament;

import java.time.Clock;
import java.util.Map;

import com.yagiztufek.matchmaking.common.ConflictException;
import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.common.NotEligibleException;
import com.yagiztufek.matchmaking.common.NotFoundException;
import com.yagiztufek.matchmaking.config.MatchmakingProperties;
import com.yagiztufek.matchmaking.events.EventPublisher;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.player.Player;
import com.yagiztufek.matchmaking.player.PlayerRepository;
import com.yagiztufek.matchmaking.queue.MatchmakingQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TournamentService {

    private final TournamentRepository tournaments;
    private final TournamentEntryRepository entries;
    private final PlayerRepository players;
    private final MatchmakingQueue queue;
    private final EventPublisher events;
    private final MatchmakingProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public TournamentService(TournamentRepository tournaments, TournamentEntryRepository entries,
                             PlayerRepository players, MatchmakingQueue queue, EventPublisher events,
                             MatchmakingProperties properties, TransactionTemplate transaction, Clock clock) {
        this.tournaments = tournaments;
        this.entries = entries;
        this.players = players;
        this.queue = queue;
        this.events = events;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Transactional
    public Tournament create() {
        return tournaments.save(new Tournament(clock.instant()));
    }

    @Transactional(readOnly = true)
    public Tournament get(long id) {
        return tournaments.findById(id)
                .orElseThrow(() -> new NotFoundException("Tournament %d not found".formatted(id)));
    }

    @Transactional
    public Tournament close(long id) {
        Tournament tournament = get(id);
        tournament.close();
        return tournament;
    }

    /**
     * Charges the entry fee and records the entry, then publishes a PlayerJoined event.
     * Queueing and matchmaking happen asynchronously in the Kafka consumer, so this
     * returns as soon as the entry is saved.
     */
    public TournamentEntry enter(long tournamentId, long playerId) {
        TournamentEntry entry = transaction.execute(status -> {
            if (!get(tournamentId).isOpen()) {
                throw new ConflictException("Tournament %d is closed".formatted(tournamentId));
            }
            // Locking the player first serializes concurrent requests for the same player,
            // so the duplicate check below can't be raced.
            Player player = players.findByIdForUpdate(playerId)
                    .orElseThrow(() -> new NotFoundException("Player %d not found".formatted(playerId)));
            if (entries.existsByTournamentIdAndPlayerId(tournamentId, playerId)) {
                throw new ConflictException("Player %d already entered tournament %d".formatted(playerId, tournamentId));
            }
            if (player.getLevel() < properties.minLevel()) {
                throw new NotEligibleException("Player %d is level %d; level %d is required"
                        .formatted(playerId, player.getLevel(), properties.minLevel()));
            }
            player.pay(properties.entryFee());
            return entries.save(new TournamentEntry(tournamentId, playerId, player.getCountry(), clock.instant()));
        });

        // After commit: if this publish is lost, ReconciliationJob queues the entry later.
        events.publish(Events.PLAYER_JOINED, tournamentId,
                new Events.PlayerJoined(tournamentId, playerId, entry.getCountry(), entry.getJoinedAt()));
        return entry;
    }

    @Transactional(readOnly = true)
    public TournamentEntry getEntry(long tournamentId, long playerId) {
        return entries.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new NotFoundException(
                        "Player %d has not entered tournament %d".formatted(playerId, tournamentId)));
    }

    public Map<Country, Long> queueSizes(long tournamentId) {
        get(tournamentId);
        return queue.queueSizes(tournamentId);
    }
}
