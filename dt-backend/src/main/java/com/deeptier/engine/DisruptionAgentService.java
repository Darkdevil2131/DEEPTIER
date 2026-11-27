package com.deeptier.engine;

import com.deeptier.api.dto.AnalyzeDisruptionRequest;
import com.deeptier.api.dto.AnalyzeDisruptionResponse;
import com.deeptier.audit.AuditService;
import com.deeptier.domain.DisruptionEvent;
import com.deeptier.repo.DisruptionEventRepository;
import com.deeptier.sap.AgentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates AI-assisted disruption extraction (via AgentGateway) with deterministic Java
 * arithmetic (WhatIfService) and persistent governance (DisruptionEvent + AuditService).
 * Automatically falls back to deterministic Java regex extraction if the Python AI brain is offline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisruptionAgentService {

    public static final String JAVA_FALLBACK_ENGINE = "JAVA_DETERMINISTIC_FALLBACK";

    private static final Pattern HS_PATTERN = Pattern.compile("\\bHSN?(?:\\s*code)?[\\s:#-]*(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_TO_PCT_PATTERN = Pattern.compile(
            "from\\s+(\\d+(?:\\.\\d+)?)\\s*%\\s+to\\s+(\\d+(?:\\.\\d+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern TO_PCT_PATTERN = Pattern.compile(
            "to\\s+(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:tariff|duty)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PCT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:%|percent|percentage\\s*points?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELAY_PATTERN = Pattern.compile(
            "(?:delay(?:ed)?(?:\\s+by)?\\s*|lead\\s*time\\s*\\+?\\s*)(\\d+)\\s*days?\\b|\\b(\\d+)\\s*days?\\s*(?:[a-z]+\\s+){0,2}(?:delay|stoppage|lead)",
            Pattern.CASE_INSENSITIVE);

    private final AgentGateway agentGateway;
    private final WhatIfService whatIfService;
    private final DisruptionEventRepository disruptionEventRepo;
    private final AuditService auditService;

    @Transactional
    public AnalyzeDisruptionResponse analyzeDisruption(AnalyzeDisruptionRequest req) {
        String partCode = (req.partCode() != null && !req.partCode().isBlank()) ? req.partCode().trim() : "HPH-1042";
        String scenarioId = (req.scenarioId() != null && !req.scenarioId().isBlank())
                ? req.scenarioId().trim()
                : "DT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        var gatewayOpt = agentGateway.analyzeDisruption(req, partCode, scenarioId);
        AgentGateway.DeepTierBrainResult brainResult = gatewayOpt
                .orElseGet(() -> extractDeterministic(req.alertText()));

        String backendStatus = gatewayOpt.isPresent() ? brainResult.backendStatus() : "fallback";
        List<String> warnings = new ArrayList<>();
        if (brainResult.warnings() != null) {
            warnings.addAll(brainResult.warnings());
        }

        // Java ALWAYS owns 100% of financial, inventory, Monte Carlo, and optimizer math.
        int baseLeadTime = req.leadTimeDays() != null ? req.leadTimeDays() : 30;
        int effectiveLeadTime = baseLeadTime + Math.max(0, brainResult.delayDays());

        WhatIfService.WhatIfSimulationResponse simulation = whatIfService.simulate(
                scenarioId,
                brainResult.affectedHsCode(),
                brainResult.originCountry(),
                brainResult.tariffDeltaPercent(),
                req.annualSpendInr(),
                req.costFraction(),
                req.unitsOnHand(),
                req.dailyDemand(),
                effectiveLeadTime,
                req.lateCostPerDayInr(),
                req.seed()
        );

        Set<String> mergedEvidence = new LinkedHashSet<>();
        if (brainResult.evidenceIds() != null) {
            mergedEvidence.addAll(brainResult.evidenceIds());
        }
        if (simulation.evidenceIds() != null) {
            mergedEvidence.addAll(simulation.evidenceIds());
        }
        List<String> evidenceList = List.copyOf(mergedEvidence);

        String executiveBrief = brainResult.executiveBrief();
        if (executiveBrief == null || executiveBrief.isBlank() || executiveBrief.contains("INR None")) {
            executiveBrief = buildBrief(partCode, brainResult, simulation, evidenceList);
        }

        Instant now = Instant.now();
        DisruptionEvent entity = DisruptionEvent.builder()
                .scenarioId(truncate(scenarioId, 64))
                .partCode(truncate(partCode, 64))
                .alertText(truncate(req.alertText(), 2000))
                .affectedHsCode(truncate(brainResult.affectedHsCode(), 32))
                .originCountry(truncate(brainResult.originCountry(), 16))
                .tariffDeltaPercent(brainResult.tariffDeltaPercent().setScale(2, RoundingMode.HALF_UP))
                .delayDays(brainResult.delayDays())
                .signalType(truncate(brainResult.signalType(), 64))
                .confidence(brainResult.confidence().setScale(4, RoundingMode.HALF_UP))
                .extractionEngine(truncate(brainResult.extractionEngine(), 64))
                .executiveBrief(truncate(executiveBrief, 4000))
                .evidenceIds(truncate(String.join(",", evidenceList), 1000))
                .createdAt(now)
                .build();

        DisruptionEvent saved = disruptionEventRepo.save(entity);

        String auditPayload = String.format(
                "{\"eventId\":\"%s\",\"partCode\":\"%s\",\"hsCode\":\"%s\",\"engine\":\"%s\",\"exposureInr\":%s}",
                saved.getId(), partCode, saved.getAffectedHsCode(), saved.getExtractionEngine(),
                simulation.tariffShock() != null ? simulation.tariffShock().totalExposureInr() : "0");
        auditService.logEvent(
                scenarioId,
                "DISRUPTION_ANALYZED",
                saved.getExtractionEngine(),
                "DisruptionEvent",
                saved.getId(),
                auditPayload);

        return new AnalyzeDisruptionResponse(
                saved.getId(),
                saved.getScenarioId(),
                saved.getPartCode(),
                saved.getAffectedHsCode(),
                saved.getOriginCountry(),
                saved.getTariffDeltaPercent(),
                saved.getDelayDays(),
                saved.getSignalType(),
                saved.getConfidence(),
                saved.getExtractionEngine(),
                backendStatus,
                List.copyOf(warnings),
                saved.getExecutiveBrief(),
                evidenceList,
                simulation,
                saved.getCreatedAt()
        );
    }

    private AgentGateway.DeepTierBrainResult extractDeterministic(String alertText) {
        String text = alertText != null ? alertText : "";
        Matcher hsMatcher = HS_PATTERN.matcher(text);
        boolean hasHs = hsMatcher.find();
        String hsCode = hasHs ? hsMatcher.group(1) : null;

        Matcher fromToMatcher = FROM_TO_PCT_PATTERN.matcher(text);
        Matcher toPctMatcher = TO_PCT_PATTERN.matcher(text);
        Matcher pctMatcher = PCT_PATTERN.matcher(text);

        BigDecimal tariffDelta = null;
        if (fromToMatcher.find()) {
            BigDecimal fromVal = new BigDecimal(fromToMatcher.group(1));
            BigDecimal toVal = new BigDecimal(fromToMatcher.group(2));
            tariffDelta = toVal.subtract(fromVal).max(BigDecimal.ZERO);
        } else if (toPctMatcher.find()) {
            tariffDelta = new BigDecimal(toPctMatcher.group(1));
        } else if (pctMatcher.find()) {
            tariffDelta = new BigDecimal(pctMatcher.group(1));
        }

        Matcher delayMatcher = DELAY_PATTERN.matcher(text);
        boolean hasDelay = delayMatcher.find();
        int delayDays = 0;
        if (hasDelay) {
            String grp = delayMatcher.group(1) != null ? delayMatcher.group(1) : delayMatcher.group(2);
            if (grp != null) {
                delayDays = Integer.parseInt(grp);
            }
        }

        String lower = text.toLowerCase(Locale.ROOT);
        String originCountry = "CN";
        if (lower.contains("vietnam")) {
            originCountry = "VN";
        } else if (lower.contains("germany")) {
            originCountry = "DE";
        } else if (lower.contains("japan")) {
            originCountry = "JP";
        } else if (lower.contains("india") && !lower.contains("china")) {
            originCountry = "IN";
        }

        // If neither HS/HSN code nor tariff/delay signal is present, do NOT fabricate HS 7325 / 20%!
        if (!hasHs && tariffDelta == null && !hasDelay) {
            return new AgentGateway.DeepTierBrainResult(
                    "UNKNOWN",
                    originCountry,
                    BigDecimal.ZERO,
                    0,
                    "UNPARSED",
                    new BigDecimal("0.2000"),
                    JAVA_FALLBACK_ENGINE,
                    null,
                    List.of("EV-JAVA-FALLBACK"),
                    "fallback",
                    List.of("No HS/HSN code, tariff percentage, or delay duration detected; human review required.")
            );
        }

        String resolvedHs = hsCode != null ? hsCode : "7325";
        BigDecimal resolvedTariff = tariffDelta != null ? tariffDelta : BigDecimal.ZERO;

        boolean hasLogisticsTerms = lower.contains("port") || lower.contains("congestion")
                || lower.contains("strike") || lower.contains("blockade") || lower.contains("delay");
        String signalType = (hasLogisticsTerms && resolvedTariff.compareTo(BigDecimal.ZERO) == 0)
                ? "LOGISTICS_DISRUPTION"
                : "TARIFF_SHOCK";

        BigDecimal confidence = (hasHs && tariffDelta != null)
                ? new BigDecimal("0.8500")
                : new BigDecimal("0.6500");

        return new AgentGateway.DeepTierBrainResult(
                resolvedHs,
                originCountry,
                resolvedTariff,
                delayDays,
                signalType,
                confidence,
                JAVA_FALLBACK_ENGINE,
                null,
                List.of("EV-JAVA-FALLBACK"),
                "fallback",
                List.of("Python AI service unreachable or timed out; deterministic Java fallback used.")
        );
    }

    private String buildBrief(
            String partCode,
            AgentGateway.DeepTierBrainResult extracted,
            WhatIfService.WhatIfSimulationResponse sim,
            List<String> evidenceIds) {

        BigDecimal exposureInr = sim.tariffShock() != null && sim.tariffShock().totalExposureInr() != null
                ? sim.tariffShock().totalExposureInr()
                : BigDecimal.ZERO;
        String coverDays = sim.inventory() != null && sim.inventory().coverDaysDisplay() != null
                ? sim.inventory().coverDaysDisplay()
                : "UNKNOWN";
        int gapDays = sim.inventory() != null ? sim.inventory().gapDays() : 0;

        if ("UNPARSED".equals(extracted.signalType())) {
            return String.format(
                    "Alert for part %s could not be mapped to an HS/HSN code, tariff delta, or delay (%s). "
                            + "Modeled duty exposure is INR %s with %s days of cover (gap %d days). "
                            + "Human review is required before taking any sourcing action.",
                    partCode, extracted.extractionEngine(), exposureInr.toPlainString(), coverDays, gapDays);
        }

        String topStrategy = (sim.recoveryOptions() != null && !sim.recoveryOptions().isEmpty())
                ? sim.recoveryOptions().getFirst().optionType()
                : "HUMAN_REVIEW_REQUIRED";

        return String.format(
                "Part %s (HS %s, origin %s) analyzed via %s: %s (+%s%% tariff delta, %dd delay). "
                        + "Deterministic duty exposure is INR %s with %s days of cover (gap %d days). "
                        + "Top ranked recovery option: %s. Grounded in evidenceIds %s. Human approval required.",
                partCode, extracted.affectedHsCode(), extracted.originCountry(), extracted.extractionEngine(),
                extracted.signalType(), extracted.tariffDeltaPercent().stripTrailingZeros().toPlainString(),
                extracted.delayDays(), exposureInr.toPlainString(), coverDays, gapDays, topStrategy, evidenceIds);
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) {
            return "";
        }
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }
}
