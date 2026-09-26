package com.yagiztufek.matchmaking.player;

import java.time.Clock;
import java.util.random.RandomGenerator;

import com.yagiztufek.matchmaking.common.ConflictException;
import com.yagiztufek.matchmaking.common.Country;
import com.yagiztufek.matchmaking.common.NotFoundException;
import com.yagiztufek.matchmaking.config.MatchmakingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository players;
    private final MatchmakingProperties properties;
    private final RandomGenerator random;
    private final Clock clock;

    public PlayerService(PlayerRepository players, MatchmakingProperties properties,
                         RandomGenerator random, Clock clock) {
        this.players = players;
        this.properties = properties;
        this.random = random;
        this.clock = clock;
    }

    /** Creates a player; if no country is given one is assigned at random. */
    @Transactional
    public Player create(String username, Country country) {
        if (players.existsByUsername(username)) {
            throw new ConflictException("Username '%s' is taken".formatted(username));
        }
        Country assigned = country != null ? country : Country.values()[random.nextInt(Country.values().length)];
        return players.save(new Player(username, assigned, properties.startingCoins(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Player get(long id) {
        return players.findById(id).orElseThrow(() -> new NotFoundException("Player %d not found".formatted(id)));
    }

    @Transactional
    public Player levelUp(long id) {
        Player player = players.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Player %d not found".formatted(id)));
        player.levelUp();
        return player;
    }
}
