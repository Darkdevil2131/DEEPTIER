package com.deeptier.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable hash-chained audit log entry.
 * beforeHash + payload -> SHA-256 -> afterHash stored in event.
 * Compatible with both PostgreSQL and SAP HANA (standard column lengths).
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditEvent {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 64)
    private String scenarioId;

    @Column(nullable = false, length = 64)
    private String eventType; // e.g. SEED_RESET, APPROVAL_REQUESTED, APPROVED, RFQ_CREATED

    @Column(nullable = false, length = 64)
    private String actor; // system | user:<id>

    @Column(length = 64)
    private String entityType;

    @Column(length = 64)
    private String entityId;

    @Column(length = 4000)
    private String payload; // JSON payload

    @Column(nullable = false, length = 64)
    private String beforeHash; // hash of previous event (genesis = "0")

    @Column(nullable = false, length = 64)
    private String afterHash; // SHA-256 of (beforeHash + payload)

    @Column(nullable = false)
    private Instant occurredAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now();
        }
    }

    // Backward-compatibility aliases
    public String getPrevHash() { return beforeHash; }
    public String getHash() { return afterHash; }
    public void setPrevHash(String p) { this.beforeHash = p; }
    public void setHash(String h) { this.afterHash = h; }
}
