package com.deeptier.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClassifySignalRequest(
        @NotBlank(message = "signalText is required")
        String signalText,

        String hsCode,
        String originCountry
) {}
