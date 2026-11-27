package com.deeptier.api;

import com.deeptier.api.dto.*;
import com.deeptier.domain.DataClass;
import com.deeptier.engine.CoverService;
import com.deeptier.engine.ExposureService;
import com.deeptier.engine.TierMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Internal tool endpoints consumed by the Python AI service (and future Joule via SAP Build Actions).
 * Protected by X-Internal-Key header (enforced by InternalKeyFilter).
 *
 * Contract:
 *  - AI agents NEVER compute numbers. They call these endpoints, get deterministic results,
 *    then classify/explain/draft using the returned data.
 *  - Every response carries strongly typed DTO records with dataClass and evidenceIds.
 *  - Zero raw Maps.
 */
@Slf4j
@RestController
@RequestMapping("/internal/tools")
@RequiredArgsConstructor
@Tag(name = "Internal Tools (AI)", description = "Deterministic math endpoints for AI agent consumption. " +
        "Protected by X-Internal-Key. AI classifies signals; Java computes all numbers.")
@SecurityRequirement(name = "InternalKey")
public class InternalToolsController {

    private final ExposureService exposureService;
    private final CoverService coverService;
    private final TierMapService tierMapService;

    // -- 1. compute_exposure --------------------------------------------------

    @PostMapping("/compute_exposure")
    @Operation(summary = "Compute duty exposure in INR",
               description = "Formula: annualSpendInr * costFraction * deltaPercentagePoints / 100. " +
                             "Returns BigDecimal - AI must NOT recompute this.")
    public ResponseEntity<ComputeExposureResponse> computeExposure(
            @Valid @RequestBody ComputeExposureRequest req) {

        BigDecimal exposure = req.annualSpendInr()
                .multiply(req.costFraction())
                .multiply(req.deltaPercentagePoints())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new ComputeExposureResponse(
                "compute_exposure",
                exposure,
                DataClass.SYNTHETIC.name(),
                req.evidenceIds() != null ? req.evidenceIds() : List.of(),
                "annualSpendInr * costFraction * deltaPercentagePoints / 100",
                new ComputeExposureResponse.ExposureInputs(
                        req.annualSpendInr(),
                        req.costFraction(),
                        req.deltaPercentagePoints()
                )
        ));
    }

    // -- 2. compute_cover ----------------------------------------------------

    @PostMapping("/compute_cover")
    @Operation(summary = "Compute days of inventory cover",
               description = "Returns daysOfCover = unitsOnHand / dailyDemand. " +
                             "Returns UNKNOWN if dailyDemand is 0 or null.")
    public ResponseEntity<ComputeCoverResponse> computeCover(
            @Valid @RequestBody ComputeCoverRequest req) {

        if (req.dailyDemand() == null || req.dailyDemand() == 0) {
            return ResponseEntity.ok(new ComputeCoverResponse(
                    "compute_cover",
                    req.unitsOnHand(),
                    req.dailyDemand(),
                    null,
                    "UNKNOWN",
                    req.leadTimeDays(),
                    req.leadTimeDays(),
                    DataClass.ASSUMED.name(),
                    "dailyDemand is zero or missing - cannot compute cover"
            ));
        }

        double cover = (double) req.unitsOnHand() / req.dailyDemand();
        int gapDays = (int) Math.max(0, req.leadTimeDays() - cover);

        return ResponseEntity.ok(new ComputeCoverResponse(
                "compute_cover",
                req.unitsOnHand(),
                req.dailyDemand(),
                cover,
                String.format("%.1f", cover),
                req.leadTimeDays(),
                gapDays,
                DataClass.SYNTHETIC.name(),
                null
        ));
    }

    // -- 3. classify_signal --------------------------------------------------

    @PostMapping("/classify_signal")
    @Operation(summary = "AI signal classifier stub",
               description = "AI agent classifies the risk signal text into a category. " +
                             "AI must cite evidenceIds from the response - never invent.")
    public ResponseEntity<ClassifySignalResponse> classifySignal(
            @Valid @RequestBody ClassifySignalRequest req) {

        return ResponseEntity.ok(new ClassifySignalResponse(
                "classify_signal",
                req.signalText(),
                "TARIFF_SHOCK",
                0.85,
                "HIGH",
                List.of("7325"),
                List.of("EV-TRADE-2024-001"),
                DataClass.ESTIMATED.name(),
                "P0 stub - Python AI service classifies in P1"
        ));
    }

    // -- 4. draft_rfq --------------------------------------------------------

    @PostMapping("/draft_rfq")
    @Operation(summary = "Draft mock RFQ (never sent externally)",
               description = "AI agent drafts a mock Request for Quotation. " +
                             "This endpoint NEVER sends anything externally - it creates an " +
                             "in-memory/DB mock only, for the Human-in-the-Loop approval gate.")
    public ResponseEntity<DraftRfqResponse> draftRfq(
            @Valid @RequestBody DraftRfqRequest req) {

        String rfqId = "RFQ-MOCK-" + System.currentTimeMillis();
        return ResponseEntity.ok(new DraftRfqResponse(
                "draft_rfq",
                rfqId,
                "DRAFT",
                false,
                true,
                "REVIEW_REQUIRED",
                req.supplierNodeId(),
                req.quantity(),
                req.deliveryOption(),
                "Dear Supplier, We request a quotation for urgent replenishment " +
                "of HS 7325 castings. [MOCK - not sent] [Requires human approval]",
                DataClass.SYNTHETIC.name(),
                List.of("EV-BOM-001"),
                "MOCK RFQ only - Human-in-the-Loop gate required before any action"
        ));
    }

    // -- 5. get_tier_map -----------------------------------------------------

    @GetMapping("/get_tier_map")
    @Operation(summary = "Get supplier tier map for AI context",
               description = "Returns the live supplier graph from the database (or canonical fallback if empty) " +
                             "for AI agent context injection. AI uses nodeIds/linkTypes as evidenceIds.")
    public ResponseEntity<InternalTierMapResponse> getTierMap() {
        TierMapService.TierGraph graph = tierMapService.getGraph();
        if (graph != null && graph.nodes() != null && !graph.nodes().isEmpty()) {
            List<InternalTierMapResponse.TierNodeDto> nodes = graph.nodes().stream()
                    .map(n -> new InternalTierMapResponse.TierNodeDto(
                            n.getId(),
                            n.getTier(),
                            n.getName(),
                            n.getCountry(),
                            n.getLinkType() != null ? n.getLinkType().name() : "DECLARED",
                            n.getConfidence() != null ? n.getConfidence().doubleValue() : 1.0
                    ))
                    .toList();
            List<InternalTierMapResponse.TierLinkDto> links = graph.links() != null
                    ? graph.links().stream()
                            .map(l -> new InternalTierMapResponse.TierLinkDto(
                                    l.getFromSupplierId(),
                                    l.getToSupplierId(),
                                    l.getHsCode(),
                                    l.getAnnualSpendInr() != null ? l.getAnnualSpendInr() : BigDecimal.ZERO,
                                    l.getCostFraction() != null ? l.getCostFraction() : BigDecimal.ONE,
                                    l.getLinkType() != null ? l.getLinkType().name() : "DECLARED"
                            ))
                            .toList()
                    : List.of();
            String dc = graph.dataClass() != null ? graph.dataClass().name() : DataClass.SYNTHETIC.name();
            return ResponseEntity.ok(new InternalTierMapResponse("get_tier_map", nodes, links, dc));
        }

        return ResponseEntity.ok(new InternalTierMapResponse(
                "get_tier_map",
                List.of(
                        new InternalTierMapResponse.TierNodeDto("NODE-OEM-HPH", 0, "Indian OEM (HPH-1042)", "IN", "DECLARED", 1.0),
                        new InternalTierMapResponse.TierNodeDto("NODE-T1-ALPHA", 1, "Alpha Machining", "IN", "DECLARED", 0.9),
                        new InternalTierMapResponse.TierNodeDto("NODE-T2-BETA", 2, "Beta Foundry", "CN", "INFERRED", 0.6)
                ),
                List.of(
                        new InternalTierMapResponse.TierLinkDto("NODE-OEM-HPH", "NODE-T1-ALPHA", "7325", BigDecimal.valueOf(4800000), BigDecimal.ONE, "DECLARED"),
                        new InternalTierMapResponse.TierLinkDto("NODE-T1-ALPHA", "NODE-T2-BETA", "7325", BigDecimal.valueOf(4800000), BigDecimal.valueOf(0.60), "INFERRED")
                ),
                DataClass.SYNTHETIC.name()
        ));
    }
}

