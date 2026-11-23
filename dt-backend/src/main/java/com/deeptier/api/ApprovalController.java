package com.deeptier.api;

import com.deeptier.api.dto.*;
import com.deeptier.approval.ApprovalService;
import com.deeptier.approval.ApprovalStatus;
import com.deeptier.approval.IdempotencyRecord;
import com.deeptier.approval.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@Tag(name = "Approval", description = "Human-in-the-Loop approval gate, idempotency, and mock RFQ creation")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    @Operation(summary = "Create an approval request",
               description = "Creates a new approval request in PENDING_APPROVAL state. Supports Idempotency-Key header.")
    public ResponseEntity<ApprovalResponse> createApproval(
            @Valid @RequestBody CreateApprovalRequest req,
            @Parameter(description = "Idempotency key to prevent duplicate creation")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey) {

        String key = resolveKey(idempotencyKey, xIdempotencyKey);
        String endpoint = "POST:/api/approvals";

        if (key != null) {
            Optional<IdempotencyRecord> existing = idempotencyService.findExisting(key, endpoint);
            if (existing.isPresent()) {
                ApprovalResponse cached = idempotencyService.deserialize(existing.get(), ApprovalResponse.class);
                return ResponseEntity.status(existing.get().getHttpStatus()).body(cached);
            }
        }

        ApprovalResponse res = approvalService.createApproval(req);

        if (key != null) {
            idempotencyService.record(key, endpoint, HttpStatus.CREATED.value(), res);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping
    @Operation(summary = "List approval requests",
               description = "Returns approval requests optionally filtered by status, ordered by createdAt desc.")
    public ResponseEntity<List<ApprovalResponse>> list(
            @RequestParam(required = false) ApprovalStatus status) {
        return ResponseEntity.ok(approvalService.listApprovals(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval request by ID")
    public ResponseEntity<ApprovalResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(approvalService.getApproval(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve request",
               description = "Approves a PENDING_APPROVAL request, generates a Mock RFQ, and records audit trail. Supports Idempotency-Key header.")
    public ResponseEntity<ApprovalDecisionResponse> approve(
            @PathVariable String id,
            @RequestBody(required = false) ApprovalDecisionRequest req,
            @Parameter(description = "Idempotency key to prevent duplicate approval/RFQ generation")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey) {

        String key = resolveKey(idempotencyKey, xIdempotencyKey);
        String endpoint = "POST:/api/approvals/" + id + "/approve";

        if (key != null) {
            Optional<IdempotencyRecord> existing = idempotencyService.findExisting(key, endpoint);
            if (existing.isPresent()) {
                ApprovalDecisionResponse cached = idempotencyService.deserialize(existing.get(), ApprovalDecisionResponse.class);
                return ResponseEntity.status(existing.get().getHttpStatus()).body(cached);
            }
        }

        ApprovalDecisionResponse res = approvalService.approve(id, req);

        if (key != null) {
            idempotencyService.record(key, endpoint, HttpStatus.OK.value(), res);
        }

        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject request",
               description = "Rejects a PENDING_APPROVAL request, updates status to REJECTED then AUDITED without generating RFQ. Supports Idempotency-Key header.")
    public ResponseEntity<ApprovalResponse> reject(
            @PathVariable String id,
            @RequestBody(required = false) ApprovalDecisionRequest req,
            @Parameter(description = "Idempotency key to prevent duplicate rejection")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey) {

        String key = resolveKey(idempotencyKey, xIdempotencyKey);
        String endpoint = "POST:/api/approvals/" + id + "/reject";

        if (key != null) {
            Optional<IdempotencyRecord> existing = idempotencyService.findExisting(key, endpoint);
            if (existing.isPresent()) {
                ApprovalResponse cached = idempotencyService.deserialize(existing.get(), ApprovalResponse.class);
                return ResponseEntity.status(existing.get().getHttpStatus()).body(cached);
            }
        }

        ApprovalResponse res = approvalService.reject(id, req);

        if (key != null) {
            idempotencyService.record(key, endpoint, HttpStatus.OK.value(), res);
        }

        return ResponseEntity.ok(res);
    }

    private static String resolveKey(String key1, String key2) {
        if (key1 != null && !key1.isBlank()) {
            return key1;
        }
        if (key2 != null && !key2.isBlank()) {
            return key2;
        }
        return null;
    }
}
