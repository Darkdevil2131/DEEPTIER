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
class ExposureServiceTest {

    @Autowired ExposureService exposureService;
    @Autowired SeedGenerator seedGenerator;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
    }

    @Test
    @DisplayName("D01: Tariff event on HS code with no matching parts yields zero exposure, no error")
    void testNonMatchingHsCodeYieldsZeroExposure() {
        ExposureService.ExposureSummary result = exposureService.calculateExposure(
                "9999", "CN", BigDecimal.valueOf(25), BigDecimal.valueOf(100));

        assertThat(result.totalExposureInr()).isEqualByComparingTo("0.00");
        assertThat(result.lineItems()).isEmpty();
    }

    @Test
    @DisplayName("HPH Scenario: HS 7325 from CN +20pp -> 0.60 * 4.8M * 0.20 = ?5,76,000")
    void testHphScenarioExposure() {
        ExposureService.ExposureSummary result = exposureService.calculateExposure(
                "7325", "CN", BigDecimal.valueOf(20), BigDecimal.valueOf(100));

        assertThat(result.totalExposureInr()).isEqualByComparingTo("576000.00");
        assertThat(result.lineItems()).isNotEmpty();
        assertThat(result.lineItems().get(0).dutyDeltaInr()).isEqualByComparingTo("576000.00");
    }

    @Test
    @DisplayName("D03: Exposure total equals sum of per-part line items")
    void testTotalEqualsSumOfLines() {
        ExposureService.ExposureSummary result = exposureService.calculateExposure(
                "7325", "CN", BigDecimal.valueOf(20), BigDecimal.valueOf(100));

        BigDecimal sum = result.lineItems().stream()
                .map(ExposureService.PartExposure::dutyDeltaInr)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(result.totalExposureInr()).isEqualByComparingTo(sum);
    }

    @Test
    @DisplayName("D06: Higher tariff rate -> exposure never decreases")
    void testHigherRateMonotonicity() {
        ExposureService.ExposureSummary rate10 = exposureService.calculateExposure(
                "7325", "CN", BigDecimal.valueOf(10), BigDecimal.valueOf(100));
        ExposureService.ExposureSummary rate20 = exposureService.calculateExposure(
                "7325", "CN", BigDecimal.valueOf(20), BigDecimal.valueOf(100));
        ExposureService.ExposureSummary rate35 = exposureService.calculateExposure(
                "7325", "CN", BigDecimal.valueOf(35), BigDecimal.valueOf(100));

        assertThat(rate20.totalExposureInr()).isGreaterThan(rate10.totalExposureInr());
        assertThat(rate35.totalExposureInr()).isGreaterThan(rate20.totalExposureInr());
    }
}
