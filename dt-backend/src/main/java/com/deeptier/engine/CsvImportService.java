package com.deeptier.engine;

import com.deeptier.api.dto.CsvImportResponse;
import com.deeptier.api.dto.CsvRowValidationError;
import com.deeptier.approval.CsvValidationException;
import com.deeptier.audit.AuditService;
import com.deeptier.domain.DataClass;
import com.deeptier.domain.LinkType;
import com.deeptier.domain.SupplierLink;
import com.deeptier.domain.SupplierNode;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Atomic RFC 4180 CSV Supply Chain Onboarding Engine.
 * Validates all rows before persisting; any validation failure rolls back the batch
 * and throws CsvValidationException with line-level error details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "parent_part_code",
            "child_part_code",
            "source_supplier_id",
            "target_supplier_id",
            "supplier_name",
            "country",
            "tier",
            "link_type",
            "confidence",
            "annual_spend_inr",
            "cost_fraction",
            "hs_code",
            "lead_time_days",
            "evidence_ids"
    );

    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;
    private final AuditService auditService;

    @Transactional
    public CsvImportResponse importCsv(String csvContent, String mode) {
        String normalizedMode = normalizeMode(mode);
        if (csvContent == null || csvContent.isBlank()) {
            throw new CsvValidationException(
                    "CSV content is empty.",
                    0,
                    List.of(new CsvRowValidationError(1, "file", "", "CSV file or payload must not be empty"))
            );
        }

        List<ParsedLine> lines = parseLines(csvContent);
        if (lines.isEmpty()) {
            throw new CsvValidationException(
                    "CSV content contains no header row.",
                    0,
                    List.of(new CsvRowValidationError(1, "header", "", "CSV header row is missing"))
            );
        }

        ParsedLine headerLine = lines.get(0);
        Map<String, Integer> headerIndex = mapHeaders(headerLine.fields());
        List<CsvRowValidationError> errors = new ArrayList<>();

        for (String required : REQUIRED_HEADERS) {
            if (!headerIndex.containsKey(required)) {
                errors.add(new CsvRowValidationError(
                        headerLine.lineNumber(),
                        required,
                        "",
                        "Missing required CSV header: " + required
                ));
            }
        }
        if (!errors.isEmpty()) {
            throw new CsvValidationException(
                    String.format("CSV header is missing %d required column(s).", errors.size()),
                    0,
                    errors
            );
        }

        List<ParsedLine> dataLines = lines.subList(1, lines.size());
        if (dataLines.isEmpty()) {
            throw new CsvValidationException(
                    "CSV contains header only and 0 data rows.",
                    0,
                    List.of(new CsvRowValidationError(2, "row", "", "At least one data row is required"))
            );
        }

        List<ValidatedRow> validatedRows = new ArrayList<>();
        for (ParsedLine dataLine : dataLines) {
            ValidatedRow row = validateRow(dataLine, headerIndex, errors);
            if (row != null) {
                validatedRows.add(row);
            }
        }

        if (!errors.isEmpty()) {
            throw new CsvValidationException(
                    String.format("CSV contains %d validation errors across %d rows. Batch rolled back.",
                            errors.size(), dataLines.size()),
                    dataLines.size(),
                    errors
            );
        }

        if ("replace".equals(normalizedMode)) {
            linkRepo.deleteAll();
            nodeRepo.deleteAll();
        }

        Instant now = Instant.now();
        Map<String, SupplierNode> nodesById = new HashMap<>();
        if ("append".equals(normalizedMode)) {
            for (SupplierNode existing : nodeRepo.findAll()) {
                nodesById.put(existing.getId(), existing);
            }
        }

        Set<String> existingLinkKeys = new HashSet<>();
        if ("append".equals(normalizedMode)) {
            for (SupplierLink existing : linkRepo.findAll()) {
                existingLinkKeys.add(linkKey(existing.getFromSupplierId(), existing.getToSupplierId(), existing.getHsCode()));
            }
        }

        int nodesCreated = 0;
        int linksCreated = 0;

        for (ValidatedRow row : validatedRows) {
            DataClass dataClass = mapDataClass(row.linkType());

            if (!nodesById.containsKey(row.targetSupplierId())) {
                SupplierNode node = SupplierNode.builder()
                        .id(row.targetSupplierId())
                        .supplierId(row.targetSupplierId())
                        .name(row.supplierName())
                        .country(row.country())
                        .tier(row.tier())
                        .linkType(row.linkType())
                        .confidence(row.confidence())
                        .dataClass(dataClass)
                        .evidenceIds(row.evidenceIds())
                        .createdAt(now)
                        .build();
                nodeRepo.save(node);
                nodesById.put(node.getId(), node);
                nodesCreated++;
            }

            if (row.sourceSupplierId() != null && !row.sourceSupplierId().isBlank()) {
                String key = linkKey(row.sourceSupplierId(), row.targetSupplierId(), row.hsCode());
                if (!existingLinkKeys.contains(key)) {
                    SupplierLink link = SupplierLink.builder()
                            .fromSupplierId(row.sourceSupplierId())
                            .toSupplierId(row.targetSupplierId())
                            .hsCode(row.hsCode())
                            .linkType(row.linkType())
                            .confidence(row.confidence())
                            .annualSpendInr(row.annualSpendInr())
                            .costFraction(row.costFraction())
                            .dataClass(dataClass)
                            .evidenceIds(row.evidenceIds())
                            .createdAt(now)
                            .build();
                    linkRepo.save(link);
                    existingLinkKeys.add(key);
                    linksCreated++;
                }
            }
        }

        String scenarioId = "SCENARIO-CSV-IMPORT-" + now.getEpochSecond();
        auditService.logEvent(
                scenarioId,
                "CSV_IMPORT",
                "system",
                "SupplyChain",
                scenarioId,
                String.format("Imported %d rows (%d nodes created, %d links created, mode=%s)",
                        dataLines.size(), nodesCreated, linksCreated, normalizedMode)
        );

        log.info("CSV import completed: rows={}, nodesCreated={}, linksCreated={}, mode={}",
                dataLines.size(), nodesCreated, linksCreated, normalizedMode);

        return new CsvImportResponse(
                "IMPORT_SUCCESS",
                dataLines.size(),
                nodesCreated,
                linksCreated,
                scenarioId,
                now
        );
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "replace";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!"replace".equals(normalized) && !"append".equals(normalized)) {
            throw new IllegalArgumentException("Invalid import mode: '" + mode + "'. Must be 'replace' or 'append'.");
        }
        return normalized;
    }

    private ValidatedRow validateRow(
            ParsedLine line,
            Map<String, Integer> headerIndex,
            List<CsvRowValidationError> errors) {

        int rowNum = line.lineNumber();
        List<String> fields = line.fields();
        int initialErrorCount = errors.size();

        String parentPartCode = getField(fields, headerIndex.get("parent_part_code"));
        String childPartCode = getField(fields, headerIndex.get("child_part_code"));
        String sourceSupplierId = getField(fields, headerIndex.get("source_supplier_id"));
        String targetSupplierId = getField(fields, headerIndex.get("target_supplier_id"));
        String supplierName = getField(fields, headerIndex.get("supplier_name"));
        String country = getField(fields, headerIndex.get("country"));
        String tierRaw = getField(fields, headerIndex.get("tier"));
        String linkTypeRaw = getField(fields, headerIndex.get("link_type"));
        String confidenceRaw = getField(fields, headerIndex.get("confidence"));
        String annualSpendRaw = getField(fields, headerIndex.get("annual_spend_inr"));
        String costFractionRaw = getField(fields, headerIndex.get("cost_fraction"));
        String hsCode = getField(fields, headerIndex.get("hs_code"));
        String leadTimeRaw = getField(fields, headerIndex.get("lead_time_days"));
        String evidenceIds = getField(fields, headerIndex.get("evidence_ids"));

        requireNonBlank(parentPartCode, rowNum, "parent_part_code", errors);
        requireNonBlank(targetSupplierId, rowNum, "target_supplier_id", errors);
        requireNonBlank(supplierName, rowNum, "supplier_name", errors);
        requireNonBlank(country, rowNum, "country", errors);

        checkMaxLength(targetSupplierId, 64, rowNum, "target_supplier_id", errors);
        checkMaxLength(sourceSupplierId, 64, rowNum, "source_supplier_id", errors);
        checkMaxLength(supplierName, 255, rowNum, "supplier_name", errors);
        checkMaxLength(country, 64, rowNum, "country", errors);
        checkMaxLength(hsCode, 32, rowNum, "hs_code", errors);
        checkMaxLength(evidenceIds, 1000, rowNum, "evidence_ids", errors);

        Integer tier = parseNonNegativeInt(tierRaw, rowNum, "tier", errors);
        LinkType linkType = parseLinkType(linkTypeRaw, rowNum, errors);
        BigDecimal confidence = parseDecimalRange(confidenceRaw, BigDecimal.ZERO, BigDecimal.ONE, 2,
                rowNum, "confidence", "Confidence must be between 0.0 and 1.0", errors);
        BigDecimal annualSpendInr = parseNonNegativeDecimal(annualSpendRaw, 2, rowNum, "annual_spend_inr", errors);
        BigDecimal costFraction = parseDecimalRange(costFractionRaw, BigDecimal.ZERO, BigDecimal.ONE, 2,
                rowNum, "cost_fraction", "cost_fraction must be between 0.0 and 1.0", errors);
        Integer leadTimeDays = parseNonNegativeInt(leadTimeRaw, rowNum, "lead_time_days", errors);

        if (errors.size() > initialErrorCount) {
            return null;
        }

        return new ValidatedRow(
                parentPartCode,
                childPartCode,
                sourceSupplierId,
                targetSupplierId,
                supplierName,
                country,
                tier,
                linkType,
                confidence,
                annualSpendInr,
                costFraction,
                hsCode,
                leadTimeDays,
                evidenceIds
        );
    }

    private void requireNonBlank(String val, int line, String col, List<CsvRowValidationError> errors) {
        if (val == null || val.isBlank()) {
            errors.add(new CsvRowValidationError(line, col, val != null ? val : "", col + " must not be blank"));
        }
    }

    private void checkMaxLength(String val, int maxLen, int line, String col, List<CsvRowValidationError> errors) {
        if (val != null && val.length() > maxLen) {
            errors.add(new CsvRowValidationError(line, col, val, col + " exceeds maximum length of " + maxLen));
        }
    }

    private Integer parseNonNegativeInt(String raw, int line, String col, List<CsvRowValidationError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new CsvRowValidationError(line, col, "", col + " must be a non-negative integer"));
            return null;
        }
        try {
            int val = Integer.parseInt(raw.trim());
            if (val < 0) {
                errors.add(new CsvRowValidationError(line, col, raw, col + " must be >= 0"));
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            errors.add(new CsvRowValidationError(line, col, raw, "Invalid integer for " + col));
            return null;
        }
    }

    private LinkType parseLinkType(String raw, int line, List<CsvRowValidationError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new CsvRowValidationError(line, "link_type", "", "Invalid link_type. Must be DECLARED, INFERRED, or ASSUMED"));
            return null;
        }
        try {
            return LinkType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(new CsvRowValidationError(line, "link_type", raw, "Invalid link_type. Must be DECLARED, INFERRED, or ASSUMED"));
            return null;
        }
    }

    private BigDecimal parseDecimalRange(
            String raw,
            BigDecimal min,
            BigDecimal max,
            int scale,
            int line,
            String col,
            String rangeMsg,
            List<CsvRowValidationError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new CsvRowValidationError(line, col, "", col + " is required"));
            return null;
        }
        try {
            BigDecimal val = new BigDecimal(raw.trim());
            if (val.compareTo(min) < 0 || val.compareTo(max) > 0) {
                errors.add(new CsvRowValidationError(line, col, raw, rangeMsg));
                return null;
            }
            return val.setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errors.add(new CsvRowValidationError(line, col, raw, "Invalid decimal for " + col));
            return null;
        }
    }

    private BigDecimal parseNonNegativeDecimal(String raw, int scale, int line, String col, List<CsvRowValidationError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new CsvRowValidationError(line, col, "", col + " is required"));
            return null;
        }
        try {
            BigDecimal val = new BigDecimal(raw.trim());
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new CsvRowValidationError(line, col, raw, col + " must be >= 0"));
                return null;
            }
            return val.setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errors.add(new CsvRowValidationError(line, col, raw, "Invalid decimal for " + col));
            return null;
        }
    }

    private DataClass mapDataClass(LinkType linkType) {
        return switch (linkType) {
            case DECLARED -> DataClass.OBSERVED;
            case INFERRED -> DataClass.ESTIMATED;
            case ASSUMED -> DataClass.ASSUMED;
        };
    }

    private String linkKey(String from, String to, String hsCode) {
        return (from != null ? from : "") + "|" + (to != null ? to : "") + "|" + (hsCode != null ? hsCode : "");
    }

    private Map<String, Integer> mapHeaders(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            map.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return map;
    }

    private String getField(List<String> fields, Integer idx) {
        if (idx == null || idx < 0 || idx >= fields.size()) {
            return "";
        }
        return fields.get(idx).trim();
    }

    /**
     * Minimal RFC 4180 CSV parser supporting quoted fields, escaped quotes (""),
     * embedded commas, and LF/CRLF line endings.
     */
    private List<ParsedLine> parseLines(String content) {
        String clean = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<ParsedLine> result = new ArrayList<>();
        List<String> currentFields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int lineNumber = 1;
        int recordStartLine = 1;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < clean.length() && clean.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        lineNumber++;
                    }
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentFields.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r' || c == '\n') {
                    if (c == '\r' && i + 1 < clean.length() && clean.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentFields.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isBlankRow(currentFields)) {
                        result.add(new ParsedLine(recordStartLine, currentFields));
                    }
                    currentFields = new ArrayList<>();
                    lineNumber++;
                    recordStartLine = lineNumber;
                } else {
                    currentField.append(c);
                }
            }
        }

        if (currentField.length() > 0 || !currentFields.isEmpty()) {
            currentFields.add(currentField.toString());
            if (!isBlankRow(currentFields)) {
                result.add(new ParsedLine(recordStartLine, currentFields));
            }
        }

        return result;
    }

    private boolean isBlankRow(List<String> fields) {
        return fields.stream().allMatch(f -> f == null || f.isBlank());
    }

    private record ParsedLine(int lineNumber, List<String> fields) {}

    private record ValidatedRow(
            String parentPartCode,
            String childPartCode,
            String sourceSupplierId,
            String targetSupplierId,
            String supplierName,
            String country,
            int tier,
            LinkType linkType,
            BigDecimal confidence,
            BigDecimal annualSpendInr,
            BigDecimal costFraction,
            String hsCode,
            int leadTimeDays,
            String evidenceIds
    ) {}
}
