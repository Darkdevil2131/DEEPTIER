package com.deeptier.api.dto;

import com.deeptier.approval.ApprovalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ApprovalResponse(
        String id,
        String partCode,
        String sourceSupplierId,
        String targetSupplierId,
        Integer recommendedQty,
        BigDecimal estimatedLandedCost,
        Integer estimatedLeadTimeDays,
        ApprovalStatus approvalStatus,
        String requestedBy,
        String reviewerName,
        String reviewComment,
        String rfqId,
        Instant createdAt,
        Instant updatedAt
) {}
