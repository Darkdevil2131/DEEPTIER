package com.deeptier.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backup_supplier")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BackupSupplier {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "part_code", nullable = false, length = 64)
    private String partCode;

    @Column(name = "supplier_id", nullable = false, length = 64)
    private String supplierId;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Column(name = "country", nullable = false, length = 64)
    private String country;

    @Column(name = "hub_location", length = 128)
    private String hubLocation;

    @Column(name = "available_capacity", nullable = false)
    private Integer availableCapacity;

    @Column(name = "unit_cost_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCostInr;

    @Column(name = "standard_freight_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal standardFreightInr;

    @Column(name = "expedited_freight_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal expeditedFreightInr;

    @Column(name = "tariff_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal tariffRatePercent;

    @Column(name = "standard_lead_time_days", nullable = false)
    private Integer standardLeadTimeDays;

    @Column(name = "expedited_lead_time_days", nullable = false)
    private Integer expeditedLeadTimeDays;

    @Column(name = "quality_score", precision = 3, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "is_vetted", nullable = false)
    private Boolean isVetted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.isVetted == null) {
            this.isVetted = Boolean.TRUE;
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
