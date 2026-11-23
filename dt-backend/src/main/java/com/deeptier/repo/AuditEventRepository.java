package com.deeptier.repo;

import com.deeptier.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    List<AuditEvent> findByScenarioIdOrderByOccurredAtAsc(String scenarioId);

    Optional<AuditEvent> findFirstByOrderByOccurredAtDesc();

    default Optional<AuditEvent> findLatest() {
        return findFirstByOrderByOccurredAtDesc();
    }
}
