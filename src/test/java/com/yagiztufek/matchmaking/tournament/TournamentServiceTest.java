package com.yagiztufek.matchmaking.tournament;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.yagiztufek.matchmaking.common.ConflictException;
import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.common.NotEligibleException;
import com.yagiztufek.matchmaking.events.EventPublisher;
import com.yagiztufek.matchmaking.events.Events;
import com.yagiztufek.matchmaking.player.Player;
import com.yagiztufek.matchmaking.player.PlayerRepository;
import com.yagiztufek.matchmaking.queue.MatchmakingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.yagiztufek.matchmaking.TestData.PROPERTIES;
import static com.yagiztufek.matchmaking.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TournamentServiceTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long PLAYER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final TournamentEntryRepository entries = mock(TournamentEntryRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final TournamentService service = new TournamentService(tournaments, entries, players,
            mock(MatchmakingQueue.class), events, PROPERTIES,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(NOW, ZoneOffset.UTC));

    private final Tournament tournament = withId(new Tournament(NOW), TOURNAMENT_ID);
    private final Player player = withId(new Player("yagiz", Country.TR, 5_000, NOW), PLAYER_ID);

    @BeforeEach
    void setUp() {
        when(tournaments.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(players.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player));
        when(entries.save(any(TournamentEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void eligiblePlayerPaysTheFeeAndJoinsTheQueue() {
        levelUpTo(20);

        TournamentEntry entry = service.enter(TOURNAMENT_ID, PLAYER_ID);

        assertThat(entry.getStatus()).isEqualTo(TournamentEntry.Status.QUEUED);
        assertThat(entry.getCountry()).isEqualTo(Country.TR);
        assertThat(player.getCoins()).isEqualTo(5_000 + 19 * 25 - 1_000);
        verify(events).publish(Events.PLAYER_JOINED, TOURNAMENT_ID,
                new Events.PlayerJoined(TOURNAMENT_ID, PLAYER_ID, Country.TR, NOW));
    }

    @Test
    void playerBelowTheMinimumLevelIsRejected() {
        levelUpTo(19);

        assertThatThrownBy(() -> service.enter(TOURNAMENT_ID, PLAYER_ID))
                .isInstanceOf(NotEligibleException.class)
                .hasMessageContaining("level 20 is required");
        verify(entries, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void playerWhoCannotAffordTheFeeIsRejected() {
        levelUpTo(20);
        player.pay(player.getCoins() - 999);

        assertThatThrownBy(() -> service.enter(TOURNAMENT_ID, PLAYER_ID))
                .isInstanceOf(NotEligibleException.class);
        verifyNoInteractions(events);
    }

    @Test
    void enteringTheSameTournamentTwiceIsRejected() {
        levelUpTo(20);
        when(entries.existsByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.enter(TOURNAMENT_ID, PLAYER_ID))
                .isInstanceOf(ConflictException.class);
        assertThat(player.getCoins()).isEqualTo(5_000 + 19 * 25);
    }

    @Test
    void closedTournamentAcceptsNoEntries() {
        levelUpTo(20);
        tournament.close();

        assertThatThrownBy(() -> service.enter(TOURNAMENT_ID, PLAYER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("closed");
        verifyNoInteractions(events);
    }

    private void levelUpTo(int level) {
        while (player.getLevel() < level) {
            player.levelUp();
        }
    }
}
