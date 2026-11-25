package com.deeptier.repo;

import com.deeptier.domain.DisruptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, String> {

    List<DisruptionEvent> findByPartCodeOrderByCreatedAtDesc(String partCode);

    List<DisruptionEvent> findByScenarioIdOrderByCreatedAtDesc(String scenarioId);
}
