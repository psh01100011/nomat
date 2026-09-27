package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapLike;
import com.dogdog.nomat.domain.map.entity.MapLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapLikeRepository extends JpaRepository<MapLike, MapLikeId> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO map_likes (map_id, user_id, created_at)
            VALUES (:mapId, :userId, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("mapId") Long mapId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM map_likes WHERE map_id = :mapId AND user_id = :userId", nativeQuery = true)
    int deleteByMapIdAndUserId(@Param("mapId") Long mapId, @Param("userId") Long userId);

    @Query("SELECT COUNT(mapLike) FROM MapLike mapLike WHERE mapLike.id.mapId = :mapId")
    long countByMapId(@Param("mapId") Long mapId);

    @Query("""
            SELECT mapLike.id.mapId
            FROM MapLike mapLike
            WHERE mapLike.id.userId = :userId
              AND mapLike.id.mapId IN :mapIds
            """)
    List<Long> findMapIdsByUserIdAndMapIdIn(
            @Param("userId") Long userId,
            @Param("mapIds") Collection<Long> mapIds
    );
}
