package com.dogdog.nomat.domain.asset.repository;

import com.dogdog.nomat.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
}
