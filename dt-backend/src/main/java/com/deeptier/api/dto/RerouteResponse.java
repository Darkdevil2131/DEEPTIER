package com.deeptier.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record RerouteResponse(
        String shipmentId,
        String status,
        Boolean feasible,
        String decisionStatus,
        SelectedRouteDto selectedRoute,
        List<RouteLegDto> legs,
        BigDecimal costEstimateInr,
        Double transitHoursEstimate,
        List<RouteAlternativeDto> alternatives,
        DecisionDto decision,
        List<String> dataProvenance,
        List<String> warnings
) {
    public RerouteResponse(
            String shipmentId,
            String status,
            SelectedRouteDto selectedRoute,
            List<RouteLegDto> legs,
            BigDecimal costEstimateInr,
            Double transitHoursEstimate,
            List<RouteAlternativeDto> alternatives,
            DecisionDto decision,
            List<String> dataProvenance,
            List<String> warnings) {
        this(shipmentId, status,
                selectedRoute != null && Boolean.TRUE.equals(selectedRoute.feasible()),
                decision != null ? decision.status() : null,
                selectedRoute, legs, costEstimateInr, transitHoursEstimate,
                alternatives, decision, dataProvenance, warnings);
    }

    public record SelectedRouteDto(
            String option,
            String mode,
            BigDecimal freightInr,
            Double transitHours,
            Boolean feasible,
            List<String> disruptionIds
    ) {}

    public record RouteLegDto(
            String legId,
            String fromHub,
            String toHub,
            String mode,
            Double distanceKm,
            Double transitHours,
            BigDecimal costInr
    ) {}

    public record RouteAlternativeDto(
            String option,
            String mode,
            BigDecimal costInr,
            Double transitHours,
            Boolean feasible,
            Double score
    ) {}

    public record DecisionDto(
            String status,
            List<String> gateReasons,
            String recommendedOption,
            String commercialEvidence
    ) {}
}
