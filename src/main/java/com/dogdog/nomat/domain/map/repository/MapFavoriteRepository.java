package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapFavorite;
import com.dogdog.nomat.domain.map.entity.MapFavoriteId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapFavoriteRepository extends JpaRepository<MapFavorite, MapFavoriteId> {

    @Query("""
            SELECT mapFavorite.id.mapId
            FROM MapFavorite mapFavorite
            WHERE mapFavorite.id.userId = :userId
              AND mapFavorite.id.mapId IN :mapIds
            """)
    List<Long> findMapIdsByUserIdAndMapIdIn(
            @Param("userId") Long userId,
            @Param("mapIds") Collection<Long> mapIds
    );
}
