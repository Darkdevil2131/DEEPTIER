package com.deeptier.api;

import com.deeptier.security.JwtService;
import com.deeptier.seed.SeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatIfControllerTest {

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
    void simulateHphScenarioReturnsExpectedNumbers() throws Exception {
        String body = """
                {
                  "scenarioId": "HPH-TEST-001",
                  "hsCode": "7325",
                  "originCountry": "CN",
                  "deltaPercentagePoints": 20,
                  "annualSpendInr": 4800000,
                  "costFraction": 0.60,
                  "unitsOnHand": 300,
                  "dailyDemand": 20,
                  "leadTimeDays": 30
                }
                """;
        mvc.perform(post("/api/whatif/simulate")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tariffShock.totalExposureInr").value(576000.0))
                .andExpect(jsonPath("$.inventory.gapDays").value(15))
                .andExpect(jsonPath("$.monteCarlo.trials").value(10000))
                .andExpect(jsonPath("$.recoveryOptions").isArray());
    }
}
