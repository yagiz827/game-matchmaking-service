package com.yagiztufek.matchmaking.match;

import com.yagiztufek.matchmaking.common.Country;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "match_participants")
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Country country;

    private Integer placement;

    private Integer points;

    @Column(name = "coins_awarded")
    private Long coinsAwarded;

    protected MatchParticipant() {
    }

    public MatchParticipant(Long matchId, Long playerId, Country country) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.country = country;
    }

    public void recordResult(int placement, int points, long coinsAwarded) {
        this.placement = placement;
        this.points = points;
        this.coinsAwarded = coinsAwarded;
    }

    public Long getId() {
        return id;
    }

    public Long getMatchId() {
        return matchId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public Country getCountry() {
        return country;
    }

    public Integer getPlacement() {
        return placement;
    }

    public Integer getPoints() {
        return points;
    }

    public Long getCoinsAwarded() {
        return coinsAwarded;
    }
}
