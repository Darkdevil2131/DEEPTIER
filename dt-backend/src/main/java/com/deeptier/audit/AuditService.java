package com.deeptier.audit;

import com.deeptier.repo.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditRepo;

    @Transactional
    public AuditEvent logEvent(
            String scenarioId,
            String eventType,
            String actor,
            String entityType,
            String entityId,
            String payload) {

        String prevHash = auditRepo.findFirstByOrderByOccurredAtDesc()
                .map(AuditEvent::getAfterHash)
                .orElse("0");

        String currentPayload = payload != null ? payload : "{}";
        String afterHash = computeSha256(prevHash + currentPayload);

        AuditEvent event = AuditEvent.builder()
                .scenarioId(scenarioId)
                .eventType(eventType)
                .actor(actor != null ? actor : "system")
                .entityType(entityType)
                .entityId(entityId)
                .payload(currentPayload)
                .beforeHash(prevHash)
                .afterHash(afterHash)
                .occurredAt(Instant.now())
                .build();

        AuditEvent saved = auditRepo.save(event);
        log.info("Audit logged: eventType={}, actor={}, beforeHash={}, afterHash={}",
                eventType, actor, prevHash, afterHash);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getEvents(String scenarioId) {
        if (scenarioId != null && !scenarioId.isBlank()) {
            return auditRepo.findByScenarioIdOrderByOccurredAtAsc(scenarioId);
        }
        return auditRepo.findAll();
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
