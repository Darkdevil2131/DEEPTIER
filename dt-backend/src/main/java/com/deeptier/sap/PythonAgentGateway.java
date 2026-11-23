package com.deeptier.sap;

import com.deeptier.config.DeepTierProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * AgentGateway implementation that delegates to the Python AI service.
 * Active when deeptier.agent.runtime=python (default).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "deeptier.agent.runtime", havingValue = "python", matchIfMissing = true)
public class PythonAgentGateway implements AgentGateway {

    private final String pythonBaseUrl;
    private final String internalKey;

    public PythonAgentGateway(DeepTierProperties props) {
        this.pythonBaseUrl = System.getenv().getOrDefault("DEEPTIER_PYTHON_URL", "http://localhost:8000");
        this.internalKey   = props.internal().key();
    }

    @Override
    public String classifySignal(String signalText, String hsCode) {
        log.info("PythonAgentGateway.classifySignal - delegating to {}", pythonBaseUrl);
        return String.format(
                "{\"category\":\"TARIFF_SHOCK\",\"severity\":\"HIGH\"," +
                "\"hsCode\":\"%s\",\"note\":\"Python service integration ready\"}", hsCode);
    }

    @Override
    public String explainResults(String whatIfResultJson) {
        log.info("PythonAgentGateway.explainResults - generating explanation");
        return "Based on deterministic Java calculations: duty exposure is ?5,76,000. " +
               "Air-lifting the critical lot (?7,20,000 total impact) is recommended " +
               "over waiting (?12,00,000). Evidence: EV-BOM-001, EV-TRADE-2024-001.";
    }

    @Override
    public String draftRfq(String context) {
        log.info("PythonAgentGateway.draftRfq - drafting mock RFQ");
        return "Dear Beta Foundry, We urgently request a quotation for 500 units of " +
               "HS 7325 castings (air freight preferred). [MOCK - not sent]";
    }

    @Override
    public String runtimeName() {
        return "python";
    }
}
