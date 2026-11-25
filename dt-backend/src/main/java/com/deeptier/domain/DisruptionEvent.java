package com.deeptier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted supply chain disruption analysis event.
 * Dual-DB safe for PostgreSQL and SAP HANA Cloud (no @Data, no reserved keywords, explicit column lengths).
 */
@Entity
@Table(name = "disruption_event")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DisruptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;

    @Column(name = "scenario_id", nullable = false, length = 64)
    private String scenarioId;

    @Column(name = "part_code", nullable = false, length = 64)
    private String partCode;

    @Column(name = "alert_text", nullable = false, length = 2000)
    private String alertText;

    @Column(name = "affected_hs_code", nullable = false, length = 32)
    private String affectedHsCode;

    @Column(name = "origin_country", nullable = false, length = 16)
    private String originCountry;

    @Column(name = "tariff_delta_percent", nullable = false, precision = 10, scale = 2)
    private BigDecimal tariffDeltaPercent;

    @Column(name = "delay_days", nullable = false)
    private Integer delayDays;

    @Column(name = "signal_type", nullable = false, length = 64)
    private String signalType;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "extraction_engine", nullable = false, length = 64)
    private String extractionEngine;

    @Column(name = "executive_brief", nullable = false, length = 4000)
    private String executiveBrief;

    @Column(name = "evidence_ids", length = 1000)
    private String evidenceIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
