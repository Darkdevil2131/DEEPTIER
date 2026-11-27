package com.deeptier.api;

import com.deeptier.api.dto.MockRfqResponse;
import com.deeptier.api.dto.SapPurchaseRequisitionPayload;
import com.deeptier.api.dto.SapPushResponse;
import com.deeptier.approval.RfqService;
import com.deeptier.sap.SapPayloadService;
import com.deeptier.sap.SapPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rfqs")
@RequiredArgsConstructor
@Tag(name = "RFQ", description = "Mock RFQ tracking and S/4HANA Purchase Requisition push")
public class RfqController {

    private final RfqService rfqService;
    private final SapPayloadService sapPayloadService;
    private final SapPushService sapPushService;

    @GetMapping
    @Operation(summary = "List all generated mock RFQs",
               description = "Returns list of all generated MockRfq records ordered by issuedAt desc.")
    public ResponseEntity<List<MockRfqResponse>> list() {
        return ResponseEntity.ok(rfqService.listAll());
    }

    @GetMapping("/{id}/sap-payload")
    @Operation(summary = "Generate SAP S/4HANA Clean-Core Purchase Requisition payload",
               description = "Maps an AUDITED MockRfq and its ApprovalRequest + AuditEvent hash to S/4HANA OData API_PURCHASEREQ_PROCESS_SRV schema.")
    public ResponseEntity<SapPurchaseRequisitionPayload> getSapPayload(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(sapPayloadService.buildPurchaseRequisitionPayload(id, idempotencyKey));
    }

    @PostMapping("/{id}/push-sap")
    @Operation(summary = "Push Purchase Requisition to SAP S/4HANA",
               description = "Executes live OData V2 CSRF+POST when S4HANA_BASE_URL is configured, or simulated S/4HANA push otherwise. Marks RFQ as PUSHED_TO_SAP and logs SAP_PR_PUSHED.")
    public ResponseEntity<SapPushResponse> pushToSap(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(sapPushService.pushToSap(id, idempotencyKey));
    }
}
