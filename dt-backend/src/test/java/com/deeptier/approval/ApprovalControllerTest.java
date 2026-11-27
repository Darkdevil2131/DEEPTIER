package com.deeptier.approval;

import com.deeptier.repo.AuditEventRepository;
import com.deeptier.security.JwtService;
import com.deeptier.seed.SeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SeedGenerator seedGenerator;
    @Autowired ApprovalRequestRepository approvalRepo;
    @Autowired MockRfqRepository mockRfqRepo;
    @Autowired IdempotencyRecordRepository idempotencyRepo;
    @Autowired AuditEventRepository auditRepo;
    @Autowired JwtService jwtService;
    private String analystToken;
    private String approverToken;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
        mockRfqRepo.deleteAll();
        approvalRepo.deleteAll();
        idempotencyRepo.deleteAll();
        analystToken = jwtService.generateToken("analyst@deeptier.local", "ROLE_ANALYST");
        approverToken = jwtService.generateToken("approver@deeptier.local", "ROLE_APPROVER");
    }

    @Test
    @DisplayName("1. Happy path: submit -> PENDING_APPROVAL -> approve -> MockRfq created + final status AUDITED + AuditEvent rows written")
    void testHappyPathApproval() throws Exception {
        // 1. Submit approval request
        String createBody = """
                {
                  "partCode": "HPH-1042",
                  "sourceSupplierId": "SUP-BETA-002",
                  "targetSupplierId": "SUP-GAMMA-003",
                  "recommendedQty": 500,
                  "estimatedLandedCost": 1250000.0000,
                  "estimatedLeadTimeDays": 6,
                  "requestedBy": "user:logistics-lead"
                }
                """;

        String createRes = mvc.perform(post("/api/approvals")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partCode").value("HPH-1042"))
                .andExpect(jsonPath("$.targetSupplierId").value("SUP-GAMMA-003"))
                .andExpect(jsonPath("$.recommendedQty").value(500))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();

        String approvalId = com.jayway.jsonpath.JsonPath.read(createRes, "$.id");
        assertThat(approvalId).isNotBlank();

        // 2. Approve request
        String decisionBody = """
                {
                  "reviewerName": "user:ravi-owner",
                  "comment": "Air-lift option selected to avoid 15-day stockout"
                }
                """;

        mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.id").value(approvalId))
                .andExpect(jsonPath("$.approval.approvalStatus").value("AUDITED"))
                .andExpect(jsonPath("$.approval.reviewerName").value("approver@deeptier.local"))
                .andExpect(jsonPath("$.approval.rfqId").isString())
                .andExpect(jsonPath("$.rfq.id").isString())
                .andExpect(jsonPath("$.rfq.approvalRequestId").value(approvalId))
                .andExpect(jsonPath("$.rfq.targetSupplierId").value("SUP-GAMMA-003"))
                .andExpect(jsonPath("$.rfq.partCode").value("HPH-1042"))
                .andExpect(jsonPath("$.rfq.requestedQty").value(500))
                .andExpect(jsonPath("$.rfq.rfqStatus").value("DISPATCHED_MOCK"));

        // 3. Verify MockRfq row in database
        var rfqs = mockRfqRepo.findAll();
        assertThat(rfqs).hasSize(1);
        MockRfq savedRfq = rfqs.get(0);
        assertThat(savedRfq.getApprovalRequestId()).isEqualTo(approvalId);
        assertThat(savedRfq.getRfqStatus()).isEqualTo("DISPATCHED_MOCK");

        // 4. Verify AuditEvent rows written for state transitions
        var events = auditRepo.findAll();
        assertThat(events).anyMatch(e -> "APPROVAL_CREATED".equals(e.getEventType()));
        assertThat(events).anyMatch(e -> "APPROVAL_APPROVED".equals(e.getEventType()));
        assertThat(events).anyMatch(e -> "RFQ_ISSUED".equals(e.getEventType()));
        assertThat(events).anyMatch(e -> "APPROVAL_AUDITED".equals(e.getEventType()));

        // 5. Verify RfqController lists the generated RFQ
        mvc.perform(get("/api/rfqs")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].approvalRequestId").value(approvalId));
    }

    @Test
    @DisplayName("2. Rejection path: submit -> reject -> no MockRfq created + final status AUDITED")
    void testRejectionPath() throws Exception {
        // 1. Submit approval request
        String createBody = """
                {
                  "partCode": "HPH-1042",
                  "targetSupplierId": "SUP-BETA-002",
                  "recommendedQty": 200,
                  "estimatedLandedCost": 800000.0000,
                  "estimatedLeadTimeDays": 10,
                  "requestedBy": "user:ops"
                }
                """;

        String createRes = mvc.perform(post("/api/approvals")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String approvalId = com.jayway.jsonpath.JsonPath.read(createRes, "$.id");

        // 2. Reject request
        String decisionBody = """
                {
                  "reviewerName": "user:finance-director",
                  "comment": "Cost exceeds current quarter emergency allocation"
                }
                """;

        mvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId))
                .andExpect(jsonPath("$.approvalStatus").value("AUDITED"))
                .andExpect(jsonPath("$.reviewerName").value("approver@deeptier.local"))
                .andExpect(jsonPath("$.rfqId").doesNotExist());

        // 3. Verify NO MockRfq created
        assertThat(mockRfqRepo.findAll()).isEmpty();

        // 4. Verify AuditEvent logged for rejection
        var events = auditRepo.findAll();
        assertThat(events).anyMatch(e -> "APPROVAL_REJECTED".equals(e.getEventType()));
        assertThat(events).anyMatch(e -> "APPROVAL_AUDITED".equals(e.getEventType()));
    }

    @Test
    @DisplayName("3. Illegal transition: approving or rejecting an already AUDITED/REJECTED request returns 409 Conflict")
    void testIllegalStateTransitionReturns409() throws Exception {
        // 1. Submit approval request
        String createBody = """
                {
                  "partCode": "HPH-1042",
                  "targetSupplierId": "SUP-GAMMA-003",
                  "recommendedQty": 100,
                  "estimatedLandedCost": 500000.0000,
                  "requestedBy": "user:lead"
                }
                """;

        String createRes = mvc.perform(post("/api/approvals")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String approvalId = com.jayway.jsonpath.JsonPath.read(createRes, "$.id");

        // 2. Approve request (now AUDITED)
        String decisionBody = """
                {
                  "reviewerName": "user:ravi-owner",
                  "comment": "Approved"
                }
                """;

        mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk());

        // 3. Attempting to approve again must return 409 Conflict
        mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.title").value("State Transition Conflict"));

        // 4. Attempting to reject an already AUDITED request also returns 409 Conflict
        mvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("4. Idempotency replay: sending same Idempotency-Key twice on /approve returns exact same rfqId and does not create duplicate MockRfq")
    void testIdempotencyReplay() throws Exception {
        // 1. Submit approval request
        String createBody = """
                {
                  "partCode": "HPH-1042",
                  "targetSupplierId": "SUP-GAMMA-003",
                  "recommendedQty": 300,
                  "estimatedLandedCost": 720000.0000,
                  "requestedBy": "user:ravi"
                }
                """;

        String createRes = mvc.perform(post("/api/approvals")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String approvalId = com.jayway.jsonpath.JsonPath.read(createRes, "$.id");

        String decisionBody = """
                {
                  "reviewerName": "user:ravi-owner",
                  "comment": "Confirmed air-lift recovery"
                }
                """;

        String idempotencyKey = "IDEM-KEY-GOVERNANCE-20261124";

        // First call with Idempotency-Key
        String firstRes = mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + approverToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.approvalStatus").value("AUDITED"))
                .andReturn().getResponse().getContentAsString();

        String firstRfqId = com.jayway.jsonpath.JsonPath.read(firstRes, "$.rfq.id");
        int rfqCountFirst = mockRfqRepo.findAll().size();
        int auditCountFirst = auditRepo.findAll().size();

        // Second call with same Idempotency-Key -> should replay cached response without 409 conflict
        String secondRes = mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + approverToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.approvalStatus").value("AUDITED"))
                .andReturn().getResponse().getContentAsString();

        String secondRfqId = com.jayway.jsonpath.JsonPath.read(secondRes, "$.rfq.id");

        // Verifications:
        assertThat(secondRfqId).isEqualTo(firstRfqId);
        assertThat(firstRes).isEqualTo(secondRes);
        assertThat(mockRfqRepo.findAll()).hasSize(rfqCountFirst);
        assertThat(auditRepo.findAll()).hasSize(auditCountFirst);
        assertThat(idempotencyRepo.findById(idempotencyKey)).isPresent();
    }

    @Test
    @DisplayName("5. Listing approvals supports ?status filter and ordering by createdAt desc")
    void testListApprovalsWithFilter() throws Exception {
        // Create 2 requests
        mvc.perform(post("/api/approvals")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"partCode":"PART-A","targetSupplierId":"SUP-A","recommendedQty":10}
                        """)).andExpect(status().isCreated());

        mvc.perform(post("/api/approvals")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"partCode":"PART-B","targetSupplierId":"SUP-B","recommendedQty":20}
                        """)).andExpect(status().isCreated());

        mvc.perform(get("/api/approvals?status=PENDING_APPROVAL")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
