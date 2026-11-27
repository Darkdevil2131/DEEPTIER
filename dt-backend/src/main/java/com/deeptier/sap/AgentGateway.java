package com.deeptier.sap;

import com.deeptier.api.dto.AnalyzeDisruptionRequest;
import com.deeptier.api.dto.RerouteRequest;
import com.deeptier.api.dto.RerouteResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Hot-swappable AI agent gateway.
 *
 * Implementations:
 *   PythonAgentGateway  - calls Python FastAPI/LangGraph service (active)
 *   JouleAgentGateway   - calls SAP Joule Studio via SAP Build Actions (future)
 */
public interface AgentGateway {

    record DeepTierBrainResult(
            String affectedHsCode,
            String originCountry,
            BigDecimal tariffDeltaPercent,
            int delayDays,
            String signalType,
            BigDecimal confidence,
            String extractionEngine,
            String executiveBrief,
            List<String> evidenceIds,
            String backendStatus,
            List<String> warnings
    ) {
        public DeepTierBrainResult(
                String affectedHsCode,
                String originCountry,
                BigDecimal tariffDeltaPercent,
                int delayDays,
                String signalType,
                BigDecimal confidence,
                String extractionEngine,
                String executiveBrief,
                List<String> evidenceIds) {
            this(affectedHsCode, originCountry, tariffDeltaPercent, delayDays, signalType,
                    confidence, extractionEngine, executiveBrief, evidenceIds, "connected", List.of());
        }
    }

    default Optional<DeepTierBrainResult> analyzeDisruption(String alertText, String partCode, String scenarioId) {
        return Optional.empty();
    }

    default Optional<DeepTierBrainResult> analyzeDisruption(AnalyzeDisruptionRequest req, String partCode, String scenarioId) {
        return analyzeDisruption(req.alertText(), partCode, scenarioId);
    }

    default RerouteResponse rerouteShipment(RerouteRequest request) {
        throw new UnsupportedOperationException("57-hub logistics routing is unavailable on runtime: " + runtimeName());
    }

    String classifySignal(String signalText, String hsCode);

    String explainResults(String whatIfResultJson);

    String draftRfq(String context);

    String runtimeName();
}
