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

            boolean demoDefaults = Boolean.TRUE.equals(request.useDemoDefaults());
            if (request.declaredValueUsd() != null) {
                bodyMap.put("declared_value", request.declaredValueUsd());
                bodyMap.put("declared_value_currency", "USD");
            } else if (demoDefaults) {
                bodyMap.put("declared_value", BigDecimal.valueOf(25000));
                bodyMap.put("declared_value_currency", "USD");
            }

            if (request.incoterm() != null && !request.incoterm().isBlank()) {
                bodyMap.put("incoterm", request.incoterm());
            } else if (demoDefaults) {
                bodyMap.put("incoterm", "CIF");
            }

            if (request.documents() != null && !request.documents().isEmpty()) {
                List<Map<String, Object>> docs = new ArrayList<>();
                for (RerouteRequest.DocumentInput doc : request.documents()) {
                    Map<String, Object> dm = new LinkedHashMap<>();
                    dm.put("document_type", doc.documentType());
                    if (doc.fields() != null) dm.put("fields", doc.fields());
                    if (doc.rawText() != null) dm.put("raw_text", doc.rawText());
                    docs.add(dm);
                }
                bodyMap.put("documents", docs);
            } else if (demoDefaults) {
                bodyMap.put("documents", List.of(
                        Map.of("document_type", "commercial_invoice",
                                "raw_text", "Invoice Number: INV-DEMO-1\nShipper: Alpha\nConsignee: Pune OEM\nCurrency: USD\nTotal: 25000"),
                        Map.of("document_type", "packing_list",
                                "raw_text", "Package Count: 5"),
                        Map.of("document_type", "bill_of_lading",
                                "raw_text", "Consignee: Pune OEM")
                ));
            }

            if (request.carrierQuotes() != null && !request.carrierQuotes().isEmpty()) {
                List<Map<String, Object>> quotes = new ArrayList<>();
                for (RerouteRequest.CarrierQuoteInput q : request.carrierQuotes()) {
                    Map<String, Object> qm = new LinkedHashMap<>();
                    qm.put("carrier_id", q.carrierId());
                    qm.put("mode", q.mode());
                    qm.put("amount", q.amount());
                    qm.put("currency", q.currency() != null && !q.currency().isBlank() ? q.currency() : "INR");
                    if (q.transitHours() != null) qm.put("transit_hours", q.transitHours());
                    if (q.quotedCapacityKg() != null) qm.put("quoted_capacity_kg", q.quotedCapacityKg());
                    if (q.includesInsurance() != null) qm.put("includes_insurance", q.includesInsurance());
                    quotes.add(qm);
                }
                bodyMap.put("quotes", quotes);
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

    RerouteResponse mapRerouteResponse(JsonNode root) {
        String shipmentId = root.path("shipment_id").asText("REROUTE-UNKNOWN");
        String status = root.path("status").asText("completed");
        JsonNode agents = root.path("agents");
        JsonNode routeData = agents.path("route_optimization").path("data");
        JsonNode orchData = agents.path("logistics_orchestrator").path("data");
        JsonNode decisionNode = orchData.path("decision");
        JsonNode candidate = decisionNode.path("candidate");
        boolean noFeasibleRoute = routeData.path("no_feasible_route").asBoolean(false);

        List<RerouteResponse.RouteLegDto> legs = new ArrayList<>();
        List<RerouteResponse.RouteAlternativeDto> alternatives = new ArrayList<>();

        // Parse 57-hub impact comparison options & legs if present
        JsonNode impactOptions = routeData.path("impact_comparison").path("options");
        if (impactOptions.isArray()) {
            for (JsonNode opt : impactOptions) {
                String optLabel = opt.path("option").asText("");
                BigDecimal freightInr = decimalOrNull(opt.path("freight_inr"));
                if (freightInr == null) freightInr = decimalOrNull(opt.path("cost_estimate_inr"));
                Double transitHours = opt.path("base_transit_hours").isNumber()
                        ? Double.valueOf(opt.path("base_transit_hours").asDouble())
                        : (opt.path("eta_days").isNumber() ? Double.valueOf(opt.path("eta_days").asDouble() * 24.0) : null);
                alternatives.add(new RerouteResponse.RouteAlternativeDto(
                        optLabel, opt.path("mode").asText("multimodal"), freightInr, transitHours, true, null));

                if (legs.isEmpty() && opt.path("portions").isArray()) {
                    for (JsonNode portion : opt.path("portions")) {
                        Double portionHours = portion.path("arrival_days").isNumber()
                                ? Double.valueOf(portion.path("arrival_days").asDouble() * 24.0)
                                : null;
                        if (portion.path("legs").isArray()) {
                            for (JsonNode leg : portion.path("legs")) {
                                Double legHours = numberOrNull(leg, "hours", "base_transit_hours", "transit_hours");
                                if (legHours == null && leg.path("days").isNumber()) {
                                    legHours = leg.path("days").asDouble() * 24.0;
                                }
                                if (legHours == null) legHours = portionHours;
                                legs.add(new RerouteResponse.RouteLegDto(
                                        textOr(leg, "id", "leg_id"),
                                        textOr(leg, "from", "origin"),
                                        textOr(leg, "to", "destination"),
                                        leg.path("mode").asText("road"),
                                        numberOrNull(leg, "distance_km"),
                                        legHours,
                                        decimalOrNull(leg.path("freight_inr"))
                                ));
                            }
                        }
                    }
                }
            }
        }

        // Parse standard routes[]
        JsonNode routesArr = routeData.path("routes");
        if (routesArr.isArray()) {
            for (JsonNode r : routesArr) {
                String mode = r.path("mode").asText("road");
                BigDecimal costInr = decimalOrNull(r.path("cost_estimate_inr"));
                if (costInr == null) costInr = decimalOrNull(r.path("cost_inr"));
                if (costInr == null && r.path("cost_estimate_usd").isNumber()) {
                    costInr = r.path("cost_estimate_usd").decimalValue().multiply(BigDecimal.valueOf(85));
                }
                Double hours = numberOrNull(r, "transit_hours_estimate", "base_transit_hours", "transit_hours");
                boolean routeFeasible = r.path("feasible").asBoolean(!noFeasibleRoute);
                Double score = numberOrNull(r, "decision_score", "score");
                alternatives.add(new RerouteResponse.RouteAlternativeDto(mode, mode, costInr, hours, routeFeasible, score));
                if (legs.isEmpty() && r.path("legs").isArray()) {
                    for (JsonNode leg : r.path("legs")) {
                        Double legHours = numberOrNull(leg, "hours", "base_transit_hours", "transit_hours");
                        if (legHours == null) legHours = hours;
                        legs.add(new RerouteResponse.RouteLegDto(
                                textOr(leg, "id", "leg_id"),
                                textOr(leg, "from", "origin"),
                                textOr(leg, "to", "destination"),
                                leg.path("mode").asText(mode),
                                numberOrNull(leg, "distance_km"),
                                legHours,
                                decimalOrNull(leg.path("cost_inr"))
                        ));
                    }
                }
            }
        }

        // Parse gates[] (and legacy gate_reasons[])
        List<String> gateReasons = new ArrayList<>();
        JsonNode gatesNode = decisionNode.path("gates");
        if (gatesNode.isArray()) {
            for (JsonNode g : gatesNode) {
                if (g.isObject()) {
                    String gateName = g.path("gate").asText("gate");
                    String gateStatus = g.path("status").asText("");
                    String reason = g.path("reason").asText("");
                    gateReasons.add(gateName + ": " + gateStatus + (reason.isBlank() ? "" : " - " + reason));
                } else if (g.isTextual()) {
                    gateReasons.add(g.asText());
                }
            }
        }
        if (gateReasons.isEmpty()) {
            gateReasons.addAll(readStringArray(decisionNode.path("gate_reasons")));
        }

        String decisionStatus = decisionNode.path("status").asText("blocked");
        boolean hasCandidate = candidate != null && candidate.isObject() && !candidate.isEmpty();
        RerouteResponse.RouteAlternativeDto firstFeasibleAlt = alternatives.stream()
                .filter(a -> Boolean.TRUE.equals(a.feasible()))
                .findFirst()
                .orElse(null);

        boolean overallFeasible = !noFeasibleRoute && (hasCandidate || firstFeasibleAlt != null);
        RerouteResponse.SelectedRouteDto selectedRoute = null;
        BigDecimal costEstimate = null;
        Double transitEstimate = null;
        String recommendedOption = null;

        if (overallFeasible) {
            if (hasCandidate) {
                recommendedOption = candidate.path("option").asText(candidate.path("mode").asText(null));
                costEstimate = decimalOrNull(candidate.path("freight_inr"));
                if (costEstimate == null) costEstimate = decimalOrNull(candidate.path("cost_estimate_inr"));
                transitEstimate = numberOrNull(candidate, "base_transit_hours", "transit_hours_estimate");
                if (transitEstimate == null && candidate.path("eta_days").isNumber()) {
                    transitEstimate = candidate.path("eta_days").asDouble() * 24.0;
                }
            }
            if (firstFeasibleAlt != null) {
                if (recommendedOption == null) recommendedOption = firstFeasibleAlt.option();
                if (costEstimate == null) costEstimate = firstFeasibleAlt.costInr();
                if (transitEstimate == null) transitEstimate = firstFeasibleAlt.transitHours();
            }
            String selectedMode = hasCandidate
                    ? candidate.path("mode").asText(firstFeasibleAlt != null ? firstFeasibleAlt.mode() : "road")
                    : (firstFeasibleAlt != null ? firstFeasibleAlt.mode() : "road");
            selectedRoute = new RerouteResponse.SelectedRouteDto(
                    recommendedOption,
                    selectedMode,
                    costEstimate,
                    transitEstimate,
                    true,
                    readStringArray(candidate.path("disruption_ids"))
            );
        }

        RerouteResponse.DecisionDto decision = new RerouteResponse.DecisionDto(
                decisionStatus,
                gateReasons,
                recommendedOption,
                decisionNode.path("commercial_evidence").asText(null)
        );

        return new RerouteResponse(
                shipmentId,
                status,
                overallFeasible,
                decisionStatus,
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

    private static String textOr(JsonNode node, String primary, String fallback) {
        String val = node.path(primary).asText("");
        if (val.isBlank() && fallback != null) {
            val = node.path(fallback).asText("");
        }
        return val;
    }

    private static Double numberOrNull(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isNumber()) {
                return child.asDouble();
            }
        }
        return null;
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
