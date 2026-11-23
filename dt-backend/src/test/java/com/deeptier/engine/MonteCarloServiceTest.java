package com.deeptier.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MonteCarloServiceTest {

    private final MonteCarloService monteCarloService = new MonteCarloService();

    @Test
    @DisplayName("D05/T14: Fixed seed 20261121 produces 100% identical outputs across runs")
    void testDeterminismWithFixedSeed() {
        MonteCarloService.SimulationResult run1 = monteCarloService.runSimulation(
                20261121L, 10_000, 16.0, 0.25, 0.30, 5.0, 30, 15, BigDecimal.valueOf(80_000));

        MonteCarloService.SimulationResult run2 = monteCarloService.runSimulation(
                20261121L, 10_000, 16.0, 0.25, 0.30, 5.0, 30, 15, BigDecimal.valueOf(80_000));

        assertThat(run1.arrivalDaysP10()).isEqualTo(run2.arrivalDaysP10());
        assertThat(run1.arrivalDaysP50()).isEqualTo(run2.arrivalDaysP50());
        assertThat(run1.arrivalDaysP90()).isEqualTo(run2.arrivalDaysP90());
        assertThat(run1.deadlineMissProbability()).isEqualTo(run2.deadlineMissProbability());
        assertThat(run1.stockoutProbability()).isEqualTo(run2.stockoutProbability());
        assertThat(run1.expectedLateLossInr()).isEqualByComparingTo(run2.expectedLateLossInr());
    }

    @Test
    @DisplayName("Percentile order: p10 <= p50 <= p90")
    void testPercentileMonotonicity() {
        MonteCarloService.SimulationResult result = monteCarloService.runSimulation(
                20261121L, 10_000, 16.0, 0.25, 0.30, 5.0, 30, 15, BigDecimal.valueOf(80_000));

        assertThat(result.arrivalDaysP10()).isLessThanOrEqualTo(result.arrivalDaysP50());
        assertThat(result.arrivalDaysP50()).isLessThanOrEqualTo(result.arrivalDaysP90());
        assertThat(result.deadlineMissProbability()).isBetween(0.0, 1.0);
        assertThat(result.stockoutProbability()).isBetween(0.0, 1.0);
    }
}
