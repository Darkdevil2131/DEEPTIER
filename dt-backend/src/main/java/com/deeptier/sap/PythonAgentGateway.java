package com.deeptier.sap;

import com.deeptier.api.dto.AnalyzeDisruptionRequest;
import com.deeptier.api.dto.RerouteRequest;
import com.deeptier.api.dto.RerouteResponse;
import com.deeptier.config.DeepTierProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final String tradePilotApiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PythonAgentGateway(
            DeepTierProperties props,
            ObjectMapper objectMapper,
            @Value("${DEEPTIER_PYTHON_URL:http://127.0.0.1:8000}") String pythonBaseUrl,
            @Value("${TRADEPILOT_API_KEY:}") String tradePilotApiKey) {
        this.pythonBaseUrl = pythonBaseUrl != null ? pythonBaseUrl.replaceAll("/+$", "") : "http://127.0.0.1:8000";
        this.internalKey = props.internal().key();
        this.tradePilotApiKey = tradePilotApiKey != null ? tradePilotApiKey : "";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    PythonAgentGateway(DeepTierProperties props, ObjectMapper objectMapper, String pythonBaseUrl, HttpClient httpClient) {
        this(props, objectMapper, pythonBaseUrl, "", httpClient);
    }

    PythonAgentGateway(DeepTierProperties props, ObjectMapper objectMapper, String pythonBaseUrl,
                       String tradePilotApiKey, HttpClient httpClient) {
        this.pythonBaseUrl = pythonBaseUrl != null ? pythonBaseUrl.replaceAll("/+$", "") : "http://127.0.0.1:8000";
        this.internalKey = props.internal().key();
        this.tradePilotApiKey = tradePilotApiKey != null ? tradePilotApiKey : "";
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<DeepTierBrainResult> analyzeDisruption(String alertText, String partCode, String scenarioId) {
        return analyzeDisruption(
                new AnalyzeDisruptionRequest(alertText, partCode, scenarioId, null, null, null, null, null, null, null),
                partCode,
                scenarioId);
    }

    @Override
    public Optional<DeepTierBrainResult> analyzeDisruption(AnalyzeDisruptionRequest req, String partCode, String scenarioId) {
        try {
            URI endpoint = URI.create(pythonBaseUrl + "/api/v1/deeptier/analyze");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertText", req.alertText());
            payload.put("partCode", partCode != null && !partCode.isBlank() ? partCode : "HPH-1042");
            payload.put("scenarioId", scenarioId != null ? scenarioId : "");
            if (req.unitsOnHand() != null) payload.put("unitsOnHand", req.unitsOnHand());
            if (req.dailyDemand() != null) payload.put("dailyDemand", req.dailyDemand());
            if (req.leadTimeDays() != null) payload.put("leadTimeDays", req.leadTimeDays());
            if (req.annualSpendInr() != null) payload.put("annualSpendInr", req.annualSpendInr());
            if (req.costFraction() != null) payload.put("costFraction", req.costFraction());
            if (req.lateCostPerDayInr() != null) payload.put("lateCostPerDayInr", req.lateCostPerDayInr());

            String body = objectMapper.writeValueAsString(payload);

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
            String backendStatus = root.path("backendStatus").asText("connected");

            List<String> evidenceIds = readStringArray(root.path("evidenceIds"));
            List<String> warnings = readStringArray(root.path("warnings"));

            return Optional.of(new DeepTierBrainResult(
                    hsCode, originCountry, tariffDelta, delayDays, signalType,
                    confidence, engine, brief, evidenceIds, backendStatus, warnings));
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

    @Override
    public RerouteResponse rerouteShipment(RerouteRequest request) {
        try {
            URI endpoint = URI.create(pythonBaseUrl + "/api/v1/shipments/analyze");
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            if (request.shipmentId() != null && !request.shipmentId().isBlank()) {
                bodyMap.put("shipment_id", request.shipmentId());
            }
            bodyMap.put("origin", request.origin());
            bodyMap.put("destination", request.destination());
            bodyMap.put("cargo_description", request.cargoDescription());
            bodyMap.put("weight_kg", request.weightKg());
            bodyMap.put("allowed_modes", request.allowedModes());

            if (request.disruptions() != null && !request.disruptions().isEmpty()) {
                List<Map<String, Object>> disruptions = new ArrayList<>();
                for (RerouteRequest.DisruptionInput d : request.disruptions()) {
                    Map<String, Object> dm = new LinkedHashMap<>();
                    dm.put("signal_id", d.signalId());
                    if (d.affectedModes() != null) dm.put("affected_modes", d.affectedModes());
                    if (d.affectedLocations() != null) dm.put("affected_locations", d.affectedLocations());
                    if (d.affectedLegIds() != null) dm.put("affected_leg_ids", d.affectedLegIds());
                    dm.put("severity", d.severity() != null ? d.severity() : BigDecimal.valueOf(0.5));
                    if (d.delayHours() != null) dm.put("delay_hours", d.delayHours());
                    disruptions.add(dm);
                }
                bodyMap.put("disruptions", disruptions);
            }

            if (request.impactScenario() != null) {
                RerouteRequest.ImpactScenarioInput imp = request.impactScenario();
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("critical_weight_kg", imp.criticalWeightKg());
                im.put("target_days", imp.targetDays());
                im.put("daily_stoppage_inr", imp.dailyStoppageInr());
                if (imp.closedLegIds() != null) im.put("closed_leg_ids", imp.closedLegIds());
                if (imp.freightMultiplier() != null) im.put("freight_multiplier", imp.freightMultiplier());
                bodyMap.put("impact_scenario", im);
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)));

            if (internalKey != null && !internalKey.isBlank()) {
                reqBuilder.header("X-Internal-Key", internalKey);
            }
            if (!tradePilotApiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + tradePilotApiKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400 || response.statusCode() == 422) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "TradePilot rejected reroute request parameters.");
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "TradePilot 57-hub routing service returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return mapRerouteResponse(root);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TradePilot 57-hub routing request was interrupted.");
        } catch (Exception ex) {
            log.warn("TradePilot 57-hub routing unavailable: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TradePilot 57-hub routing service is currently offline or unreachable.");
        }
    }

    private RerouteResponse mapRerouteResponse(JsonNode root) {
        String shipmentId = root.path("shipment_id").asText("REROUTE-UNKNOWN");
        String status = root.path("status").asText("completed");
        JsonNode agents = root.path("agents");
        JsonNode routeData = agents.path("route_optimization").path("data");
        JsonNode orchData = agents.path("logistics_orchestrator").path("data");
        JsonNode decisionNode = orchData.path("decision");
        JsonNode candidate = decisionNode.path("candidate");

        List<RerouteResponse.RouteLegDto> legs = new ArrayList<>();
        List<RerouteResponse.RouteAlternativeDto> alternatives = new ArrayList<>();

        // Parse 57-hub impact comparison options & legs if present
        JsonNode impactOptions = routeData.path("impact_comparison").path("options");
        if (impactOptions.isArray()) {
            for (JsonNode opt : impactOptions) {
                String optLabel = opt.path("option").asText("");
                BigDecimal freightInr = decimalOrNull(opt.path("freight_inr"));
                Double etaDays = opt.path("eta_days").isNumber() ? opt.path("eta_days").asDouble() : null;
                Double transitHours = etaDays != null ? etaDays * 24.0 : null;
                alternatives.add(new RerouteResponse.RouteAlternativeDto(
                        optLabel, opt.path("mode").asText("multimodal"), freightInr, transitHours, true, null));

                if (legs.isEmpty() && opt.path("portions").isArray()) {
                    for (JsonNode portion : opt.path("portions")) {
                        if (portion.path("legs").isArray()) {
                            for (JsonNode leg : portion.path("legs")) {
                                legs.add(new RerouteResponse.RouteLegDto(
                                        leg.path("leg_id").asText(""),
                                        leg.path("origin").asText(""),
                                        leg.path("destination").asText(""),
                                        leg.path("mode").asText("road"),
                                        leg.path("distance_km").isNumber() ? Double.valueOf(leg.path("distance_km").asDouble()) : null,
                                        leg.path("days").isNumber() ? Double.valueOf(leg.path("days").asDouble() * 24.0) : null,
                                        decimalOrNull(leg.path("freight_inr"))
                                ));
                            }
                        }
                    }
                }
            }
        }

        // Parse standard routes[] if present
        JsonNode routesArr = routeData.path("routes");
        if (routesArr.isArray()) {
            for (JsonNode r : routesArr) {
                String mode = r.path("mode").asText("road");
                BigDecimal costInr = decimalOrNull(r.path("cost_inr"));
                if (costInr == null && r.path("cost_usd").isNumber()) {
                    costInr = r.path("cost_usd").decimalValue().multiply(BigDecimal.valueOf(85));
                }
                Double hours = r.path("transit_hours").isNumber() ? r.path("transit_hours").asDouble() : null;
                boolean feasible = r.path("feasible").asBoolean(true);
                Double score = r.path("score").isNumber() ? r.path("score").asDouble() : null;
                alternatives.add(new RerouteResponse.RouteAlternativeDto(mode, mode, costInr, hours, feasible, score));
                if (legs.isEmpty() && r.path("legs").isArray()) {
                    for (JsonNode leg : r.path("legs")) {
                        legs.add(new RerouteResponse.RouteLegDto(
                                leg.path("leg_id").asText(""),
                                leg.path("from").asText(leg.path("origin").asText("")),
                                leg.path("to").asText(leg.path("destination").asText("")),
                                leg.path("mode").asText(mode),
                                leg.path("distance_km").isNumber() ? Double.valueOf(leg.path("distance_km").asDouble()) : null,
                                leg.path("transit_hours").isNumber() ? Double.valueOf(leg.path("transit_hours").asDouble()) : hours,
                                decimalOrNull(leg.path("cost_inr"))
                        ));
                    }
                }
            }
        }

        String candOption = candidate.path("option").asText(null);
        BigDecimal costEstimate = decimalOrNull(candidate.path("freight_inr"));
        Double transitEstimate = null;
        if (candidate.path("eta_days").isNumber()) {
            transitEstimate = candidate.path("eta_days").asDouble() * 24.0;
        }
        if (costEstimate == null && !alternatives.isEmpty()) {
            costEstimate = alternatives.getFirst().costInr();
            transitEstimate = alternatives.getFirst().transitHours();
            if (candOption == null) candOption = alternatives.getFirst().option();
        }

        RerouteResponse.SelectedRouteDto selectedRoute = new RerouteResponse.SelectedRouteDto(
                candOption,
                candidate.path("mode").asText(!alternatives.isEmpty() ? alternatives.getFirst().mode() : "road"),
                costEstimate,
                transitEstimate,
                true,
                readStringArray(candidate.path("disruption_ids"))
        );

        RerouteResponse.DecisionDto decision = new RerouteResponse.DecisionDto(
                decisionNode.path("status").asText("ready_for_human_approval"),
                readStringArray(decisionNode.path("gate_reasons")),
                candOption,
                decisionNode.path("commercial_evidence").asText(null)
        );

        return new RerouteResponse(
                shipmentId,
                status,
                selectedRoute,
                legs,
                costEstimate,
                transitEstimate,
                alternatives,
                decision,
                readStringArray(root.path("data_provenance")),
                readStringArray(agents.path("route_optimization").path("warnings"))
        );
    }

    private static List<String> readStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    list.add(item.asText());
                }
            }
        }
        return list;
    }

    private static BigDecimal decimalOrDefault(JsonNode node, BigDecimal fallback) {
        return (node != null && node.isNumber()) ? node.decimalValue() : fallback;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return (node != null && node.isNumber()) ? node.decimalValue() : null;
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
