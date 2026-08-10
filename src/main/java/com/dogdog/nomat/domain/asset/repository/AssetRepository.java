package com.dogdog.nomat.domain.asset.repository;

import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            AssetStatus status,
            LocalDateTime createdAt,
            Pageable pageable
    );
}
