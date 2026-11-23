package com.deeptier.seed;

import com.deeptier.audit.AuditEvent;
import com.deeptier.domain.*;
import com.deeptier.repo.AuditEventRepository;
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

/**
 * Loads the deterministic "Hydraulic Pump Housing" demo scenario.
 *
 * Worked scenario:
 *   Product:   HPH-1042  HS 7325
 *   Tier-1:    Alpha Machining  DECLARED confidence=0.9  spend=4,800,000 INR
 *   Tier-2:    Beta Foundry (Country X / CN)  INFERRED confidence=0.6  castings=60% of spend
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
    public static final String NODE_OEM        = "NODE-OEM-HPH";
    public static final String NODE_ALPHA      = "NODE-T1-ALPHA";
    public static final String NODE_BETA       = "NODE-T2-BETA";
    public static final String LINK_OEM_ALPHA  = "LINK-OEM-ALPHA";
    public static final String LINK_ALPHA_BETA = "LINK-ALPHA-BETA";

    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;
    private final AuditEventRepository   auditRepo;

    @Transactional
    public void reset(long seed) {
        log.info("SeedGenerator.reset() - clearing all data, seed={}", seed);

        // 1. Wipe in FK-safe order
        auditRepo.deleteAll();
        linkRepo.deleteAll();
        nodeRepo.deleteAll();

        // 2. OEM node
        SupplierNode oem = SupplierNode.builder()
                .id(NODE_OEM)
                .supplierId("HPH-OEM")
                .name("Indian OEM (HPH-1042)")
                .country("IN")
                .tier(0)
                .linkType(LinkType.DECLARED)
                .confidence(dec("1.00"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-OEM-001")
                .createdAt(Instant.now())
                .build();
        nodeRepo.save(oem);

        // 3. Tier-1 Alpha Machining
        SupplierNode alpha = SupplierNode.builder()
                .id(NODE_ALPHA)
                .supplierId("ALPHA-MACH-01")
                .name("Alpha Machining")
                .country("IN")
                .tier(1)
                .linkType(LinkType.DECLARED)
                .confidence(dec("0.90"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-BOM-001,EV-PO-2024-001")
                .createdAt(Instant.now())
                .build();
        nodeRepo.save(alpha);

        // 4. Tier-2 Beta Foundry
        SupplierNode beta = SupplierNode.builder()
                .id(NODE_BETA)
                .supplierId("BETA-FOUNDRY-X")
                .name("Beta Foundry")
                .country("CN")
                .tier(2)
                .linkType(LinkType.INFERRED)
                .confidence(dec("0.60"))
                .dataClass(DataClass.SYNTHETIC)
                .evidenceIds("EV-TRADE-2024-001,EV-ML-INF-001")
                .createdAt(Instant.now())
                .build();
        nodeRepo.save(beta);

        // 5. Links
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
                .createdAt(Instant.now())
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
                .createdAt(Instant.now())
                .build();
        linkRepo.save(alphaToBeta);

        // 6. Genesis audit event
        String payload = String.format(
                "{\"event\":\"SEED_RESET\",\"seed\":%d,\"dataClass\":\"SYNTHETIC\"," +
                "\"nodes\":[\"NODE-OEM-HPH\",\"NODE-T1-ALPHA\",\"NODE-T2-BETA\"]}", seed);

        AuditEvent genesis = AuditEvent.builder()
                .scenarioId("SEED-SCENARIO-HPH")
                .eventType("SEED_RESET")
                .actor("system")
                .payload(payload)
                .beforeHash("0")
                .afterHash(sha256("0" + payload))
                .occurredAt(Instant.now())
                .build();
        auditRepo.save(genesis);

        log.info("SeedGenerator.reset() complete - 3 nodes, 2 links, 1 audit event");
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
