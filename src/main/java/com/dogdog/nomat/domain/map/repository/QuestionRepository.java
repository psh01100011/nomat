package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
}
