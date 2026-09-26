package com.yagiztufek.matchmaking.match;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.common.NotFoundException;
import com.yagiztufek.matchmaking.config.MatchmakingProperties;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.player.Player;
import com.yagiztufek.matchmaking.player.PlayerRepository;
import com.yagiztufek.matchmaking.tournament.TournamentEntry;
import com.yagiztufek.matchmaking.tournament.TournamentEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    private final MatchRepository matches;
    private final MatchParticipantRepository participants;
    private final TournamentEntryRepository entries;
    private final PlayerRepository players;
    private final MatchmakingProperties properties;
    private final RandomGenerator random;
    private final Clock clock;

    public MatchService(MatchRepository matches, MatchParticipantRepository participants,
                        TournamentEntryRepository entries, PlayerRepository players,
                        MatchmakingProperties properties, RandomGenerator random, Clock clock) {
        this.matches = matches;
        this.participants = participants;
        this.entries = entries;
        this.players = players;
        this.properties = properties;
        this.random = random;
        this.clock = clock;
    }

    /** Result of trying to turn a group popped from the queue into a match. */
    public sealed interface GroupOutcome {

        record Created(long matchId, List<Long> playerIds) implements GroupOutcome {
        }

        /** Some players were no longer QUEUED in the database; the rest should go back in line. */
        record Stale(Map<Country, Long> stillQueued) implements GroupOutcome {
        }
    }

    /**
     * Saves a match for a group taken from the Redis queue. The database is the source
     * of truth: if any player's entry is no longer QUEUED, nothing is written.
     */
    @Transactional
    public GroupOutcome createMatch(long tournamentId, Map<Country, Long> group) {
        Map<Long, TournamentEntry> locked = entries.findForUpdate(tournamentId, group.values()).stream()
                .collect(Collectors.toMap(TournamentEntry::getPlayerId, Function.identity()));

        Map<Country, Long> stillQueued = new LinkedHashMap<>();
        group.forEach((country, playerId) -> {
            TournamentEntry entry = locked.get(playerId);
            if (entry != null && entry.isQueued()) {
                stillQueued.put(country, playerId);
            }
        });
        if (stillQueued.size() != group.size()) {
            return new GroupOutcome.Stale(stillQueued);
        }

        Match match = matches.save(new Match(tournamentId, clock.instant()));
        group.forEach((country, playerId) -> {
            participants.save(new MatchParticipant(match.getId(), playerId, country));
            locked.get(playerId).matched(match.getId());
        });
        return new GroupOutcome.Created(match.getId(), List.copyOf(group.values()));
    }

    /**
     * Plays a match and pays out rewards. Idempotent: if the match was already settled
     * (e.g. Kafka redelivered the event) nothing changes and the stored result is returned.
     *
     * <p>Scoring follows the original game: players are knocked out one at a time in
     * random order and every survivor earns a point per round, so with five players
     * the winner gets 4 points and the first player out gets 0.
     */
    @Transactional
    public Events.MatchCompleted play(long matchId) {
        Match match = matches.findByIdForUpdate(matchId)
                .orElseThrow(() -> new NotFoundException("Match %d not found".formatted(matchId)));
        List<MatchParticipant> roster = participants.findByMatchIdOrderByPlayerId(matchId);

        if (!match.isCompleted()) {
            List<Long> playerIds = roster.stream().map(MatchParticipant::getPlayerId).toList();
            // Lock rows in ascending id order (roster is sorted) so concurrent matches can't deadlock.
            Map<Long, Player> lockedPlayers = new LinkedHashMap<>();
            for (Long id : playerIds) {
                lockedPlayers.put(id, players.findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalStateException("Player %d missing".formatted(id))));
            }
            Map<Long, TournamentEntry> lockedEntries = entries.findForUpdate(match.getTournamentId(), playerIds)
                    .stream().collect(Collectors.toMap(TournamentEntry::getPlayerId, Function.identity()));

            List<MatchParticipant> finishingOrder = new ArrayList<>(roster);
            Collections.shuffle(finishingOrder, random);
            int size = finishingOrder.size();
            for (int i = 0; i < size; i++) {
                MatchParticipant participant = finishingOrder.get(i);
                int placement = i + 1;
                int points = size - placement;
                long coins = rewardFor(placement);

                participant.recordResult(placement, points, coins);
                lockedPlayers.get(participant.getPlayerId()).reward(coins);
                lockedEntries.get(participant.getPlayerId()).finished(points);
            }
            match.complete(clock.instant());
        }

        List<Events.Standing> standings = roster.stream()
                .sorted(Comparator.comparing(MatchParticipant::getPlacement))
                .map(p -> new Events.Standing(p.getPlayerId(), p.getCountry(), p.getPlacement(),
                        p.getPoints(), p.getCoinsAwarded()))
                .toList();
        return new Events.MatchCompleted(matchId, match.getTournamentId(), standings);
    }

    @Transactional(readOnly = true)
    public MatchView get(long matchId) {
        Match match = matches.findById(matchId)
                .orElseThrow(() -> new NotFoundException("Match %d not found".formatted(matchId)));
        List<MatchParticipant> roster = participants.findByMatchIdOrderByPlayerId(matchId);
        Map<Long, String> usernames = players.findAllById(
                        roster.stream().map(MatchParticipant::getPlayerId).toList()).stream()
                .collect(Collectors.toMap(Player::getId, Player::getUsername));

        List<MatchView.Row> rows = roster.stream()
                .sorted(Comparator.comparing(MatchParticipant::getPlacement,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new MatchView.Row(p.getPlayerId(), usernames.get(p.getPlayerId()), p.getCountry(),
                        p.getPlacement(), p.getPoints(), p.getCoinsAwarded()))
                .toList();
        return new MatchView(match.getId(), match.getTournamentId(), match.getStatus(),
                match.getCreatedAt(), match.getCompletedAt(), rows);
    }

    private long rewardFor(int placement) {
        return switch (placement) {
            case 1 -> properties.firstPlaceReward();
            case 2 -> properties.secondPlaceReward();
            default -> 0L;
        };
    }
}
