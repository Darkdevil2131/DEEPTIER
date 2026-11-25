package com.deeptier.engine;

import com.deeptier.api.dto.CsvImportResponse;
import com.deeptier.approval.CsvValidationException;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import com.deeptier.seed.SeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CsvImportServiceTest {

    @Autowired CsvImportService csvImportService;
    @Autowired SupplierNodeRepository nodeRepo;
    @Autowired SupplierLinkRepository linkRepo;
    @Autowired SeedGenerator seedGenerator;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
    }

    @Test
    @DisplayName("Valid CSV in replace mode imports all nodes and links atomically")
    void validCsvReplaceMode() {
        String csv = """
                parent_part_code,child_part_code,source_supplier_id,target_supplier_id,supplier_name,country,tier,link_type,confidence,annual_spend_inr,cost_fraction,hs_code,lead_time_days,evidence_ids
                HPH-1042,HPH-1042,,OEM-01,"Pune Assembly OEM",IN,0,DECLARED,1.00,0,0.00,7325,0,EV-01
                HPH-1042,SUB-01,OEM-01,SUP-T1-01,"Coimbatore Precision",IN,1,DECLARED,0.90,2500000,1.00,7325,15,EV-02
                HPH-1042,RAW-01,SUP-T1-01,SUP-T2-01,"Ningbo Foundry",CN,2,INFERRED,0.65,2500000,0.50,7325,30,EV-03
                """;

        CsvImportResponse response = csvImportService.importCsv(csv, "replace");

        assertThat(response.status()).isEqualTo("IMPORT_SUCCESS");
        assertThat(response.totalRowsProcessed()).isEqualTo(3);
        assertThat(response.nodesCreated()).isEqualTo(3);
        assertThat(response.linksCreated()).isEqualTo(2);
        assertThat(nodeRepo.count()).isEqualTo(3);
        assertThat(linkRepo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Append mode avoids duplicating existing nodes and links on repeated upload")
    void appendModeIdempotentOnRepeatedUpload() {
        String csv = """
                parent_part_code,child_part_code,source_supplier_id,target_supplier_id,supplier_name,country,tier,link_type,confidence,annual_spend_inr,cost_fraction,hs_code,lead_time_days,evidence_ids
                HPH-1042,SUB-99,NODE-OEM-HPH,SUP-NEW-99,"Belgaum Castings",IN,1,DECLARED,0.90,1000000,0.40,7325,10,EV-NEW-99
                """;

        CsvImportResponse first = csvImportService.importCsv(csv, "append");
        assertThat(first.nodesCreated()).isEqualTo(1);
        assertThat(first.linksCreated()).isEqualTo(1);
        assertThat(nodeRepo.count()).isEqualTo(6);

        CsvImportResponse second = csvImportService.importCsv(csv, "append");
        assertThat(second.nodesCreated()).isZero();
        assertThat(second.linksCreated()).isZero();
        assertThat(nodeRepo.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("Invalid CSV row rolls back transaction and reports exact line and column errors")
    void invalidCsvRollsBackAndReportsLineErrors() {
        long initialNodes = nodeRepo.count();
        long initialLinks = linkRepo.count();

        String badCsv = """
                parent_part_code,child_part_code,source_supplier_id,target_supplier_id,supplier_name,country,tier,link_type,confidence,annual_spend_inr,cost_fraction,hs_code,lead_time_days,evidence_ids
                HPH-1042,SUB-01,OEM-01,SUP-VALID,"Valid Supplier",IN,1,DECLARED,0.90,1000000,0.50,7325,10,EV-01
                HPH-1042,SUB-02,SUP-VALID,SUP-BAD,"Bad Confidence",CN,2,UNKNOWN_LINK,1.45,1000000,0.50,7325,20,EV-02
                """;

        assertThatThrownBy(() -> csvImportService.importCsv(badCsv, "replace"))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(ex -> {
                    CsvValidationException cve = (CsvValidationException) ex;
                    assertThat(cve.getErrors()).hasSize(2);
                    assertThat(cve.getErrors()).anyMatch(e -> e.line() == 3 && "link_type".equals(e.column()));
                    assertThat(cve.getErrors()).anyMatch(e -> e.line() == 3 && "confidence".equals(e.column()));
                });

        // Database must remain untouched (5 nodes, 4 links from seed)
        assertThat(nodeRepo.count()).isEqualTo(initialNodes);
        assertThat(linkRepo.count()).isEqualTo(initialLinks);
    }
}
