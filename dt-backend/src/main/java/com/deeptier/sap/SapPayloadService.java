package com.deeptier.sap;

import com.deeptier.api.dto.SapPurchaseRequisitionPayload;
import com.deeptier.api.dto.SapPurchaseRequisitionPayload.*;
import com.deeptier.approval.*;
import com.deeptier.audit.AuditEvent;
import com.deeptier.repo.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Generates SAP S/4HANA Cloud Clean-Core OData payloads
 * for API_PURCHASEREQ_PROCESS_SRV (A_PurchaseRequisition / A_PurchaseReqnItem)
 * from an AUDITED MockRfq and its ApprovalRequest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SapPayloadService {

    private static final String SAP_SERVICE = "API_PURCHASEREQ_PROCESS_SRV";
    private static final String ENTITY_SET = "A_PurchaseRequisition";

    private final MockRfqRepository mockRfqRepo;
    private final ApprovalRequestRepository approvalRepo;
    private final AuditEventRepository auditRepo;

    @Transactional(readOnly = true)
    public SapPurchaseRequisitionPayload buildPurchaseRequisitionPayload(String rfqId, String idempotencyKey) {
        MockRfq rfq = mockRfqRepo.findById(rfqId)
                .orElseThrow(() -> new ResourceNotFoundException("MockRfq not found with id: " + rfqId));

        ApprovalRequest approval = approvalRepo.findById(rfq.getApprovalRequestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ApprovalRequest not found with id: " + rfq.getApprovalRequestId()));

        if (approval.getApprovalStatus() != ApprovalStatus.AUDITED) {
            throw new IllegalStateTransitionException(
                    "RFQ " + rfqId + " cannot be exported to SAP S/4HANA until approval is AUDITED (current status: "
                            + approval.getApprovalStatus() + ")");
        }

        String auditHash = auditRepo.findLatest()
                .map(AuditEvent::getAfterHash)
                .orElse("0");

        String safeIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : rfq.getId();

        String unitPrice = computeUnitPrice(rfq.getQuotedLandedCost(), rfq.getRequestedQty());
        int leadDays = rfq.getExpectedLeadTimeDays() != null ? rfq.getExpectedLeadTimeDays() : 6;
        Instant baseTime = rfq.getIssuedAt() != null ? rfq.getIssuedAt() : Instant.now();
        long deliveryEpochMillis = baseTime.plus(leadDays, ChronoUnit.DAYS).toEpochMilli();

        String reviewer = (approval.getReviewerName() != null && !approval.getReviewerName().isBlank())
                ? approval.getReviewerName()
                : "system";

        PurchaseReqnItem item = new PurchaseReqnItem(
                "Emergency lot - " + rfq.getPartCode(),
                rfq.getPartCode(),
                "1010",
                "101A",
                String.valueOf(rfq.getRequestedQty()),
                "EA",
                unitPrice,
                "1",
                "INR",
                "/Date(" + deliveryEpochMillis + ")/",
                rfq.getTargetSupplierId(),
                "U",
                "S",
                "APPROVED",
                "DeepTier HITL: " + reviewer
        );

        PurchaseRequisitionBody body = new PurchaseRequisitionBody(
                "NB",
                "DeepTier Emergency Recovery Lot: " + rfq.getPartCode(),
                new PurchaseReqnItemsWrapper(List.of(item))
        );

        CorrelationMeta correlation = new CorrelationMeta(
                rfq.getId(),
                approval.getId(),
                safeIdempotencyKey,
                auditHash
        );

        log.info("Generated SAP PurchaseRequisition payload for rfqId={}, approvalId={}, auditHash={}",
                rfq.getId(), approval.getId(), auditHash);

        return new SapPurchaseRequisitionPayload(SAP_SERVICE, ENTITY_SET, correlation, body);
    }

    private String computeUnitPrice(BigDecimal totalCost, Integer qty) {
        if (totalCost == null || qty == null || qty <= 0) {
            return "0.00";
        }
        return totalCost.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP).toPlainString();
    }
}
