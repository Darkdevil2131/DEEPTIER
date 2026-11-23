package com.deeptier.api;

import com.deeptier.api.dto.MockRfqResponse;
import com.deeptier.approval.RfqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rfqs")
@RequiredArgsConstructor
@Tag(name = "RFQ", description = "Mock RFQ tracking (strictly sentExternally = false)")
public class RfqController {

    private final RfqService rfqService;

    @GetMapping
    @Operation(summary = "List all generated mock RFQs",
               description = "Returns list of all generated MockRfq records ordered by issuedAt desc.")
    public ResponseEntity<List<MockRfqResponse>> list() {
        return ResponseEntity.ok(rfqService.listAll());
    }
}
