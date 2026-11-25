package com.deeptier.sap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Hot-swappable AI agent gateway.
 *
 * Implementations:
 *   PythonAgentGateway  - calls Python FastAPI/Flask service (active)
 *   JouleAgentGateway   - calls SAP Joule Studio via SAP Build Actions (future)
 *
 * Switch by setting: deeptier.agent.runtime: python | joule
 *
 * IMPORTANT: Implementations must NEVER compute numbers themselves.
 * They call /internal/tools/* to get deterministic results, then
 * use those results in natural-language explanations and RFQ drafts.
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
            List<String> evidenceIds
    ) {}

    /**
     * Call the AI brain to extract disruption parameters and optionally synthesize a grounded brief.
     * Returns Optional.empty() when the external AI runtime is offline or timed out so Java can
     * seamlessly fall back to deterministic rule-based extraction.
     */
    default Optional<DeepTierBrainResult> analyzeDisruption(String alertText, String partCode, String scenarioId) {
        return Optional.empty();
    }

    /**
     * Classify a disruption signal and return structured risk assessment.
     * @param signalText free-text news/alert/ERP signal
     * @param hsCode     HS code of affected commodity
     * @return structured JSON string with category, severity, evidenceIds
     */
    String classifySignal(String signalText, String hsCode);

    /**
     * Explain what-if results in plain English for the cockpit UI.
     * @param whatIfResultJson JSON from /api/whatif/simulate
     * @return explanation string with evidenceIds cited inline
     */
    String explainResults(String whatIfResultJson);

    /**
     * Draft a mock RFQ for human review.
     * NEVER sends externally. Must go through approval gate.
     * @param context JSON context (supplier, quantity, option)
     * @return draft RFQ text
     */
    String draftRfq(String context);

    /** Current runtime identifier (for /actuator/info) */
    String runtimeName();
}
