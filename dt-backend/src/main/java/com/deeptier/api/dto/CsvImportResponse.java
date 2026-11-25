package com.deeptier.api.dto;

import java.time.Instant;

public record CsvImportResponse(
        String status,
        int totalRowsProcessed,
        int nodesCreated,
        int linksCreated,
        String scenarioId,
        Instant timestamp
) {
}
