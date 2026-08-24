package com.dogdog.nomat.domain.game.repository;

import com.dogdog.nomat.domain.game.entity.MapPlayHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPlayHistoryRepository extends JpaRepository<MapPlayHistory, Long> {

    Optional<MapPlayHistory> findByUserIdAndMapId(Long userId, Long mapId);
}
