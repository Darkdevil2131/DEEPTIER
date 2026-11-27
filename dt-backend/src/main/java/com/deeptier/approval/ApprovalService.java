package com.deeptier.approval;

import com.deeptier.api.dto.*;
import com.deeptier.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepo;
    private final MockRfqRepository mockRfqRepo;
    private final AuditService auditService;

    @Transactional
    public ApprovalResponse createApproval(CreateApprovalRequest req) {
        String requestedBy = resolveAuthenticatedUser()
                .orElse(req.requestedBy() != null && !req.requestedBy().isBlank() ? req.requestedBy() : "system");

        ApprovalRequest request = ApprovalRequest.builder()
                .partCode(req.partCode())
                .sourceSupplierId(req.sourceSupplierId())
                .targetSupplierId(req.targetSupplierId())
                .recommendedQty(req.recommendedQty())
                .estimatedLandedCost(req.estimatedLandedCost())
                .estimatedLeadTimeDays(req.estimatedLeadTimeDays())
                .requestedBy(requestedBy)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .createdAt(Instant.now())
                .build();

        ApprovalRequest saved = approvalRepo.save(request);

        auditService.logEvent(
                req.scenarioId(),
                "APPROVAL_CREATED",
                saved.getRequestedBy(),
                "ApprovalRequest",
                saved.getId(),
                "Created approval request for part " + saved.getPartCode() + " -> status PENDING_APPROVAL"
        );

        log.info("Approval request created: id={}, partCode={}, status={}", saved.getId(), saved.getPartCode(), saved.getApprovalStatus());
        return toDto(saved);
    }

    @Transactional
    public ApprovalDecisionResponse approve(String id, ApprovalDecisionRequest req) {
        ApprovalRequest request = approvalRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest not found with id: " + id));

        if (request.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateTransitionException(
                    "Cannot approve request in status: " + request.getApprovalStatus() + ". Request must be in PENDING_APPROVAL state."
            );
        }

        Optional<String> authenticatedUser = resolveAuthenticatedUser();
        String actor = authenticatedUser.orElse(req != null ? req.effectiveReviewerName() : "system");
        if (authenticatedUser.isPresent()
                && request.getRequestedBy() != null
                && actor.equalsIgnoreCase(request.getRequestedBy())) {
            throw new AccessDeniedException("Self-approval is not permitted: approver cannot be the same user who created the request");
        }
        String comment = req != null ? req.effectiveComment() : "Approved";

        request.setReviewerName(actor);
        request.setReviewComment(comment);

        // 1. PENDING_APPROVAL -> APPROVED
        request.setApprovalStatus(ApprovalStatus.APPROVED);
        auditService.logEvent(
                null,
                "APPROVAL_APPROVED",
                actor,
                "ApprovalRequest",
                id,
                "Approved by " + actor + (comment != null ? ": " + comment : "")
        );

        // 2. Generate MockRfq -> RFQ_ISSUED
        MockRfq mockRfq = MockRfq.builder()
                .approvalRequestId(id)
                .targetSupplierId(request.getTargetSupplierId())
                .partCode(request.getPartCode())
                .requestedQty(request.getRecommendedQty())
                .quotedLandedCost(request.getEstimatedLandedCost())
                .expectedLeadTimeDays(request.getEstimatedLeadTimeDays())
                .rfqStatus("DISPATCHED_MOCK")
                .issuedBy(actor)
                .issuedAt(Instant.now())
                .build();

        mockRfq = mockRfqRepo.save(mockRfq);
        request.setRfqId(mockRfq.getId());
        request.setApprovalStatus(ApprovalStatus.RFQ_ISSUED);

        auditService.logEvent(
                null,
                "RFQ_ISSUED",
                actor,
                "MockRfq",
                mockRfq.getId(),
                "Mock RFQ generated: " + mockRfq.getId() + " for part " + mockRfq.getPartCode()
        );

        // 3. RFQ_ISSUED -> AUDITED
        request.setApprovalStatus(ApprovalStatus.AUDITED);
        auditService.logEvent(
                null,
                "APPROVAL_AUDITED",
                actor,
                "ApprovalRequest",
                id,
                "Governance approval sealed and audited"
        );

        ApprovalRequest updated = approvalRepo.save(request);
        log.info("Approval finalized: id={}, rfqId={}, finalStatus={}", id, mockRfq.getId(), updated.getApprovalStatus());

        return new ApprovalDecisionResponse(toDto(updated), toRfqDto(mockRfq));
    }

    @Transactional
    public ApprovalResponse reject(String id, ApprovalDecisionRequest req) {
        ApprovalRequest request = approvalRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest not found with id: " + id));

        if (request.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateTransitionException(
                    "Cannot reject request in status: " + request.getApprovalStatus() + ". Request must be in PENDING_APPROVAL state."
            );
        }

        String actor = resolveAuthenticatedUser().orElse(req != null ? req.effectiveReviewerName() : "system");
        String comment = req != null ? req.effectiveComment() : "Rejected";

        request.setReviewerName(actor);
        request.setReviewComment(comment);

        // 1. PENDING_APPROVAL -> REJECTED
        request.setApprovalStatus(ApprovalStatus.REJECTED);
        auditService.logEvent(
                null,
                "APPROVAL_REJECTED",
                actor,
                "ApprovalRequest",
                id,
                "Rejected by " + actor + (comment != null ? ": " + comment : "")
        );

        // 2. REJECTED -> AUDITED (no MockRfq generated)
        request.setApprovalStatus(ApprovalStatus.AUDITED);
        auditService.logEvent(
                null,
                "APPROVAL_AUDITED",
                actor,
                "ApprovalRequest",
                id,
                "Governance rejection sealed and audited"
        );

        ApprovalRequest updated = approvalRepo.save(request);
        log.info("Rejection finalized: id={}, finalStatus={}", id, updated.getApprovalStatus());

        return toDto(updated);
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> listApprovals(ApprovalStatus status) {
        List<ApprovalRequest> requests = (status != null)
                ? approvalRepo.findByApprovalStatusOrderByCreatedAtDesc(status)
                : approvalRepo.findAllByOrderByCreatedAtDesc();
        return requests.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalResponse getApproval(String id) {
        return approvalRepo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest not found with id: " + id));
    }

    private Optional<String> resolveAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return Optional.ofNullable(auth.getName());
        }
        return Optional.empty();
    }

    public ApprovalResponse toDto(ApprovalRequest a) {
        return new ApprovalResponse(
                a.getId(),
                a.getPartCode(),
                a.getSourceSupplierId(),
                a.getTargetSupplierId(),
                a.getRecommendedQty(),
                a.getEstimatedLandedCost(),
                a.getEstimatedLeadTimeDays(),
                a.getApprovalStatus(),
                a.getRequestedBy(),
                a.getReviewerName(),
                a.getReviewComment(),
                a.getRfqId(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }

    public MockRfqResponse toRfqDto(MockRfq r) {
        return new MockRfqResponse(
                r.getId(),
                r.getApprovalRequestId(),
                r.getTargetSupplierId(),
                r.getPartCode(),
                r.getRequestedQty(),
                r.getQuotedLandedCost(),
                r.getExpectedLeadTimeDays(),
                r.getRfqStatus(),
                r.getIssuedBy(),
                r.getIssuedAt()
        );
    }
}
