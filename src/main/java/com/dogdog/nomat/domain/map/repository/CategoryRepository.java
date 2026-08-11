package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
