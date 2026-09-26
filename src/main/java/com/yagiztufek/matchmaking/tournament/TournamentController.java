package com.yagiztufek.matchmaking.tournament;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.leaderboard.LeaderboardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    private static final int MAX_LEADERBOARD_SIZE = 100;

    private final TournamentService tournamentService;
    private final LeaderboardService leaderboard;

    public TournamentController(TournamentService tournamentService, LeaderboardService leaderboard) {
        this.tournamentService = tournamentService;
        this.leaderboard = leaderboard;
    }

    @PostMapping
    public ResponseEntity<TournamentResponse> create() {
        Tournament tournament = tournamentService.create();
        return ResponseEntity.created(URI.create("/api/v1/tournaments/" + tournament.getId()))
                .body(TournamentResponse.from(tournament));
    }

    @GetMapping("/{id}")
    public TournamentResponse get(@PathVariable long id) {
        return TournamentResponse.from(tournamentService.get(id));
    }

    @PostMapping("/{id}/close")
    public TournamentResponse close(@PathVariable long id) {
        return TournamentResponse.from(tournamentService.close(id));
    }

    /** 202 Accepted: the entry is saved, matchmaking continues in the background. */
    @PostMapping("/{id}/entries")
    public ResponseEntity<EntryResponse> enter(@PathVariable long id, @Valid @RequestBody EnterRequest request) {
        TournamentEntry entry = tournamentService.enter(id, request.playerId());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/tournaments/%d/entries/%d".formatted(id, request.playerId())))
                .body(EntryResponse.from(entry));
    }

    @GetMapping("/{id}/entries/{playerId}")
    public EntryResponse entry(@PathVariable long id, @PathVariable long playerId) {
        return EntryResponse.from(tournamentService.getEntry(id, playerId));
    }

    @GetMapping("/{id}/queue")
    public Map<Country, Long> queue(@PathVariable long id) {
        return tournamentService.queueSizes(id);
    }

    @GetMapping("/{id}/leaderboard")
    public List<LeaderboardService.Row> leaderboard(@PathVariable long id,
                                                    @RequestParam(required = false) Country country,
                                                    @RequestParam(defaultValue = "10") int limit) {
        tournamentService.get(id);
        return leaderboard.top(id, country, Math.clamp(limit, 1, MAX_LEADERBOARD_SIZE));
    }

    public record EnterRequest(@NotNull Long playerId) {
    }

    public record TournamentResponse(long id, Tournament.Status status, Instant createdAt) {

        static TournamentResponse from(Tournament t) {
            return new TournamentResponse(t.getId(), t.getStatus(), t.getCreatedAt());
        }
    }

    public record EntryResponse(long tournamentId, long playerId, Country country, TournamentEntry.Status status,
                                int score, Long matchId, Instant joinedAt) {

        static EntryResponse from(TournamentEntry e) {
            return new EntryResponse(e.getTournamentId(), e.getPlayerId(), e.getCountry(), e.getStatus(),
                    e.getScore(), e.getMatchId(), e.getJoinedAt());
        }
    }
}
