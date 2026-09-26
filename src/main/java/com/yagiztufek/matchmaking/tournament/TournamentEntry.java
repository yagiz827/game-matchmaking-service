package com.yagiztufek.matchmaking.tournament;

import java.time.Instant;

import com.yagiztufek.matchmaking.common.Country;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A player's place in a tournament. PostgreSQL is the source of truth for the
 * entry's status; the Redis queue is only a fast index over QUEUED entries.
 */
@Entity
@Table(name = "tournament_entries")
public class TournamentEntry {

    public enum Status { QUEUED, MATCHED, FINISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int score;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected TournamentEntry() {
    }

    public TournamentEntry(Long tournamentId, Long playerId, Country country, Instant joinedAt) {
        this.tournamentId = tournamentId;
        this.playerId = playerId;
        this.country = country;
        this.status = Status.QUEUED;
        this.joinedAt = joinedAt;
    }

    public void matched(long matchId) {
        if (status != Status.QUEUED) {
            throw new IllegalStateException("Entry %d is %s, not QUEUED".formatted(id, status));
        }
        this.status = Status.MATCHED;
        this.matchId = matchId;
    }

    public void finished(int points) {
        this.status = Status.FINISHED;
        this.score = points;
    }

    public boolean isQueued() {
        return status == Status.QUEUED;
    }

    public Long getId() {
        return id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public Country getCountry() {
        return country;
    }

    public Status getStatus() {
        return status;
    }

    public int getScore() {
        return score;
    }

    public Long getMatchId() {
        return matchId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
