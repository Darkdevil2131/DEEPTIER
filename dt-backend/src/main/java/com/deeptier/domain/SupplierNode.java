package com.deeptier.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_node")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SupplierNode {

    @Id
    @Column(length = 64)
    private String id;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String supplierId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 64)
    private String country;

    @Column(nullable = false)
    private int tier; // 0=OEM, 1=Tier-1, 2=Tier-2...

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LinkType linkType;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

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
