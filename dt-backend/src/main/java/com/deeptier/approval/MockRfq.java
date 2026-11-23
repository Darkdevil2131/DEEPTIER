package com.deeptier.approval;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mock_rfq")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MockRfq {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "approval_request_id", nullable = false, unique = true, length = 64)
    private String approvalRequestId;

    @Column(name = "target_supplier_id", nullable = false, length = 64)
    private String targetSupplierId;

    @Column(name = "part_code", nullable = false, length = 64)
    private String partCode;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "quoted_landed_cost", precision = 18, scale = 4)
    private BigDecimal quotedLandedCost;

    @Column(name = "expected_lead_time_days")
    private Integer expectedLeadTimeDays;

    @Column(name = "rfq_status", nullable = false, length = 32)
    @Builder.Default
    private String rfqStatus = "DISPATCHED_MOCK";

    @Column(name = "issued_by", length = 64)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = "RFQ-2026-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.rfqStatus == null) {
            this.rfqStatus = "DISPATCHED_MOCK";
        }
        if (this.issuedAt == null) {
            this.issuedAt = Instant.now();
        }
    }
}
