package com.deeptier.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_link")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SupplierLink {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String fromSupplierId;

    @Column(nullable = false, length = 64)
    private String toSupplierId;

    @Column(length = 32)
    private String hsCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LinkType linkType;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(precision = 19, scale = 2)
    private BigDecimal annualSpendInr;

    @Column(precision = 5, scale = 2)
    private BigDecimal costFraction; // e.g. 0.60 for castings

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private DataClass dataClass;

    @Column(length = 1000)
    private String evidenceIds;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
