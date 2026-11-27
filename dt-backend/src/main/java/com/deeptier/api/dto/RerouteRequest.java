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
import java.util.Map;

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
        ImpactScenarioInput impactScenario,

        @Valid
        List<DocumentInput> documents,

        @DecimalMin("0.0")
        BigDecimal declaredValueUsd,

        @Size(max = 20)
        String incoterm,

        @Valid
        List<CarrierQuoteInput> carrierQuotes,

        Boolean useDemoDefaults
) {
    public RerouteRequest(
            String shipmentId,
            String origin,
            String destination,
            String cargoDescription,
            BigDecimal weightKg,
            List<String> allowedModes,
            List<DisruptionInput> disruptions,
            ImpactScenarioInput impactScenario) {
        this(shipmentId, origin, destination, cargoDescription, weightKg, allowedModes,
                disruptions, impactScenario, null, null, null, null, null);
    }

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

    public record DocumentInput(
            @NotBlank String documentType,
            Map<String, Object> fields,
            String rawText
    ) {}

    public record CarrierQuoteInput(
            @NotBlank String carrierId,
            @NotBlank String mode,
            @NotNull @Positive BigDecimal amount,
            String currency,
            @Positive Double transitHours,
            @Positive BigDecimal quotedCapacityKg,
            Boolean includesInsurance
    ) {}
}
