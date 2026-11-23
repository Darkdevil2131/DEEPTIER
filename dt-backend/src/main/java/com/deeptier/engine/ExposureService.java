package com.deeptier.engine;

import com.deeptier.domain.DataClass;
import com.deeptier.domain.SupplierLink;
import com.deeptier.domain.SupplierNode;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic Tariff Shock & Duty Exposure Engine.
 *
 * Formula:
 *   duty_delta_inr = annual_spend_inr * cost_fraction * (delta_rate_pct / 100) * (pass_through_pct / 100)
 *
 * Acceptance tests supported:
 *   D01: Tariff event on HS code with no matching parts -> 0 exposure, no error
 *   D03: Exposure total equals sum of per-part line items
 *   D06: Higher tariff rate -> exposure never decreases
 */
@Service
public class ExposureService {

    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;

    public ExposureService(SupplierNodeRepository nodeRepo, SupplierLinkRepository linkRepo) {
        this.nodeRepo = nodeRepo;
        this.linkRepo = linkRepo;
    }

    public record PartExposure(
            String linkId,
            String fromSupplierId,
            String toSupplierId,
            String supplierName,
            String supplierCountry,
            String hsCode,
            int tier,
            BigDecimal annualSpendInr,
            BigDecimal costFraction,
            BigDecimal affectedSpendInr,
            BigDecimal dutyDeltaInr,
            DataClass dataClass
    ) {}

    public record ExposureSummary(
            String hsCode,
            String originCountry,
            BigDecimal deltaPercentagePoints,
            BigDecimal passThroughPct,
            BigDecimal totalExposureInr,
            List<PartExposure> lineItems,
            DataClass dataClass,
            List<String> evidenceIds
    ) {}

    public ExposureSummary calculateExposure(
            String hsCode,
            String originCountry,
            BigDecimal deltaPercentagePoints,
            BigDecimal passThroughPct) {

        BigDecimal deltaRate = deltaPercentagePoints != null ? deltaPercentagePoints : BigDecimal.valueOf(20);
        BigDecimal passThrough = passThroughPct != null ? passThroughPct : BigDecimal.valueOf(100);

        List<SupplierNode> allNodes = nodeRepo.findAll();
        Map<String, SupplierNode> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(SupplierNode::getId, n -> n, (a, b) -> a));

        List<SupplierLink> allLinks = linkRepo.findAll();
        List<PartExposure> lineItems = new ArrayList<>();
        BigDecimal totalExposure = BigDecimal.ZERO;
        List<String> evidenceIds = new ArrayList<>();

        String cleanHs = hsCode != null ? hsCode.trim() : "";

        for (SupplierLink link : allLinks) {
            // Check HS code match (exact or 4-digit prefix match)
            boolean hsMatches = cleanHs.isEmpty() ||
                    (link.getHsCode() != null && (link.getHsCode().equals(cleanHs) || link.getHsCode().startsWith(cleanHs)));

            if (!hsMatches) {
                continue;
            }

            // Check if supplier is in origin country
            SupplierNode targetSupplier = nodeMap.get(link.getToSupplierId());
            boolean countryMatches = originCountry == null || originCountry.isBlank() ||
                    (targetSupplier != null && originCountry.equalsIgnoreCase(targetSupplier.getCountry()));

            if (!countryMatches) {
                continue;
            }

            BigDecimal spend = link.getAnnualSpendInr() != null ? link.getAnnualSpendInr() : BigDecimal.ZERO;
            BigDecimal fraction = link.getCostFraction() != null ? link.getCostFraction() : BigDecimal.ONE;
            BigDecimal affectedSpend = spend.multiply(fraction);

            // duty_delta = affectedSpend * (deltaRate / 100) * (passThrough / 100)
            BigDecimal dutyDelta = affectedSpend
                    .multiply(deltaRate)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    .multiply(passThrough)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            totalExposure = totalExposure.add(dutyDelta);

            if (link.getEvidenceIds() != null) {
                for (String ev : link.getEvidenceIds().split(",")) {
                    if (!ev.isBlank() && !evidenceIds.contains(ev.trim())) {
                        evidenceIds.add(ev.trim());
                    }
                }
            }

            lineItems.add(new PartExposure(
                    link.getId(),
                    link.getFromSupplierId(),
                    link.getToSupplierId(),
                    targetSupplier != null ? targetSupplier.getName() : "UNKNOWN",
                    targetSupplier != null ? targetSupplier.getCountry() : "UNKNOWN",
                    link.getHsCode(),
                    targetSupplier != null ? targetSupplier.getTier() : 0,
                    spend,
                    fraction,
                    affectedSpend,
                    dutyDelta,
                    link.getDataClass() != null ? link.getDataClass() : DataClass.SYNTHETIC
            ));
        }

        return new ExposureSummary(
                cleanHs,
                originCountry,
                deltaRate,
                passThrough,
                totalExposure.setScale(2, RoundingMode.HALF_UP),
                lineItems,
                DataClass.SYNTHETIC,
                evidenceIds
        );
    }
}
