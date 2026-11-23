package com.deeptier.api.dto;

import com.deeptier.domain.SupplierLink;
import com.deeptier.domain.SupplierNode;
import java.util.List;

public record TierMapGraphResponse(
        List<SupplierNode> nodes,
        List<SupplierLink> links,
        String dataClass,
        int totalNodes,
        int hiddenTiersCount
) {}
