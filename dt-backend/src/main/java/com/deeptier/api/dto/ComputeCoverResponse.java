package com.deeptier.api.dto;

public record ComputeCoverResponse(
        String tool,
        Integer unitsOnHand,
        Integer dailyDemand,
        Double daysOfCover,
        String daysOfCoverDisplay,
        Integer leadTimeDays,
        Integer gapDays,
        String dataClass,
        String reason
) {}
