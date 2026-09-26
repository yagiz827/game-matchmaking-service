package com.yagiztufek.matchmaking.player;

import java.time.Instant;

import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.common.NotEligibleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class Player {

    static final long LEVEL_UP_BONUS = 25;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Country country;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private long coins;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Player() {
    }

    public Player(String username, Country country, long startingCoins, Instant createdAt) {
        this.username = username;
        this.country = country;
        this.level = 1;
        this.coins = startingCoins;
        this.createdAt = createdAt;
    }

    public void levelUp() {
        level++;
        coins += LEVEL_UP_BONUS;
    }

    public void pay(long amount) {
        if (coins < amount) {
            throw new NotEligibleException("Player %d has %d coins but needs %d".formatted(id, coins, amount));
        }
        coins -= amount;
    }

    public void reward(long amount) {
        coins += amount;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Country getCountry() {
        return country;
    }

    public int getLevel() {
        return level;
    }

    public long getCoins() {
        return coins;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
