package com.deeptier.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizerServiceTest {

    private final OptimizerService optimizerService = new OptimizerService();

    @Test
    @DisplayName("D07: Buy-ahead when lead time (30d) exceeds tariff window (14d) is rejected with reason code")
    void testBuyAheadRejectedWhenLeadTimeExceedsWindow() {
        OptimizerService.OptimizationResult result = optimizerService.optimize(
                "SCENARIO-HPH", 15, BigDecimal.valueOf(80_000),
                BigDecimal.valueOf(576_000), 14, 30, "CN", "IN");

        assertThat(result.rejectedOptions()).anyMatch(r ->
                "ORDER_LEAD_TIME_EXCEEDS_TARIFF_EFFECTIVE_DATE".equals(r.reasonCode()) &&
                "BUY_AHEAD".equals(r.optionType()));
    }

    @Test
    @DisplayName("T03: Direct road-only route from UAE to India is rejected with NO_CROSS_BORDER_ROAD_LINK")
    void testImpossibleUaeToIndiaRoadRejected() {
        OptimizerService.OptimizationResult result = optimizerService.optimize(
                "SCENARIO-UAE", 10, BigDecimal.valueOf(80_000),
                BigDecimal.valueOf(200_000), 30, 15, "AE", "IN");

        assertThat(result.rejectedOptions()).anyMatch(r ->
                "NO_CROSS_BORDER_ROAD_LINK".equals(r.reasonCode()));
    }

    @Test
    @DisplayName("Option Ranking: Feasible options are sorted by totalImpactInr ascending (Rank 1 is recommended)")
    void testOptionRankingOrder() {
        OptimizerService.OptimizationResult result = optimizerService.optimize(
                "SCENARIO-HPH", 15, BigDecimal.valueOf(80_000),
                BigDecimal.valueOf(576_000), 14, 30, "CN", "IN");

        assertThat(result.feasibleOptions()).isNotEmpty();
        assertThat(result.feasibleOptions().get(0).recommended()).isTrue();
        assertThat(result.feasibleOptions().get(0).rank()).isEqualTo(1);

        for (int i = 0; i < result.feasibleOptions().size() - 1; i++) {
            BigDecimal current = result.feasibleOptions().get(i).totalImpactInr();
            BigDecimal next = result.feasibleOptions().get(i + 1).totalImpactInr();
            assertThat(current).isLessThanOrEqualTo(next);
        }
    }
}
