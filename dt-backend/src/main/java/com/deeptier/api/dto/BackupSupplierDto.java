package com.deeptier.api.dto;

import java.math.BigDecimal;

public record BackupSupplierDto(
        String id,
        String partCode,
        String supplierId,
        String supplierName,
        String country,
        String hubLocation,
        Integer availableCapacity,
        BigDecimal unitCostInr,
        BigDecimal standardFreightInr,
        BigDecimal expeditedFreightInr,
        BigDecimal tariffRatePercent,
        Integer standardLeadTimeDays,
        Integer expeditedLeadTimeDays,
        BigDecimal qualityScore,
        Boolean isVetted
) {
}
