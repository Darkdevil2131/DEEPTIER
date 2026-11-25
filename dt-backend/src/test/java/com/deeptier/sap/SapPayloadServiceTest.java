package com.deeptier.sap;

import com.deeptier.api.dto.ApprovalDecisionRequest;
import com.deeptier.api.dto.CreateApprovalRequest;
import com.deeptier.api.dto.SapPurchaseRequisitionPayload;
import com.deeptier.approval.*;
import com.deeptier.seed.SeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SapPayloadServiceTest {

    @Autowired SapPayloadService sapPayloadService;
    @Autowired ApprovalService approvalService;
    @Autowired ApprovalRequestRepository approvalRepo;
    @Autowired MockRfqRepository mockRfqRepo;
    @Autowired SeedGenerator seedGenerator;
    @Autowired MockMvc mvc;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
    }

    @Test
    @DisplayName("Audited RFQ produces S/4HANA API_PURCHASEREQ_PROCESS_SRV payload with auditHash and null-safe idempotencyKey")
    void auditedRfqProducesSapPayload() throws Exception {
        var created = approvalService.createApproval(new CreateApprovalRequest(
                "HPH-1042", "SUP-NGB-BETA", "SUP-RJK-CAST", 300,
                new BigDecimal("720000.0000"), 4, "user:planner"
        ));
        var decision = approvalService.approve(created.id(),
                new ApprovalDecisionRequest("Ravi (Owner)", "Approved emergency lot", null, null));
        String rfqId = decision.rfq().id();

        // Direct service check with null idempotencyKey -> falls back to rfqId
        SapPurchaseRequisitionPayload payload = sapPayloadService.buildPurchaseRequisitionPayload(rfqId, null);
        assertThat(payload.sapService()).isEqualTo("API_PURCHASEREQ_PROCESS_SRV");
        assertThat(payload.entitySet()).isEqualTo("A_PurchaseRequisition");
        assertThat(payload.correlation().deepTierRfqId()).isEqualTo(rfqId);
        assertThat(payload.correlation().idempotencyKey()).isEqualTo(rfqId);
        assertThat(payload.correlation().auditHash()).isNotBlank();
        assertThat(payload.payload().toPurchaseReqnItem().results()).hasSize(1);
        assertThat(payload.payload().toPurchaseReqnItem().results().get(0).purchaseRequisitionPrice()).isEqualTo("2400.00");

        // Controller GET /api/rfqs/{id}/sap-payload
        mvc.perform(get("/api/rfqs/" + rfqId + "/sap-payload")
                        .header("Idempotency-Key", "IDEM-SAP-TEST-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sapService").value("API_PURCHASEREQ_PROCESS_SRV"))
                .andExpect(jsonPath("$.correlation.idempotencyKey").value("IDEM-SAP-TEST-01"))
                .andExpect(jsonPath("$.payload.PurchaseRequisitionType").value("NB"))
                .andExpect(jsonPath("$.payload.to_PurchaseReqnItem.results[0].Material").value("HPH-1042"))
                .andExpect(jsonPath("$.payload.to_PurchaseReqnItem.results[0].FixedSupplier").value("SUP-RJK-CAST"));
    }

    @Test
    @DisplayName("Missing RFQ returns 404 Not Found")
    void missingRfqReturns404() throws Exception {
        mvc.perform(get("/api/rfqs/RFQ-DOES-NOT-EXIST/sap-payload"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("RFQ whose approval is not AUDITED returns 409 Conflict")
    void nonAuditedApprovalReturns409() throws Exception {
        ApprovalRequest pending = approvalRepo.save(ApprovalRequest.builder()
                .partCode("HPH-1042")
                .sourceSupplierId("SUP-NGB-BETA")
                .targetSupplierId("SUP-RJK-CAST")
                .recommendedQty(120)
                .estimatedLandedCost(new BigDecimal("300000.0000"))
                .estimatedLeadTimeDays(4)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .requestedBy("system")
                .createdAt(Instant.now())
                .build());

        MockRfq unsealedRfq = mockRfqRepo.save(MockRfq.builder()
                .id("RFQ-UNSEALED-" + UUID.randomUUID().toString().substring(0, 6))
                .approvalRequestId(pending.getId())
                .targetSupplierId("SUP-RJK-CAST")
                .partCode("HPH-1042")
                .requestedQty(120)
                .quotedLandedCost(new BigDecimal("300000.0000"))
                .expectedLeadTimeDays(4)
                .rfqStatus("DISPATCHED_MOCK")
                .issuedBy("system")
                .issuedAt(Instant.now())
                .build());

        mvc.perform(get("/api/rfqs/" + unsealedRfq.getId() + "/sap-payload"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }
}
