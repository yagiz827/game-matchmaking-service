package com.yagiztufek.matchmaking.match;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    List<MatchParticipant> findByMatchIdOrderByPlayerId(Long matchId);
}
