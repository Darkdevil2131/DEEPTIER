package com.deeptier.engine;

import com.deeptier.domain.DataClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CoverServiceTest {

    private final CoverService coverService = new CoverService();
    private final LocalDate today = LocalDate.of(2026, 11, 28);
    private final BigDecimal dailyLoss = BigDecimal.valueOf(80_000);

    @Test
    @DisplayName("D04: Zero daily demand yields UNKNOWN cover and no division by zero")
    void testZeroDemandYieldsUnknown() {
        CoverService.CoverResult result = coverService.calculateCover(
                300, 0, 30, dailyLoss, today);

        assertThat(result.coverDays()).isNull();
        assertThat(result.coverDaysDisplay()).isEqualTo("UNKNOWN");
        assertThat(result.stockoutDate()).isNull();
        assertThat(result.dataClass()).isEqualTo(DataClass.ASSUMED);
    }

    @Test
    @DisplayName("HPH Scenario: 300 units, 20/day demand -> 15 days cover, 15 days gap, ?12,00,000 late loss")
    void testHphScenarioCover() {
        CoverService.CoverResult result = coverService.calculateCover(
                300, 20, 30, dailyLoss, today);

        assertThat(result.coverDays()).isEqualTo(15.0);
        assertThat(result.coverDaysDisplay()).isEqualTo("15.0");
        assertThat(result.stockoutDate()).isEqualTo(today.plusDays(15));
        assertThat(result.gapDays()).isEqualTo(15);
        assertThat(result.expectedLateLossInr()).isEqualByComparingTo("1200000");
    }

    @Test
    @DisplayName("Adequate stock: 600 units, 20/day demand, 30-day lead -> 0 gap days, 0 late loss")
    void testAdequateInventoryNoGap() {
        CoverService.CoverResult result = coverService.calculateCover(
                600, 20, 30, dailyLoss, today);

        assertThat(result.coverDays()).isEqualTo(30.0);
        assertThat(result.gapDays()).isEqualTo(0);
        assertThat(result.expectedLateLossInr()).isEqualByComparingTo("0");
    }
}
