package com.deeptier.engine;

import com.deeptier.domain.DataClass;
import com.deeptier.domain.LinkType;
import com.deeptier.domain.SupplierLink;
import com.deeptier.domain.SupplierNode;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Supplier Tier-Map Service.
 *
 * Rules:
 *  - Declared edges receive fixed confidence 0.90.
 *  - Inferred edges calculated by formula:
 *      min(0.80, 0.30 + 0.20 * hsMatch + 0.15 * regionMatch + 0.15 * (evidenceCount >= 2))
 *  - Inferred links are NEVER displayed as declared (Acceptance test D02).
 */
@Service
public class TierMapService {

    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;

    public TierMapService(SupplierNodeRepository nodeRepo, SupplierLinkRepository linkRepo) {
        this.nodeRepo = nodeRepo;
        this.linkRepo = linkRepo;
    }

    public record TierGraph(
            List<SupplierNode> nodes,
            List<SupplierLink> links,
            DataClass dataClass,
            int totalNodes,
            int hiddenTiersCount
    ) {}

    public TierGraph getGraph() {
        List<SupplierNode> nodes = nodeRepo.findAll();
        List<SupplierLink> links = linkRepo.findAll();

        int hiddenTiers = (int) nodes.stream().filter(n -> n.getTier() >= 2).count();

        return new TierGraph(
                nodes,
                links,
                DataClass.SYNTHETIC,
                nodes.size(),
                hiddenTiers
        );
    }

    /**
     * Compute confidence for an inferred candidate edge.
     */
    public BigDecimal calculateInferredConfidence(
            boolean hsMatch,
            boolean regionMatch,
            int independentEvidenceCount) {

        double score = 0.30;
        if (hsMatch) score += 0.20;
        if (regionMatch) score += 0.15;
        if (independentEvidenceCount >= 2) score += 0.15;

        double clamped = Math.min(0.80, score);
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    public Optional<SupplierLink> confirmInferredLink(String linkId, String confirmedBy) {
        Optional<SupplierLink> opt = linkRepo.findById(linkId);
        if (opt.isPresent()) {
            SupplierLink link = opt.get();
            // Note: link remains typed INFERRED or DECLARED as per provenance, confirmed_by is recorded
            link.setEvidenceIds((link.getEvidenceIds() != null ? link.getEvidenceIds() + "," : "") +
                    "CONFIRMED_BY_" + (confirmedBy != null ? confirmedBy : "USER"));
            linkRepo.save(link);
            return Optional.of(link);
        }
        return Optional.empty();
    }
}
