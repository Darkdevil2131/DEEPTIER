package com.deeptier.engine;

import com.deeptier.domain.DataClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * End-to-End What-If Simulation Orchestrator.
 *
 * Coordinates:
 *   1. ExposureService (tariff impact)
 *   2. CoverService (inventory cover & stockout date)
 *   3. MonteCarloService (10,000 trials p10/p50/p90)
 *   4. OptimizerService (recovery ranking)
 *
 * Performance target: < 2000 ms (typically < 100 ms in deterministic Java).
 */
@Service
public class WhatIfService {

    private final ExposureService exposureService;
    private final CoverService coverService;
    private final MonteCarloService monteCarloService;
    private final OptimizerService optimizerService;

    public WhatIfService(
            ExposureService exposureService,
            CoverService coverService,
            MonteCarloService monteCarloService,
            OptimizerService optimizerService) {
        this.exposureService = exposureService;
        this.coverService = coverService;
        this.monteCarloService = monteCarloService;
        this.optimizerService = optimizerService;
    }

    public record WhatIfSimulationResponse(
            String scenarioId,
            DataClass dataClass,
            List<String> evidenceIds,
            ExposureService.ExposureSummary tariffShock,
            CoverService.CoverResult inventory,
            MonteCarloService.SimulationResult monteCarlo,
            List<OptimizerService.RouteOption> recoveryOptions,
            List<OptimizerService.RejectedOption> rejectedOptions,
            long executionTimeMs,
            String note
    ) {}

    public WhatIfSimulationResponse simulate(
            String scenarioId,
            String hsCode,
            String originCountry,
            BigDecimal deltaPercentagePoints,
            BigDecimal annualSpendInr,
            BigDecimal costFraction,
            Integer unitsOnHand,
            Integer dailyDemand,
            Integer leadTimeDays,
            BigDecimal lateCostPerDayInr,
            Long seed) {

        long start = System.currentTimeMillis();

        String code = hsCode != null ? hsCode : "7325";
        String origin = originCountry != null ? originCountry : "CN";
        BigDecimal deltaRate = deltaPercentagePoints != null ? deltaPercentagePoints : BigDecimal.valueOf(20);
        int onHand = unitsOnHand != null ? unitsOnHand : 300;
        int demand = dailyDemand != null ? dailyDemand : 20;
        int leadDays = leadTimeDays != null ? leadTimeDays : 30;
        BigDecimal dailyLoss = lateCostPerDayInr != null ? lateCostPerDayInr : BigDecimal.valueOf(80_000);

        // 1. Exposure (honor explicit annualSpendInr + costFraction overrides when supplied)
        ExposureService.ExposureSummary exposure;
        if (annualSpendInr != null && costFraction != null) {
            BigDecimal affectedSpend = annualSpendInr.multiply(costFraction);
            BigDecimal dutyDelta = affectedSpend
                    .multiply(deltaRate)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            ExposureService.PartExposure overrideItem = new ExposureService.PartExposure(
                    "OVERRIDE-INPUT",
                    "REQUEST",
                    "OVERRIDE",
                    "Request Override Basis",
                    origin,
                    code,
                    0,
                    annualSpendInr,
                    costFraction,
                    affectedSpend,
                    dutyDelta,
                    DataClass.OBSERVED
            );
            exposure = new ExposureService.ExposureSummary(
                    code,
                    origin,
                    deltaRate,
                    BigDecimal.valueOf(100),
                    dutyDelta,
                    List.of(overrideItem),
                    DataClass.OBSERVED,
                    List.of("EV-REQUEST-OVERRIDE")
            );
        } else {
            exposure = exposureService.calculateExposure(
                    code, origin, deltaRate, BigDecimal.valueOf(100));
        }

        // 2. Cover
        CoverService.CoverResult cover = coverService.calculateCover(
                onHand, demand, leadDays, dailyLoss, LocalDate.now());

        // 3. Monte Carlo (10,000 trials)
        int coverDaysInt = cover.coverDays() != null ? cover.coverDays().intValue() : 15;
        MonteCarloService.SimulationResult mc = monteCarloService.runSimulation(
                seed != null ? seed : MonteCarloService.DEFAULT_SEED,
                MonteCarloService.DEFAULT_TRIALS,
                leadDays > 0 ? leadDays : 16.0,
                0.25,
                0.30,
                5.0,
                leadDays,
                coverDaysInt,
                dailyLoss
        );

        // 4. Optimizer
        OptimizerService.OptimizationResult opt = optimizerService.optimize(
                scenarioId,
                cover.gapDays(),
                dailyLoss,
                exposure.totalExposureInr(),
                14,
                leadDays,
                origin,
                "IN"
        );

        long duration = System.currentTimeMillis() - start;

        return new WhatIfSimulationResponse(
                scenarioId != null ? scenarioId : "HPH-WHATIF-001",
                DataClass.SYNTHETIC,
                List.of("EV-BOM-001", "EV-TRADE-2024-001", "EV-ML-INF-001"),
                exposure,
                cover,
                mc,
                opt.feasibleOptions(),
                opt.rejectedOptions(),
                duration,
                "Deterministic engine simulation completed in " + duration + " ms"
        );
    }
}
