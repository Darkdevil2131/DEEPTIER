package com.deeptier.api;

import com.deeptier.audit.AuditEvent;
import com.deeptier.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable hash-chained audit log")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/api/audit")
    @Operation(summary = "Get the hash-chained audit log")
    public ResponseEntity<List<AuditEvent>> getAuditLog(
            @RequestParam(required = false) String scenarioId) {
        return ResponseEntity.ok(auditService.getEvents(scenarioId));
    }

    @GetMapping("/api/audit/events")
    @Operation(summary = "List all audit events (chronological)")
    public ResponseEntity<List<AuditEvent>> listAll() {
        return ResponseEntity.ok(auditService.getEvents(null));
    }

    @GetMapping("/api/audit/events/scenario/{scenarioId}")
    @Operation(summary = "Get audit trail for a specific scenario")
    public ResponseEntity<List<AuditEvent>> byScenario(@PathVariable String scenarioId) {
        return ResponseEntity.ok(auditService.getEvents(scenarioId));
    }
}
