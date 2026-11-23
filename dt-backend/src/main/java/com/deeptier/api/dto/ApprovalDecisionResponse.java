package com.deeptier.api.dto;

public record ApprovalDecisionResponse(
        ApprovalResponse approval,
        MockRfqResponse rfq
) {}
