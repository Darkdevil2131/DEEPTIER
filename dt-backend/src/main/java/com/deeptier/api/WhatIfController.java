package com.deeptier.api;

import com.deeptier.engine.WhatIfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * What-If Shock Simulation & Recovery Optimizer API.
 *
 * Driven by the deterministic WhatIfService engine:
 *  - ExposureService
 *  - CoverService
 *  - MonteCarloService (10,000 trials, fixed seed)
 *  - OptimizerService
 */
@RestController
@RequestMapping("/api/whatif")
@Tag(name = "WhatIf", description = "Tariff shock simulation and recovery optimizer")
public class WhatIfController {

    private final WhatIfService whatIfService;

    public WhatIfController(WhatIfService whatIfService) {
        this.whatIfService = whatIfService;
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate tariff/disruption shock",
               description = "Returns duty exposure (INR), days of cover, stockout date, " +
                             "Monte Carlo percentiles (p10/p50/p90), and ranked recovery options. " +
                             "All arithmetic runs in deterministic Java - AI agents do NOT compute numbers.")
    public ResponseEntity<WhatIfService.WhatIfSimulationResponse> simulate(
            @RequestBody(required = false) SimulateRequest req) {

        SimulateRequest r = req != null ? req : new SimulateRequest(
                "HPH-WHATIF-001", "7325", "CN", 20,
                BigDecimal.valueOf(4_800_000), BigDecimal.valueOf(0.60),
                300, 20, 30, BigDecimal.valueOf(80_000), 20261121L
        );

        WhatIfService.WhatIfSimulationResponse response = whatIfService.simulate(
                r.scenarioId(),
                r.hsCode(),
                r.originCountry(),
                r.deltaPercentagePoints() != null ? BigDecimal.valueOf(r.deltaPercentagePoints()) : null,
                r.annualSpendInr(),
                r.costFraction(),
                r.unitsOnHand(),
                r.dailyDemand(),
                r.leadTimeDays(),
                r.lateCostPerDayInr(),
                r.seed()
        );

        return ResponseEntity.ok(response);
    }

    public record SimulateRequest(
            String scenarioId,
            String hsCode,
            String originCountry,
            Integer deltaPercentagePoints,
            BigDecimal annualSpendInr,
            BigDecimal costFraction,
            Integer unitsOnHand,
            Integer dailyDemand,
            Integer leadTimeDays,
            BigDecimal lateCostPerDayInr,
            Long seed
    ) {}
}

