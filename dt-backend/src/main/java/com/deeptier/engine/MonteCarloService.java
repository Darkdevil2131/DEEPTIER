package com.deeptier.engine;

import com.deeptier.domain.DataClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;

/**
 * Seeded Monte Carlo Risk Simulator.
 *
 * Runs 10,000 trials using a fixed seed (default 20261121).
 * Durations are sampled from a Lognormal distribution:
 *   sigma^2 = ln(1 + cv^2)
 *   mu = ln(mean) - 0.5 * sigma^2
 *   sample = exp(mu + sigma * Z) where Z ~ N(0, 1)
 *
 * Determinism guarantee: Same seed produces identical results (Acceptance tests D05, T14).
 */
@Service
public class MonteCarloService {

    public static final long DEFAULT_SEED = 20261121L;
    public static final int DEFAULT_TRIALS = 10_000;

    public record SimulationResult(
            long seed,
            int trials,
            double arrivalDaysP10,
            double arrivalDaysP50,
            double arrivalDaysP90,
            double deadlineMissProbability,
            double stockoutProbability,
            double expectedLateDays,
            BigDecimal expectedLateLossInr,
            DataClass dataClass,
            String note
    ) {}

    public SimulationResult runSimulation(
            Long seedParam,
            Integer trialsParam,
            double meanLeadTimeDays,
            double coefficientOfVariation,
            double disruptionProb,
            double disruptionDelayDays,
            int deadlineDays,
            int coverDays,
            BigDecimal lateCostPerDayInr) {

        long seed = seedParam != null ? seedParam : DEFAULT_SEED;
        int trials = trialsParam != null && trialsParam > 0 ? trialsParam : DEFAULT_TRIALS;
        double cv = coefficientOfVariation > 0 ? coefficientOfVariation : 0.25;
        double mean = meanLeadTimeDays > 0 ? meanLeadTimeDays : 16.0;
        BigDecimal dailyLoss = lateCostPerDayInr != null ? lateCostPerDayInr : BigDecimal.valueOf(80_000);

        // Lognormal parameters
        double sigmaSquared = Math.log(1.0 + (cv * cv));
        double sigma = Math.sqrt(sigmaSquared);
        double mu = Math.log(mean) - (0.5 * sigmaSquared);

        Random random = new Random(seed);
        double[] arrivals = new double[trials];
        int deadlineMisses = 0;
        int stockouts = 0;
        double totalLateDays = 0.0;

        for (int i = 0; i < trials; i++) {
            // Standard normal via Box-Muller / nextGaussian()
            double z = random.nextGaussian();
            double duration = Math.exp(mu + (sigma * z));

            // Optional disruption shock
            if (disruptionProb > 0 && random.nextDouble() < disruptionProb) {
                duration += disruptionDelayDays;
            }

            arrivals[i] = duration;

            if (duration > deadlineDays) {
                deadlineMisses++;
                totalLateDays += (duration - deadlineDays);
            }
            if (duration > coverDays) {
                stockouts++;
            }
        }

        Arrays.sort(arrivals);

        double p10 = round(percentile(arrivals, 0.10));
        double p50 = round(percentile(arrivals, 0.50));
        double p90 = round(percentile(arrivals, 0.90));

        double missProb = round((double) deadlineMisses / trials);
        double stockoutProb = round((double) stockouts / trials);
        double meanLateDays = round(totalLateDays / trials);

        BigDecimal expectedLateLoss = dailyLoss
                .multiply(BigDecimal.valueOf(meanLateDays))
                .setScale(2, RoundingMode.HALF_UP);

        return new SimulationResult(
                seed,
                trials,
                p10,
                p50,
                p90,
                missProb,
                stockoutProb,
                meanLateDays,
                expectedLateLoss,
                DataClass.SYNTHETIC,
                "Seeded Monte Carlo simulation (10,000 trials, lognormal distribution)"
        );
    }

    private static double percentile(double[] sorted, double p) {
        int index = (int) Math.round(p * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
