package com.yagiztufek.matchmaking.tournament;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TournamentEntryRepository extends JpaRepository<TournamentEntry, Long> {

    Optional<TournamentEntry> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    /** Locks the entries so two matchmakers can't both move the same player out of QUEUED. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from TournamentEntry e
            where e.tournamentId = :tournamentId and e.playerId in :playerIds
            order by e.playerId""")
    List<TournamentEntry> findForUpdate(@Param("tournamentId") Long tournamentId,
                                        @Param("playerIds") Collection<Long> playerIds);

    List<TournamentEntry> findByTournamentIdAndPlayerIdIn(Long tournamentId, Collection<Long> playerIds);

    List<TournamentEntry> findByStatusAndJoinedAtBefore(TournamentEntry.Status status, Instant before, Limit limit);
}
