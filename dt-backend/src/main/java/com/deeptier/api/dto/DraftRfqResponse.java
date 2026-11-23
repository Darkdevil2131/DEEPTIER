package com.deeptier.api.dto;

import java.util.List;

public record DraftRfqResponse(
        String tool,
        String rfqId,
        String status,
        boolean sentExternally,
        boolean requiresHumanApproval,
        String approvalState,
        String supplierNodeId,
        String quantity,
        String deliveryOption,
        String draftText,
        String dataClass,
        List<String> evidenceIds,
        String note
) {}
