package com.deeptier.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ComputeCoverRequest(
        @PositiveOrZero(message = "unitsOnHand must be positive or zero")
        int unitsOnHand,

        Integer dailyDemand,

        @PositiveOrZero(message = "leadTimeDays must be positive or zero")
        int leadTimeDays
) {}
