package com.deeptier.approval;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_request")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "part_code", nullable = false, length = 64)
    private String partCode;

    @Column(name = "source_supplier_id", length = 64)
    private String sourceSupplierId;

    @Column(name = "target_supplier_id", nullable = false, length = 64)
    private String targetSupplierId;

    @Column(name = "recommended_qty", nullable = false)
    private Integer recommendedQty;

    @Column(name = "estimated_landed_cost", precision = 18, scale = 4)
    private BigDecimal estimatedLandedCost;

    @Column(name = "estimated_lead_time_days")
    private Integer estimatedLeadTimeDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 32)
    private ApprovalStatus approvalStatus;

    @Column(name = "requested_by", length = 64)
    private String requestedBy;

    @Column(name = "reviewer_name", length = 64)
    private String reviewerName;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "rfq_id", length = 64)
    private String rfqId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.approvalStatus == null) {
            this.approvalStatus = ApprovalStatus.CREATED;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
