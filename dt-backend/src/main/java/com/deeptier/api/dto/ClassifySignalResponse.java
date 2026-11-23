package com.deeptier.api.dto;

import java.util.List;

public record ClassifySignalResponse(
        String tool,
        String inputText,
        String classifiedCategory,
        double confidence,
        String severity,
        List<String> affectedHsCodes,
        List<String> evidenceIds,
        String dataClass,
        String note
) {}
