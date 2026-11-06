package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapFavorite;
import com.dogdog.nomat.domain.map.entity.MapFavoriteId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapFavoriteRepository extends JpaRepository<MapFavorite, MapFavoriteId> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO map_favorites (map_id, user_id, created_at)
            VALUES (:mapId, :userId, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("mapId") Long mapId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM map_favorites WHERE map_id = :mapId AND user_id = :userId", nativeQuery = true)
    int deleteByMapIdAndUserId(@Param("mapId") Long mapId, @Param("userId") Long userId);

    @Query("SELECT COUNT(mapFavorite) FROM MapFavorite mapFavorite WHERE mapFavorite.id.mapId = :mapId")
    long countByMapId(@Param("mapId") Long mapId);

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
