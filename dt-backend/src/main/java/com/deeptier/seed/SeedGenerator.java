package com.deeptier.seed;

import com.deeptier.approval.ApprovalRequestRepository;
import com.deeptier.approval.IdempotencyRecordRepository;
import com.deeptier.approval.MockRfqRepository;
import com.deeptier.audit.AuditEvent;
import com.deeptier.domain.*;
import com.deeptier.repo.AuditEventRepository;
import com.deeptier.repo.BackupSupplierRepository;
import com.deeptier.repo.DisruptionEventRepository;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Loads the deterministic "Hydraulic Pump Housing" demo scenario.
 *
 * Worked scenario:
 *   Product:   HPH-1042  HS 7325
 *   OEM:       Pune / Pimpri-Chinchwad (SUP-PUNE-OEM)
 *   Tier-1:    Coimbatore - Alpha Machining (SUP-CBE-ALPHA) DECLARED confidence=0.90 spend=4,800,000 INR
 *   Tier-2:    Ningbo - Beta Foundry (SUP-NGB-BETA, CN) INFERRED confidence=0.60 castings=60% of spend
 *   Tier-2:    Rajkot - Rajkot Foundry Cluster (SUP-RJK-CAST, IN) DECLARED backup
 *   Tier-3:    Rotterdam - Rotterdam Specialty Metallurgy (SUP-RTM-GAMMA, NL) INFERRED
 *   Tariff:    +20pp on HS 7325 from CN
 *   Exposure:  0.60 * 4,800,000 * 0.20 = 576,000 INR
 *   Inventory: 300 units, demand=20/day -> 15 days cover, lead=30d -> 15d gap
 *   Delay cost: 80,000 INR/day (ASSUMED)
 *
 * Zero reflection hacks. Uses standard Lombok Builders.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedGenerator {

    // Canonical IDs - stable across resets so tests can reference them
    public static final String NODE_OEM            = "NODE-OEM-HPH";
    public static final String NODE_ALPHA          = "NODE-T1-ALPHA";
    public static final String NODE_BETA           = "NODE-T2-BETA";
    public static final String NODE_RAJKOT         = "NODE-T2-RAJKOT";
    public static final String NODE_ROTTERDAM      = "NODE-T3-ROTTERDAM";

    public static final String LINK_OEM_ALPHA      = "LINK-OEM-ALPHA";
    public static final String LINK_ALPHA_BETA     = "LINK-ALPHA-BETA";
    public static final String LINK_ALPHA_RAJKOT   = "LINK-ALPHA-RAJKOT";
    public static final String LINK_BETA_ROTTERDAM = "LINK-BETA-ROTTERDAM";

    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;
    private final BackupSupplierRepository backupSupplierRepo;
    private final AuditEventRepository auditRepo;
    private final MockRfqRepository mockRfqRepo;
    private final ApprovalRequestRepository approvalRepo;
    private final DisruptionEventRepository disruptionEventRepo;
    private final IdempotencyRecordRepository idempotencyRepo;

    @Transactional
    public void reset(long seed) {
        log.info("SeedGenerator.reset() - clearing all data, seed={}", seed);

        // 1. Wipe in FK-safe order
        auditRepo.deleteAll();
        idempotencyRepo.deleteAll();
        disruptionEventRepo.deleteAll();
        mockRfqRepo.deleteAll();
        approvalRepo.deleteAll();
        backupSupplierRepo.deleteAll();
        linkRepo.deleteAll();
        nodeRepo.deleteAll();

        Instant now = Instant.now();

        // 2. OEM node - Pune / Pimpri-Chinchwad
        SupplierNode oem = SupplierNode.builder()
                .id(NODE_OEM)
                .supplierId("SUP-PUNE-OEM")
                .name("Pune Hydraulic OEM (HPH-1042)")
                .country("IN")
                .tier(0)
                .linkType(LinkType.DECLARED)
                .confidence(dec("1.00"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-OEM-001")
                .createdAt(now)
                .build();
        nodeRepo.save(oem);

        // 3. Tier-1 Alpha Machining - Coimbatore
        SupplierNode alpha = SupplierNode.builder()
                .id(NODE_ALPHA)
                .supplierId("SUP-CBE-ALPHA")
                .name("Alpha Machining (Coimbatore)")
                .country("IN")
                .tier(1)
                .linkType(LinkType.DECLARED)
                .confidence(dec("0.90"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-BOM-001,EV-PO-2024-001")
                .createdAt(now)
                .build();
        nodeRepo.save(alpha);

        // 4. Tier-2 Beta Foundry - Ningbo (CN, HS 7325)
        SupplierNode beta = SupplierNode.builder()
                .id(NODE_BETA)
                .supplierId("SUP-NGB-BETA")
                .name("Beta Foundry (Ningbo)")
                .country("CN")
                .tier(2)
                .linkType(LinkType.INFERRED)
                .confidence(dec("0.60"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-TRADE-2024-001,EV-ML-INF-001")
                .createdAt(now)
                .build();
        nodeRepo.save(beta);

        // 5. Tier-2 Domestic Backup - Rajkot (IN)
        SupplierNode rajkot = SupplierNode.builder()
                .id(NODE_RAJKOT)
                .supplierId("SUP-RJK-CAST")
                .name("Rajkot Foundry Cluster")
                .country("IN")
                .tier(2)
                .linkType(LinkType.DECLARED)
                .confidence(dec("0.90"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-QUAL-RJK-001")
                .createdAt(now)
                .build();
        nodeRepo.save(rajkot);

        // 6. Tier-3 European Metallurgy - Rotterdam (NL)
        SupplierNode rotterdam = SupplierNode.builder()
                .id(NODE_ROTTERDAM)
                .supplierId("SUP-RTM-GAMMA")
                .name("Rotterdam Specialty Metallurgy")
                .country("NL")
                .tier(3)
                .linkType(LinkType.INFERRED)
                .confidence(dec("0.55"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-TRADE-RTM-002")
                .createdAt(now)
                .build();
        nodeRepo.save(rotterdam);

        // 7. Links (HPH-1042 CN/7325 exposure remains strictly 0.60 * 4,800,000 * 0.20 = 576,000 INR)
        SupplierLink oemToAlpha = SupplierLink.builder()
                .id(LINK_OEM_ALPHA)
                .fromSupplierId(NODE_OEM)
                .toSupplierId(NODE_ALPHA)
                .hsCode("7325")
                .linkType(LinkType.DECLARED)
                .confidence(dec("0.90"))
                .annualSpendInr(new BigDecimal("4800000"))
                .costFraction(dec("1.00"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-BOM-001")
                .createdAt(now)
                .build();
        linkRepo.save(oemToAlpha);

        SupplierLink alphaToBeta = SupplierLink.builder()
                .id(LINK_ALPHA_BETA)
                .fromSupplierId(NODE_ALPHA)
                .toSupplierId(NODE_BETA)
                .hsCode("7325")
                .linkType(LinkType.INFERRED)
                .confidence(dec("0.60"))
                .annualSpendInr(new BigDecimal("4800000"))
                .costFraction(dec("0.60"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-TRADE-2024-001")
                .createdAt(now)
                .build();
        linkRepo.save(alphaToBeta);

        SupplierLink alphaToRajkot = SupplierLink.builder()
                .id(LINK_ALPHA_RAJKOT)
                .fromSupplierId(NODE_ALPHA)
                .toSupplierId(NODE_RAJKOT)
                .hsCode("7325")
                .linkType(LinkType.DECLARED)
                .confidence(dec("0.90"))
                .annualSpendInr(new BigDecimal("1200000"))
                .costFraction(dec("0.25"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-QUAL-RJK-001")
                .createdAt(now)
                .build();
        linkRepo.save(alphaToRajkot);

        SupplierLink betaToRotterdam = SupplierLink.builder()
                .id(LINK_BETA_ROTTERDAM)
                .fromSupplierId(NODE_BETA)
                .toSupplierId(NODE_ROTTERDAM)
                .hsCode("8481")
                .linkType(LinkType.INFERRED)
                .confidence(dec("0.55"))
                .annualSpendInr(new BigDecimal("950000"))
                .costFraction(dec("0.35"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-TRADE-RTM-002")
                .createdAt(now)
                .build();
        linkRepo.save(betaToRotterdam);

        // 8. Vetted Backup Supplier Catalog (4 pre-qualified alternatives for HPH-1042)
        List<BackupSupplier> backups = List.of(
                BackupSupplier.builder()
                        .id("BKP-RJK-001")
                        .partCode("HPH-1042")
                        .supplierId("SUP-RJK-CAST")
                        .supplierName("Rajkot Foundry Cluster")
                        .country("IN")
                        .hubLocation("Rajkot, Gujarat")
                        .availableCapacity(500)
                        .unitCostInr(dec("2250.0000"))
                        .standardFreightInr(dec("45000.0000"))
                        .expeditedFreightInr(dec("180000.0000"))
                        .tariffRatePercent(dec("0.00"))
                        .standardLeadTimeDays(8)
                        .expeditedLeadTimeDays(4)
                        .qualityScore(dec("0.94"))
                        .isVetted(Boolean.TRUE)
                        .createdAt(now)
                        .build(),
                BackupSupplier.builder()
                        .id("BKP-CBE-002")
                        .partCode("HPH-1042")
                        .supplierId("SUP-CBE-DIRECT")
                        .supplierName("Coimbatore Direct Castings")
                        .country("IN")
                        .hubLocation("Coimbatore, Tamil Nadu")
                        .availableCapacity(350)
                        .unitCostInr(dec("2400.0000"))
                        .standardFreightInr(dec("60000.0000"))
                        .expeditedFreightInr(dec("240000.0000"))
                        .tariffRatePercent(dec("0.00"))
                        .standardLeadTimeDays(10)
                        .expeditedLeadTimeDays(6)
                        .qualityScore(dec("0.96"))
                        .isVetted(Boolean.TRUE)
                        .createdAt(now)
                        .build(),
                BackupSupplier.builder()
                        .id("BKP-VNM-003")
                        .partCode("HPH-1042")
                        .supplierId("SUP-VNM-HEAVY")
                        .supplierName("Vietnam Heavy Industries")
                        .country("VN")
                        .hubLocation("Hai Phong, Vietnam")
                        .availableCapacity(800)
                        .unitCostInr(dec("2100.0000"))
                        .standardFreightInr(dec("110000.0000"))
                        .expeditedFreightInr(dec("290000.0000"))
                        .tariffRatePercent(dec("5.00"))
                        .standardLeadTimeDays(12)
                        .expeditedLeadTimeDays(7)
                        .qualityScore(dec("0.89"))
                        .isVetted(Boolean.TRUE)
                        .createdAt(now)
                        .build(),
                BackupSupplier.builder()
                        .id("BKP-TUR-004")
                        .partCode("HPH-1042")
                        .supplierId("SUP-TUR-FORGE")
                        .supplierName("Turkey Precision Forgings")
                        .country("TR")
                        .hubLocation("Bursa, Turkey")
                        .availableCapacity(600)
                        .unitCostInr(dec("2350.0000"))
                        .standardFreightInr(dec("140000.0000"))
                        .expeditedFreightInr(dec("260000.0000"))
                        .tariffRatePercent(dec("10.00"))
                        .standardLeadTimeDays(14)
                        .expeditedLeadTimeDays(8)
                        .qualityScore(dec("0.92"))
                        .isVetted(Boolean.TRUE)
                        .createdAt(now)
                        .build()
        );
        backupSupplierRepo.saveAll(backups);

        // 9. Genesis audit event
        String payload = String.format(
                "{\"event\":\"SEED_RESET\",\"seed\":%d,\"dataClass\":\"SYNTHETIC\"," +
                "\"nodes\":[\"NODE-OEM-HPH\",\"NODE-T1-ALPHA\",\"NODE-T2-BETA\",\"NODE-T2-RAJKOT\",\"NODE-T3-ROTTERDAM\"]," +
                "\"backupSuppliers\":4}", seed);

        AuditEvent genesis = AuditEvent.builder()
                .scenarioId("SEED-SCENARIO-HPH")
                .eventType("SEED_RESET")
                .actor("system")
                .payload(payload)
                .beforeHash("0")
                .afterHash(sha256("0" + payload))
                .occurredAt(now)
                .build();
        auditRepo.save(genesis);

        log.info("SeedGenerator.reset() complete - 5 nodes, 4 links, 4 backup suppliers, 1 audit event");
    }

    private static BigDecimal dec(String s) {
        return new BigDecimal(s);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
