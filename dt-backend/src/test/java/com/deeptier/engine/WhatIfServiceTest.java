package com.deeptier.engine;

import com.deeptier.seed.SeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WhatIfServiceTest {

    @Autowired WhatIfService whatIfService;
    @Autowired SeedGenerator seedGenerator;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
    }

    @Test
    @DisplayName("End-to-end simulation returns in < 2000 ms with all engine results populated")
    void testWhatIfExecutionUnder2Seconds() {
        long start = System.currentTimeMillis();

        WhatIfService.WhatIfSimulationResponse response = whatIfService.simulate(
                "TEST-HPH-001", "7325", "CN", BigDecimal.valueOf(20),
                BigDecimal.valueOf(4_800_000), BigDecimal.valueOf(0.60),
                300, 20, 30, BigDecimal.valueOf(80_000), 20261121L
        );

        long duration = System.currentTimeMillis() - start;

        assertThat(duration).isLessThan(2000);
        assertThat(response.tariffShock().totalExposureInr()).isEqualByComparingTo("576000.00");
        assertThat(response.inventory().gapDays()).isEqualTo(15);
        assertThat(response.recoveryOptions()).isNotEmpty();
        assertThat(response.recoveryOptions().get(0).recommended()).isTrue();
        assertThat(response.monteCarlo().trials()).isEqualTo(10_000);
    }
}
