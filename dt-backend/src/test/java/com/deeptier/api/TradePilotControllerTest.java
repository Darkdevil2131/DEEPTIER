package com.deeptier.api;

import com.deeptier.config.DeepTierProperties;
import com.deeptier.config.InternalKeyFilter;
import com.deeptier.sap.TradePilotClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradePilotControllerTest {
    private static final String ENDPOINT = "/internal/tools/tradepilot/shipments/analyze";
    private static final String INTERNAL_KEY = "java-test-internal";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void authorizationAndRequestShapeAreCheckedBeforeCallingTradePilot() throws Exception {
        var mvc = mvc("http://127.0.0.1:8000", "");
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/deeptier" + ENDPOINT).contextPath("/deeptier")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ENDPOINT).header("X-Internal-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        mvc.perform(request("{}")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void forwardsOnlyTheTradePilotKeyAndPreservesSafeGatewayErrors() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var observed = new LinkedBlockingQueue<String>();
        server.createContext("/api/v1/shipments/analyze", exchange -> {
            try (exchange) {
                var headers = exchange.getRequestHeaders();
                observed.add(headers.getFirst("Authorization") + ":" + headers.getFirst("X-Internal-Key"));
                int code = exchange.getRequestBody().readAllBytes().length == 2 ? 422 : 429;
                var body = "{\"detail\":\"sensitive upstream detail\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var mvc = mvc("http://127.0.0.1:" + server.getAddress().getPort(), "python-test-key");
            mvc.perform(request("{}")).andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.detail").value("TradePilot rejected the shipment. Check the request against the shipment schema."));
            mvc.perform(request("{\"origin\":\"Kochi\"}")).andExpect(status().isTooManyRequests());
            assertEquals("Bearer python-test-key:null", observed.remove());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRADEPILOT_TEST_URL", matches = ".+")
    void kochiShipmentReplansAfterClosureUsingTheRunningLangGraphService() throws Exception {
        var mvc = mvc(System.getenv("TRADEPILOT_TEST_URL"), System.getenv("TRADEPILOT_TEST_API_KEY"));
        var shipment = sample();
        var baseline = mvc.perform(request(shipment.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.agents.length()").value(6))
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.status").value("ready_for_human_approval"))
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.candidate.option").value("road-1/ocean"))
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.candidate.freight_inr").value(5555.3));
        for (String name : List.of("logistics_orchestrator", "route_optimization", "carrier_research",
                "risk_compliance", "document_intelligence", "market_procurement")) {
            baseline.andExpect(jsonPath("$.agents." + name + ".status").value("completed"));
        }

        var closure = shipment.putArray("disruptions").addObject().put("signal_id", "port-closure").put("severity", 1);
        closure.putArray("affected_modes").add("sea");
        closure.putArray("affected_locations").add("Chennai");
        mvc.perform(request(shipment.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.candidate.option").value("road-1/rail"))
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.candidate.freight_inr").value(5955.3));

        shipment.put("commercial_mode", "submitted");
        mvc.perform(request(shipment.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.commercial_evidence").value("unavailable"))
                .andExpect(jsonPath("$.agents.logistics_orchestrator.data.decision.candidate").isEmpty());

        shipment.put("weight_kg", -1);
        mvc.perform(request(shipment.toString())).andExpect(status().isUnprocessableEntity());
    }

    private static ObjectNode sample() throws IOException {
        var sample = (ObjectNode) MAPPER.readTree(Path.of("../AI/examples/intelligent_shipment.json").toFile());
        sample.put("shipment_id", "DEEPTIER-KOCHI-SMOKE");
        sample.put("origin", "Kochi");
        sample.put("destination", "Chennai");
        sample.put("weight_kg", 500);
        sample.putArray("allowed_modes").add("air").add("sea").add("road").add("rail");
        sample.putObject("impact_scenario").put("critical_weight_kg", 100)
                .put("target_days", 1.25).put("daily_stoppage_inr", 80000);
        return sample;
    }

    private static MockHttpServletRequestBuilder request(String body) {
        return post(ENDPOINT).header("X-Internal-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockMvc mvc(String url, String key) {
        var properties = new DeepTierProperties(null, new DeepTierProperties.InternalProps(INTERNAL_KEY), null, null);
        return MockMvcBuilders.standaloneSetup(new TradePilotController(new TradePilotClient(MAPPER, url, key, 15)))
                .addFilters(new InternalKeyFilter(properties)).build();
    }
}
