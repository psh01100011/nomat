package com.dogdog.nomat.domain.game.repository;

import com.dogdog.nomat.domain.game.entity.GamePlayerResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePlayerResultRepository extends JpaRepository<GamePlayerResult, Long> {
}
