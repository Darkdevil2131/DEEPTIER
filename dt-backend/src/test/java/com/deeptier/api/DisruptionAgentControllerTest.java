package com.deeptier.api;

import com.deeptier.api.dto.AnalyzeDisruptionRequest;
import com.deeptier.api.dto.AnalyzeDisruptionResponse;
import com.deeptier.engine.DisruptionAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisruptionAgentControllerTest {

    @Test
    void analyzeDisruptionReturnsTypedResponseAndValidatesBlankAlert() throws Exception {
        DisruptionAgentService stubService = new DisruptionAgentService(null, null, null, null) {
            @Override
            public AnalyzeDisruptionResponse analyzeDisruption(AnalyzeDisruptionRequest request) {
                return new AnalyzeDisruptionResponse(
                        "EV-1", "SCEN-1", "HPH-1042", "7325", "CN",
                        BigDecimal.valueOf(20), 5, "TARIFF_SHOCK",
                        BigDecimal.valueOf(0.85), "JAVA_DETERMINISTIC_FALLBACK",
                        "Grounded brief", List.of("EV-1"), null, Instant.now());
            }
        };

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DisruptionAgentController(stubService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/agent/analyze-disruption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertText\":\"20% tariff on HS 7325\",\"partCode\":\"HPH-1042\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("EV-1"))
                .andExpect(jsonPath("$.affectedHsCode").value("7325"))
                .andExpect(jsonPath("$.extractionEngine").value("JAVA_DETERMINISTIC_FALLBACK"));

        mvc.perform(post("/api/agent/analyze-disruption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertText\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
