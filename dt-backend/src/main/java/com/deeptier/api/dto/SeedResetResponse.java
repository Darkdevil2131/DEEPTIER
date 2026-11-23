package com.deeptier.api.dto;

import java.time.Instant;

public record SeedResetResponse(
        String status,
        long seed,
        String dataClass,
        String scenario,
        int nodes,
        int links,
        Instant timestamp
) {}
