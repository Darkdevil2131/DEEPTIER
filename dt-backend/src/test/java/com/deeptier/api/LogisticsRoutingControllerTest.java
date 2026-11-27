package com.deeptier.api;

import com.deeptier.api.dto.RerouteRequest;
import com.deeptier.api.dto.RerouteResponse;
import com.deeptier.sap.AgentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogisticsRoutingControllerTest {

    @Test
    void rerouteReturns57HubResponseAndHandlesOffline503AndValidation400() throws Exception {
        AgentGateway onlineGateway = new AgentGateway() {
            @Override
            public RerouteResponse rerouteShipment(RerouteRequest request) {
                return new RerouteResponse(
                        "SHIP-57HUB",
                        "completed",
                        new RerouteResponse.SelectedRouteDto("road-1/rail", "rail", BigDecimal.valueOf(5955.3), 48.0, true, List.of()),
                        List.of(new RerouteResponse.RouteLegDto("IN-ROAD-KOCHI-CBE", "Kochi", "Coimbatore", "road", 190.0, 4.0, BigDecimal.valueOf(1200))),
                        BigDecimal.valueOf(5955.3),
                        48.0,
                        List.of(new RerouteResponse.RouteAlternativeDto("road-1/rail", "rail", BigDecimal.valueOf(5955.3), 48.0, true, 0.91)),
                        new RerouteResponse.DecisionDto("ready_for_human_approval", List.of(), "road-1/rail", "submitted"),
                        List.of("SYNTHETIC"),
                        List.of()
                );
            }
            @Override public String classifySignal(String signalText, String hsCode) { return "{}"; }
            @Override public String explainResults(String whatIfResultJson) { return ""; }
            @Override public String draftRfq(String context) { return ""; }
            @Override public String runtimeName() { return "python"; }
        };

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LogisticsRoutingController(onlineGateway))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/logistics/reroute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"Kochi\",\"destination\":\"Chennai\",\"cargoDescription\":\"Castings\",\"weightKg\":500,\"allowedModes\":[\"road\",\"rail\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value("SHIP-57HUB"))
                .andExpect(jsonPath("$.selectedRoute.option").value("road-1/rail"))
                .andExpect(jsonPath("$.legs[0].legId").value("IN-ROAD-KOCHI-CBE"))
                .andExpect(jsonPath("$.costEstimateInr").value(5955.3));

        // Validation 400 on blank origin
        mvc.perform(post("/api/logistics/reroute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"\",\"destination\":\"Chennai\",\"cargoDescription\":\"Castings\",\"weightKg\":500,\"allowedModes\":[\"road\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Offline 503 ProblemDetail
        AgentGateway offlineGateway = new AgentGateway() {
            @Override
            public RerouteResponse rerouteShipment(RerouteRequest request) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TradePilot 57-hub routing service is offline.");
            }
            @Override public String classifySignal(String signalText, String hsCode) { return "{}"; }
            @Override public String explainResults(String whatIfResultJson) { return ""; }
            @Override public String draftRfq(String context) { return ""; }
            @Override public String runtimeName() { return "python"; }
        };

        MockMvc offlineMvc = MockMvcBuilders.standaloneSetup(new LogisticsRoutingController(offlineGateway))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        offlineMvc.perform(post("/api/logistics/reroute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"Kochi\",\"destination\":\"Chennai\",\"cargoDescription\":\"Castings\",\"weightKg\":500,\"allowedModes\":[\"road\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_ERROR"));
    }
}
