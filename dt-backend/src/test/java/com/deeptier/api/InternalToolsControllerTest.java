package com.deeptier.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalToolsControllerTest {

    @Autowired MockMvc mvc;

    private static final String KEY = "test-key";

    @Test
    void missingKeyReturns401() throws Exception {
        mvc.perform(post("/internal/tools/compute_exposure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void computeExposureHphScenario() throws Exception {
        // 0.60 * 4,800,000 * 20 / 100 = 576,000
        String body = """
                {
                  "annualSpendInr": 4800000,
                  "costFraction": 0.60,
                  "deltaPercentagePoints": 20,
                  "evidenceIds": ["EV-BOM-001"]
                }
                """;
        mvc.perform(post("/internal/tools/compute_exposure")
                        .header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dutyExposureInr").value(576000.0))
                .andExpect(jsonPath("$.dataClass").value("SYNTHETIC"))
                .andExpect(jsonPath("$.tool").value("compute_exposure"));
    }

    @Test
    void computeCoverZeroDemandReturnsUnknown() throws Exception {
        String body = """
                {"unitsOnHand": 300, "dailyDemand": 0, "leadTimeDays": 30}
                """;
        mvc.perform(post("/internal/tools/compute_cover")
                        .header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysOfCoverDisplay").value("UNKNOWN"))
                .andExpect(jsonPath("$.dataClass").value("ASSUMED"));
    }

    @Test
    void computeCoverHphScenario() throws Exception {
        // 300 / 20 = 15 days cover, 30 - 15 = 15 gap
        String body = """
                {"unitsOnHand": 300, "dailyDemand": 20, "leadTimeDays": 30}
                """;
        mvc.perform(post("/internal/tools/compute_cover")
                        .header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysOfCover").value(15.0))
                .andExpect(jsonPath("$.gapDays").value(15));
    }

    @Test
    void draftRfqNeverSentsExternally() throws Exception {
        String body = """
                {"supplierNodeId": "NODE-T2-BETA", "quantity": "500", "deliveryOption": "AIR"}
                """;
        mvc.perform(post("/internal/tools/draft_rfq")
                        .header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentExternally").value(false))
                .andExpect(jsonPath("$.requiresHumanApproval").value(true))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void getTierMapReturnsLiveSeededNodes() throws Exception {
        mvc.perform(get("/internal/tools/get_tier_map")
                        .header("X-Internal-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(5))
                .andExpect(jsonPath("$.links.length()").value(4));
    }
    @Test
    void invalidInputReturnsProblemDetail400() throws Exception {
        String body = """
                {"deltaPercentagePoints": 20}
                """;
        mvc.perform(post("/internal/tools/compute_exposure")
                        .header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }
}