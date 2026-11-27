package com.deeptier.api;

import com.deeptier.security.JwtService;
import com.deeptier.seed.SeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupplyChainControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SeedGenerator seedGenerator;
    @Autowired JwtService jwtService;
    private String analystToken;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
        analystToken = jwtService.generateToken("analyst@deeptier.local", "ROLE_ANALYST");
    }

    @Test
    void multipartCsvUploadSucceeds() throws Exception {
        String csv = """
                parent_part_code,child_part_code,source_supplier_id,target_supplier_id,supplier_name,country,tier,link_type,confidence,annual_spend_inr,cost_fraction,hs_code,lead_time_days,evidence_ids
                HPH-1042,HPH-1042,,OEM-100,"Pune OEM",IN,0,DECLARED,1.00,0,0.00,7325,0,EV-100
                HPH-1042,SUB-100,OEM-100,SUP-100,"Coimbatore Machining",IN,1,DECLARED,0.90,3200000,1.00,7325,12,EV-101
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "supply-chain.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        mvc.perform(multipart("/api/supply-chain/import/csv")
                        .file(file)
                        .param("mode", "replace")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IMPORT_SUCCESS"))
                .andExpect(jsonPath("$.totalRowsProcessed").value(2))
                .andExpect(jsonPath("$.nodesCreated").value(2))
                .andExpect(jsonPath("$.linksCreated").value(1));
    }

    @Test
    void multipartCsvUploadWithInvalidRowReturns400ProblemDetail() throws Exception {
        String badCsv = """
                parent_part_code,child_part_code,source_supplier_id,target_supplier_id,supplier_name,country,tier,link_type,confidence,annual_spend_inr,cost_fraction,hs_code,lead_time_days,evidence_ids
                HPH-1042,SUB-100,OEM-100,SUP-100,"Coimbatore Machining",IN,1,DECLARED,1.50,3200000,1.00,7325,12,EV-101
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.csv",
                "text/csv",
                badCsv.getBytes(StandardCharsets.UTF_8)
        );

        mvc.perform(multipart("/api/supply-chain/import/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CSV_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].column").value("confidence"));
    }
}
