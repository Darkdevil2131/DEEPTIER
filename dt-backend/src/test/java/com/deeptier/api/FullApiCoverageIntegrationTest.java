package com.deeptier.api;

import com.deeptier.config.DeepTierProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullApiCoverageIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DeepTierProperties props;

    @Test
    void exportsOpenApiAndVerifiesAllDiscoveredJavaEndpoints() throws Exception {
        // 1. Export & cross-check /v3/api-docs
        MvcResult docsResult = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        String openApiJson = docsResult.getResponse().getContentAsString();
        JsonNode paths = mapper.readTree(openApiJson).path("paths");

        List<String> expectedPaths = List.of(
                "/api/seed/reset",
                "/api/tier-map/nodes",
                "/api/tier-map/links",
                "/api/tier-map/graph",
                "/api/supply-chain/import/csv",
                "/api/whatif/simulate",
                "/api/agent/analyze-disruption",
                "/api/logistics/reroute",
                "/api/approvals",
                "/api/approvals/{id}",
                "/api/approvals/{id}/approve",
                "/api/approvals/{id}/reject",
                "/api/rfqs",
                "/api/rfqs/{id}/sap-payload",
                "/api/audit",
                "/api/audit/events",
                "/api/audit/events/scenario/{scenarioId}",
                "/api/audit/verify",
                "/internal/tools/compute_exposure",
                "/internal/tools/compute_cover",
                "/internal/tools/classify_signal",
                "/internal/tools/draft_rfq",
                "/internal/tools/get_tier_map"
        );
        for (String path : expectedPaths) {
            assertTrue(paths.has(path), "Missing from /v3/api-docs: " + path);
        }

        // Write machine-readable OpenAPI artifact to DEEPTIER/openapi/java-openapi.json
        Path openApiOut = Path.of("..", "openapi", "java-openapi.json");
        Files.createDirectories(openApiOut.getParent());
        Files.writeString(openApiOut, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(openApiJson)));

        // 2. Seed & Tier Map endpoints
        mvc.perform(post("/api/seed/reset?seed=20261121"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").value(5));

        mvc.perform(get("/api/tier-map/nodes")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/tier-map/links")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/tier-map/graph")).andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(5));

        // 3. What-If (verify Rank 1 is vetted supplier SUP-RJK-CAST + 0% / 12.5% decimal tariffs)
        MvcResult simRes = mvc.perform(post("/api/whatif/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hsCode\":\"7325\",\"deltaPercentagePoints\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tariffShock.totalExposureInr").exists())
                .andExpect(jsonPath("$.recoveryOptions[0].targetSupplierId").value("SUP-RJK-CAST"))
                .andExpect(jsonPath("$.recoveryOptions[0].recommended").value(true))
                .andReturn();
        String rank1Supplier = mapper.readTree(simRes.getResponse().getContentAsString())
                .path("recoveryOptions").get(0).path("targetSupplierId").asText();
        assertEquals("SUP-RJK-CAST", rank1Supplier);

        mvc.perform(post("/api/whatif/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hsCode\":\"7325\",\"deltaPercentagePoints\":12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tariffShock.deltaPercentagePoints").value(12.5));

        mvc.perform(post("/api/whatif/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hsCode\":\"7325\",\"deltaPercentagePoints\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tariffShock.totalExposureInr").value(0));

        // 4. Disruption Agent (deterministic fallback when Python offline)
        mvc.perform(post("/api/agent/analyze-disruption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertText\":\"20% tariff hike on HS 7325 castings from China with 5 days delay\",\"partCode\":\"HPH-1042\",\"scenarioId\":\"SCEN-E2E\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedHsCode").value("7325"))
                .andExpect(jsonPath("$.backendStatus").value("fallback"))
                .andExpect(jsonPath("$.simulation.tariffShock.totalExposureInr").exists());

        // Malformed JSON -> 400
        mvc.perform(post("/api/agent/analyze-disruption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));

        // 5. Rank-1 SUP-RJK-CAST -> Approval -> Approve -> RFQ -> SAP Payload
        MvcResult createRes = mvc.perform(post("/api/approvals")
                        .header("Idempotency-Key", "IDEMP-FULL-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partCode\":\"HPH-1042\",\"sourceSupplierId\":\"SUP-NGB-BETA\",\"targetSupplierId\":\"" + rank1Supplier + "\",\"recommendedQty\":300,\"estimatedLandedCost\":518000,\"estimatedLeadTimeDays\":4,\"scenarioId\":\"SCEN-E2E\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String approvalId = mapper.readTree(createRes.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(get("/api/approvals")).andExpect(status().isOk());
        mvc.perform(get("/api/approvals/" + approvalId)).andExpect(status().isOk());

        MvcResult approveRes = mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Idempotency-Key", "IDEMP-FULL-APP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerName\":\"Gaurav\",\"comment\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.approvalStatus").value("AUDITED"))
                .andReturn();
        String rfqId = mapper.readTree(approveRes.getResponse().getContentAsString()).path("rfq").path("id").asText();

        mvc.perform(get("/api/rfqs")).andExpect(status().isOk());
        mvc.perform(get("/api/rfqs/" + rfqId + "/sap-payload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sapService").value("API_PURCHASEREQ_PROCESS_SRV"))
                .andExpect(jsonPath("$.payload.PurchaseRequisitionType").value("NB"));
        mvc.perform(get("/api/rfqs/RFQ-MISSING/sap-payload")).andExpect(status().isNotFound());

        // 6. Audit endpoints + SHA-256 chain verify
        mvc.perform(get("/api/audit")).andExpect(status().isOk());
        mvc.perform(get("/api/audit/events")).andExpect(status().isOk());
        mvc.perform(get("/api/audit/events/scenario/SCEN-E2E")).andExpect(status().isOk());
        mvc.perform(get("/api/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // 7. Internal Tools endpoints (401 without key, 200 with key, 0% & 12.5% decimals, live tier map)
        String activeInternalKey = props.internal().key();
        mvc.perform(get("/internal/tools/get_tier_map")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/tools/get_tier_map").header("X-Internal-Key", activeInternalKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(5));
        mvc.perform(post("/internal/tools/compute_exposure")
                        .header("X-Internal-Key", activeInternalKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualSpendInr\":4800000,\"costFraction\":0.60,\"deltaPercentagePoints\":12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dutyExposureInr").value(360000.0));
        mvc.perform(post("/internal/tools/compute_exposure")
                        .header("X-Internal-Key", activeInternalKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualSpendInr\":4800000,\"costFraction\":0.60,\"deltaPercentagePoints\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dutyExposureInr").value(0));
        mvc.perform(post("/internal/tools/classify_signal")
                        .header("X-Internal-Key", activeInternalKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signalText\":\"20% tariff on HS 7325\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/tools/draft_rfq")
                        .header("X-Internal-Key", activeInternalKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierNodeId\":\"SUP-RJK-CAST\",\"quantity\":300,\"deliveryOption\":\"AIR\"}"))
                .andExpect(status().isOk());
    }
}
