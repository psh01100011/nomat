package com.dogdog.nomat.domain.game.repository;

import com.dogdog.nomat.domain.game.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    boolean existsBySourceRoomId(Long sourceRoomId);

    @Query("""
            SELECT COALESCE(SUM(session.playerCount), 0)
            FROM GameSession session
            WHERE session.map.id = :mapId
            """)
    long sumPlayerCountByMapId(@Param("mapId") Long mapId);
}
