package com.dogdog.nomat.domain.game.repository;

import com.dogdog.nomat.domain.game.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
}
