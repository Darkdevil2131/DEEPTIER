package com.deeptier.api;

import com.deeptier.api.dto.CsvImportResponse;
import com.deeptier.api.dto.CsvRowValidationError;
import com.deeptier.approval.CsvValidationException;
import com.deeptier.engine.CsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supply chain CSV onboarding and ingestion")
public class SupplyChainController {

    private final CsvImportService csvImportService;

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import supply chain network via multipart CSV upload",
               description = "Atomically parses and validates supplier nodes and links from CSV. Supports mode=replace (default) or mode=append.")
    public ResponseEntity<CsvImportResponse> importCsvMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "replace") String mode) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new CsvValidationException(
                    "Uploaded CSV file is empty.",
                    0,
                    List.of(new CsvRowValidationError(1, "file", "", "Uploaded CSV file must not be empty"))
            );
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(csvImportService.importCsv(content, mode));
    }

    @PostMapping(value = "/import/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    @Operation(summary = "Import supply chain network via raw text/csv body",
               description = "Atomically parses and validates supplier nodes and links from raw CSV text. Supports mode=replace (default) or mode=append.")
    public ResponseEntity<CsvImportResponse> importCsvRaw(
            @RequestBody(required = false) String csvContent,
            @RequestParam(defaultValue = "replace") String mode) {
        return ResponseEntity.ok(csvImportService.importCsv(csvContent, mode));
    }

    @GetMapping(value = "/import/template", produces = "text/csv")
    @Operation(summary = "Download sample CSV template",
               description = "Returns a valid text/csv template matching the 14 required onboarding columns.")
    public ResponseEntity<String> downloadTemplate() {
        String template = String.join("\n",
                "parent_part_code,child_part_code,source_supplier_id,target_supplier_id,supplier_name,country,tier,link_type,confidence,annual_spend_inr,cost_fraction,hs_code,lead_time_days,evidence_ids",
                "HPH-1042,CAST-7325,SUP-CBE-ALPHA,SUP-NGB-BETA,Beta Foundry (Ningbo),CN,2,INFERRED,0.60,4800000,0.60,7325,30,EV-TRADE-2024-001"
        ) + "\n";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"deeptier-supply-chain-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(template);
    }
}
