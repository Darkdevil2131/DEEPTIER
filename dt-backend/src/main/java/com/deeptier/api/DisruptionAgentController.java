package com.deeptier.api;

import com.deeptier.api.dto.AnalyzeDisruptionRequest;
import com.deeptier.api.dto.AnalyzeDisruptionResponse;
import com.deeptier.engine.DisruptionAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "Disruption Agent", description = "Hybrid AI + deterministic Java disruption analysis")
public class DisruptionAgentController {

    private final DisruptionAgentService disruptionAgentService;

    @PostMapping("/analyze-disruption")
    @Operation(summary = "Analyze a supply-chain disruption alert",
            description = "Delegates parameter extraction to the Python AI brain with automatic "
                    + "deterministic Java fallback, executes all financial/inventory math in Java, "
                    + "persists a DisruptionEvent, and logs an AuditEvent.")
    public ResponseEntity<AnalyzeDisruptionResponse> analyzeDisruption(
            @Valid @RequestBody AnalyzeDisruptionRequest request) {
        log.info("Analyzing disruption for partCode={}", request.partCode());
        return ResponseEntity.ok(disruptionAgentService.analyzeDisruption(request));
    }
}
