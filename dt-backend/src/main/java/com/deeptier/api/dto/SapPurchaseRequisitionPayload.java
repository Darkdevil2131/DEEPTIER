package com.deeptier.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SapPurchaseRequisitionPayload(
        String sapService,
        String entitySet,
        CorrelationMeta correlation,
        PurchaseRequisitionBody payload
) {

    public record CorrelationMeta(
            String deepTierRfqId,
            String approvalRequestId,
            String idempotencyKey,
            String auditHash
    ) {}

    public record PurchaseRequisitionBody(
            @JsonProperty("PurchaseRequisitionType") String purchaseRequisitionType,
            @JsonProperty("PurReqnDescription") String purReqnDescription,
            @JsonProperty("to_PurchaseReqnItem") PurchaseReqnItemsWrapper toPurchaseReqnItem
    ) {}

    public record PurchaseReqnItemsWrapper(
            List<PurchaseReqnItem> results
    ) {}

    public record PurchaseReqnItem(
            @JsonProperty("PurchaseRequisitionItemText") String purchaseRequisitionItemText,
            @JsonProperty("Material") String material,
            @JsonProperty("Plant") String plant,
            @JsonProperty("StorageLocation") String storageLocation,
            @JsonProperty("RequestedQuantity") String requestedQuantity,
            @JsonProperty("BaseUnit") String baseUnit,
            @JsonProperty("PurchaseRequisitionPrice") String purchaseRequisitionPrice,
            @JsonProperty("PurReqnPriceQuantity") String purReqnPriceQuantity,
            @JsonProperty("Currency") String currency,
            @JsonProperty("DeliveryDate") String deliveryDate,
            @JsonProperty("FixedSupplier") String fixedSupplier,
            @JsonProperty("AccountAssignmentCategory") String accountAssignmentCategory,
            @JsonProperty("PurReqnOrigin") String purReqnOrigin,
            @JsonProperty("ExternalApprovalStatus") String externalApprovalStatus,
            @JsonProperty("RequisitionerName") String requisitionerName
    ) {}
}
