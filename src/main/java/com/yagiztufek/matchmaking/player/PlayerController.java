package com.yagiztufek.matchmaking.player;

import java.net.URI;

import com.yagiztufek.matchmaking.common.Country;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> create(@Valid @RequestBody CreatePlayerRequest request) {
        Player player = playerService.create(request.username(), request.country());
        return ResponseEntity.created(URI.create("/api/v1/players/" + player.getId()))
                .body(PlayerResponse.from(player));
    }

    @GetMapping("/{id}")
    public PlayerResponse get(@PathVariable long id) {
        return PlayerResponse.from(playerService.get(id));
    }

    @PostMapping("/{id}/level-up")
    public PlayerResponse levelUp(@PathVariable long id) {
        return PlayerResponse.from(playerService.levelUp(id));
    }

    /** {@code country} is optional; a random one is assigned when omitted. */
    public record CreatePlayerRequest(@NotBlank @Size(max = 50) String username, Country country) {
    }

    public record PlayerResponse(long id, String username, Country country, int level, long coins) {

        static PlayerResponse from(Player p) {
            return new PlayerResponse(p.getId(), p.getUsername(), p.getCountry(), p.getLevel(), p.getCoins());
        }
    }
}
