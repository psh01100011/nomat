package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.MapLike;
import com.dogdog.nomat.domain.map.entity.MapLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapLikeRepository extends JpaRepository<MapLike, MapLikeId> {

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
