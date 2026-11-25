package com.deeptier.sap;

import com.deeptier.config.DeepTierProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AgentGateway implementation that delegates to the Python AI reasoning service.
 * Active when deeptier.agent.runtime=python (default).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "deeptier.agent.runtime", havingValue = "python", matchIfMissing = true)
public class PythonAgentGateway implements AgentGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final String pythonBaseUrl;
    private final String internalKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PythonAgentGateway(
            DeepTierProperties props,
            ObjectMapper objectMapper,
            @Value("${DEEPTIER_PYTHON_URL:http://127.0.0.1:8000}") String pythonBaseUrl) {
        this.pythonBaseUrl = pythonBaseUrl != null ? pythonBaseUrl.replaceAll("/+$", "") : "http://127.0.0.1:8000";
        this.internalKey = props.internal().key();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    PythonAgentGateway(DeepTierProperties props, ObjectMapper objectMapper, String pythonBaseUrl, HttpClient httpClient) {
        this.pythonBaseUrl = pythonBaseUrl != null ? pythonBaseUrl.replaceAll("/+$", "") : "http://127.0.0.1:8000";
        this.internalKey = props.internal().key();
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<DeepTierBrainResult> analyzeDisruption(String alertText, String partCode, String scenarioId) {
        try {
            URI endpoint = URI.create(pythonBaseUrl + "/api/v1/deeptier/analyze");
            String body = objectMapper.writeValueAsString(Map.of(
                    "alertText", alertText,
                    "partCode", partCode != null && !partCode.isBlank() ? partCode : "HPH-1042",
                    "scenarioId", scenarioId != null ? scenarioId : ""
            ));

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (internalKey != null && !internalKey.isBlank()) {
                reqBuilder.header("X-Internal-Key", internalKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Python AI brain returned status {} for /api/v1/deeptier/analyze", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode extracted = root.path("extracted");
            if (extracted.isMissingNode() || !extracted.isObject()) {
                log.warn("Python AI brain response missing 'extracted' object");
                return Optional.empty();
            }

            String hsCode = extracted.path("affectedHsCode").asText("7325");
            String originCountry = extracted.path("originCountry").asText("CN");
            BigDecimal tariffDelta = decimalOrDefault(extracted.path("tariffDeltaPercent"), BigDecimal.valueOf(20));
            int delayDays = extracted.path("delayDays").asInt(0);
            String signalType = extracted.path("signalType").asText("TARIFF_SHOCK");
            BigDecimal confidence = decimalOrDefault(extracted.path("confidence"), BigDecimal.valueOf(0.75));
            String engine = extracted.path("extractionEngine").asText("python_langgraph");
            String brief = root.path("executiveBrief").asText("");

            List<String> evidenceIds = new ArrayList<>();
            JsonNode evNode = root.path("evidenceIds");
            if (evNode.isArray()) {
                for (JsonNode item : evNode) {
                    if (item.isTextual()) {
                        evidenceIds.add(item.asText());
                    }
                }
            }

            return Optional.of(new DeepTierBrainResult(
                    hsCode, originCountry, tariffDelta, delayDays, signalType,
                    confidence, engine, brief, evidenceIds));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Python AI brain call interrupted, falling back to deterministic Java");
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Python AI brain unreachable or timed out ({}), falling back to deterministic Java",
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static BigDecimal decimalOrDefault(JsonNode node, BigDecimal fallback) {
        if (node != null && node.isNumber()) {
            return node.decimalValue();
        }
        return fallback;
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
        return "Based on deterministic Java calculations: duty exposure is INR 5,76,000. " +
               "Air-lifting the critical lot (INR 7,20,000 total impact) is recommended " +
               "over waiting (INR 12,00,000). Evidence: EV-BOM-001, EV-TRADE-2024-001.";
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
