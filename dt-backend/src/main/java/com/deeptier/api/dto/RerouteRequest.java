package com.deeptier.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RerouteRequest(
        @Size(max = 80)
        String shipmentId,

        @NotBlank(message = "origin is required")
        @Size(min = 2, max = 100)
        String origin,

        @NotBlank(message = "destination is required")
        @Size(min = 2, max = 100)
        String destination,

        @NotBlank(message = "cargoDescription is required")
        @Size(min = 2, max = 500)
        String cargoDescription,

        @NotNull(message = "weightKg is required")
        @Positive(message = "weightKg must be positive")
        BigDecimal weightKg,

        @NotEmpty(message = "allowedModes must contain at least one mode")
        List<@NotBlank String> allowedModes,

        @Valid
        List<DisruptionInput> disruptions,

        @Valid
        ImpactScenarioInput impactScenario
) {
    public record DisruptionInput(
            @NotBlank String signalId,
            List<String> affectedModes,
            List<String> affectedLocations,
            List<String> affectedLegIds,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal severity,
            @DecimalMin("0.0") BigDecimal delayHours
    ) {}

    public record ImpactScenarioInput(
            @NotNull @Positive BigDecimal criticalWeightKg,
            @NotNull @Positive BigDecimal targetDays,
            @NotNull @DecimalMin("0.0") BigDecimal dailyStoppageInr,
            List<String> closedLegIds,
            @DecimalMin("0.1") BigDecimal freightMultiplier
    ) {}
}
