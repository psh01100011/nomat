package com.dogdog.nomat.domain.map.repository;

import com.dogdog.nomat.domain.map.entity.QuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, Long> {
}
