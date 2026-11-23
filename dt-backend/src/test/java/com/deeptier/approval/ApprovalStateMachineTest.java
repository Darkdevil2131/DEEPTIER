package com.deeptier.approval;

import com.deeptier.api.dto.ApprovalDecisionRequest;
import com.deeptier.api.dto.ApprovalDecisionResponse;
import com.deeptier.api.dto.ApprovalResponse;
import com.deeptier.api.dto.CreateApprovalRequest;
import com.deeptier.audit.AuditEvent;
import com.deeptier.audit.AuditService;
import com.deeptier.repo.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalStateMachineTest {

    @Mock
    private ApprovalRequestRepository approvalRepo;

    @Mock
    private MockRfqRepository mockRfqRepo;

    @Mock
    private AuditEventRepository auditRepo;

    private AuditService auditService;
    private ApprovalService approvalService;
    private ApprovalRequest sampleRequest;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditRepo);
        approvalService = new ApprovalService(approvalRepo, mockRfqRepo, auditService);

        sampleRequest = ApprovalRequest.builder()
                .id("REQ-TEST-100")
                .partCode("HPH-1042")
                .sourceSupplierId("SUP-BETA")
                .targetSupplierId("SUP-GAMMA")
                .recommendedQty(300)
                .estimatedLandedCost(BigDecimal.valueOf(720000))
                .estimatedLeadTimeDays(6)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .requestedBy("user:planner")
                .createdAt(Instant.now())
                .build();

        lenient().when(auditRepo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Creation transition: CREATED -> PENDING_APPROVAL and audit logged")
    void testCreateTransition() {
        when(approvalRepo.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateApprovalRequest req = new CreateApprovalRequest(
                "HPH-1042", "SUP-BETA", "SUP-GAMMA", 300, BigDecimal.valueOf(720000), 6, "user:planner"
        );

        ApprovalResponse res = approvalService.createApproval(req);

        assertThat(res.approvalStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(res.partCode()).isEqualTo("HPH-1042");
        verify(auditRepo).save(argThat(event -> "APPROVAL_CREATED".equals(event.getEventType())));
    }

    @Test
    @DisplayName("Approval sequence: PENDING_APPROVAL -> APPROVED -> RFQ_ISSUED -> AUDITED")
    void testApproveSequence() {
        when(approvalRepo.findById("REQ-TEST-100")).thenReturn(Optional.of(sampleRequest));
        when(mockRfqRepo.save(any(MockRfq.class))).thenAnswer(invocation -> {
            MockRfq rfq = invocation.getArgument(0);
            rfq.setId("RFQ-2026-TEST01");
            return rfq;
        });
        when(approvalRepo.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest("reviewer:ravi", "Proceed with air-lift", null, null);
        ApprovalDecisionResponse res = approvalService.approve("REQ-TEST-100", decision);

        assertThat(res.approval().approvalStatus()).isEqualTo(ApprovalStatus.AUDITED);
        assertThat(res.approval().reviewerName()).isEqualTo("reviewer:ravi");
        assertThat(res.rfq().id()).isEqualTo("RFQ-2026-TEST01");
        assertThat(res.rfq().rfqStatus()).isEqualTo("DISPATCHED_MOCK");

        // Verify audit event trail: APPROVAL_APPROVED, RFQ_ISSUED, APPROVAL_AUDITED
        verify(auditRepo).save(argThat(e -> "APPROVAL_APPROVED".equals(e.getEventType())));
        verify(auditRepo).save(argThat(e -> "RFQ_ISSUED".equals(e.getEventType())));
        verify(auditRepo).save(argThat(e -> "APPROVAL_AUDITED".equals(e.getEventType())));
    }

    @Test
    @DisplayName("Rejection sequence: PENDING_APPROVAL -> REJECTED -> AUDITED (no RFQ)")
    void testRejectSequence() {
        when(approvalRepo.findById("REQ-TEST-100")).thenReturn(Optional.of(sampleRequest));
        when(approvalRepo.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest("reviewer:ravi", "Reject option", null, null);
        ApprovalResponse res = approvalService.reject("REQ-TEST-100", decision);

        assertThat(res.approvalStatus()).isEqualTo(ApprovalStatus.AUDITED);
        assertThat(res.reviewerName()).isEqualTo("reviewer:ravi");
        assertThat(res.rfqId()).isNull();

        verifyNoInteractions(mockRfqRepo);
        verify(auditRepo).save(argThat(e -> "APPROVAL_REJECTED".equals(e.getEventType())));
        verify(auditRepo).save(argThat(e -> "APPROVAL_AUDITED".equals(e.getEventType())));
    }

    @Test
    @DisplayName("Illegal transition: approving request in AUDITED state throws IllegalStateTransitionException")
    void testIllegalTransitionThrowsException() {
        sampleRequest.setApprovalStatus(ApprovalStatus.AUDITED);
        when(approvalRepo.findById("REQ-TEST-100")).thenReturn(Optional.of(sampleRequest));

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest("reviewer:ravi", "Try re-approval", null, null);

        assertThatThrownBy(() -> approvalService.approve("REQ-TEST-100", decision))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot approve request in status: AUDITED");
    }

    @Test
    @DisplayName("Missing request throws ResourceNotFoundException")
    void testMissingRequestThrowsException() {
        when(approvalRepo.findById("NON-EXISTENT")).thenReturn(Optional.empty());

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest("reviewer:ravi", "Approve", null, null);

        assertThatThrownBy(() -> approvalService.approve("NON-EXISTENT", decision))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ApprovalRequest not found");
    }
}
