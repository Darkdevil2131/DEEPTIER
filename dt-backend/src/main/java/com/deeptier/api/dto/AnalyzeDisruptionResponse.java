package com.deeptier.api.dto;

import com.deeptier.engine.WhatIfService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AnalyzeDisruptionResponse(
        String eventId,
        String scenarioId,
        String partCode,
        String affectedHsCode,
        String originCountry,
        BigDecimal tariffDeltaPercent,
        Integer delayDays,
        String signalType,
        BigDecimal confidence,
        String extractionEngine,
        String backendStatus,
        List<String> warnings,
        String executiveBrief,
        List<String> evidenceIds,
        WhatIfService.WhatIfSimulationResponse simulation,
        Instant createdAt
) {}
