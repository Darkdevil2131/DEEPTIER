package com.deeptier.sap;

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
