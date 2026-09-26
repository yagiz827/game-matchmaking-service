package com.yagiztufek.matchmaking.match;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.match.MatchService.GroupOutcome;
import com.yagiztufek.matchmaking.player.Player;
import com.yagiztufek.matchmaking.player.PlayerRepository;
import com.yagiztufek.matchmaking.tournament.TournamentEntry;
import com.yagiztufek.matchmaking.tournament.TournamentEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.yagiztufek.matchmaking.TestData.PROPERTIES;
import static com.yagiztufek.matchmaking.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long MATCH_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final MatchRepository matches = mock(MatchRepository.class);
    private final MatchParticipantRepository participants = mock(MatchParticipantRepository.class);
    private final TournamentEntryRepository entries = mock(TournamentEntryRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);

    private final MatchService service = new MatchService(matches, participants, entries, players,
            PROPERTIES, new Random(42), Clock.fixed(NOW, ZoneOffset.UTC));

    private final Map<Long, Player> playersById = new LinkedHashMap<>();
    private final Map<Long, TournamentEntry> entriesByPlayer = new LinkedHashMap<>();
    private Match match;

    @BeforeEach
    void setUp() {
        match = withId(new Match(TOURNAMENT_ID, NOW), MATCH_ID);
        List<MatchParticipant> roster = new ArrayList<>();
        long playerId = 1;
        for (Country country : Country.values()) {
            playersById.put(playerId, withId(new Player("player" + playerId, country, 4_000, NOW), playerId));
            TournamentEntry entry = withId(new TournamentEntry(TOURNAMENT_ID, playerId, country, NOW), playerId);
            entriesByPlayer.put(playerId, entry);
            roster.add(withId(new MatchParticipant(MATCH_ID, playerId, country), playerId));
            playerId++;
        }

        when(matches.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(match));
        when(participants.findByMatchIdOrderByPlayerId(MATCH_ID)).thenReturn(roster);
        when(players.findByIdForUpdate(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(playersById.get(inv.<Long>getArgument(0))));
        when(entries.findForUpdate(eq(TOURNAMENT_ID), anyCollection()))
                .thenAnswer(inv -> new ArrayList<>(entriesByPlayer.values()));
    }

    @Test
    void playingAMatchRanksEveryoneAndPaysTheTopTwo() {
        Events.MatchCompleted result = service.play(MATCH_ID);

        assertThat(result.standings()).extracting(Events.Standing::placement).containsExactly(1, 2, 3, 4, 5);
        assertThat(result.standings()).extracting(Events.Standing::points).containsExactly(4, 3, 2, 1, 0);
        assertThat(result.standings()).extracting(Events.Standing::coinsAwarded)
                .containsExactly(10_000L, 5_000L, 0L, 0L, 0L);
        assertThat(match.isCompleted()).isTrue();

        Events.Standing winner = result.standings().getFirst();
        Events.Standing runnerUp = result.standings().get(1);
        assertThat(playersById.get(winner.playerId()).getCoins()).isEqualTo(14_000);
        assertThat(playersById.get(runnerUp.playerId()).getCoins()).isEqualTo(9_000);
        assertThat(entriesByPlayer.get(winner.playerId()).getStatus()).isEqualTo(TournamentEntry.Status.FINISHED);
        assertThat(entriesByPlayer.get(winner.playerId()).getScore()).isEqualTo(4);
    }

    @Test
    void replayingAnAlreadyPlayedMatchChangesNothing() {
        Events.MatchCompleted first = service.play(MATCH_ID);
        long winnerCoins = playersById.get(first.standings().getFirst().playerId()).getCoins();

        Events.MatchCompleted second = service.play(MATCH_ID);

        assertThat(second).isEqualTo(first);
        assertThat(playersById.get(first.standings().getFirst().playerId()).getCoins()).isEqualTo(winnerCoins);
        // Players are only locked and paid on the first run.
        verify(players, times(Country.values().length)).findByIdForUpdate(anyLong());
    }

    @Test
    void creatingAMatchMarksEveryEntryAsMatched() {
        when(matches.save(any(Match.class))).thenAnswer(inv -> withId(inv.<Match>getArgument(0), 99L));

        GroupOutcome outcome = service.createMatch(TOURNAMENT_ID, group());

        assertThat(outcome).isEqualTo(new GroupOutcome.Created(99L, List.of(1L, 2L, 3L, 4L, 5L)));
        assertThat(entriesByPlayer.values()).allSatisfy(entry -> {
            assertThat(entry.getStatus()).isEqualTo(TournamentEntry.Status.MATCHED);
            assertThat(entry.getMatchId()).isEqualTo(99L);
        });
        verify(participants, times(5)).save(any(MatchParticipant.class));
    }

    @Test
    void aGroupWithAPlayerWhoIsNoLongerQueuedIsRejected() {
        entriesByPlayer.get(3L).matched(55L);

        GroupOutcome outcome = service.createMatch(TOURNAMENT_ID, group());

        assertThat(outcome).isInstanceOf(GroupOutcome.Stale.class);
        assertThat(((GroupOutcome.Stale) outcome).stillQueued()).containsOnlyKeys(
                Country.TR, Country.US, Country.FR, Country.DE);
        verify(matches, never()).save(any());
    }

    private Map<Country, Long> group() {
        Map<Country, Long> group = new LinkedHashMap<>();
        entriesByPlayer.values().stream()
                .sorted(Comparator.comparing(TournamentEntry::getPlayerId))
                .forEach(e -> group.put(e.getCountry(), e.getPlayerId()));
        return group;
    }
}
