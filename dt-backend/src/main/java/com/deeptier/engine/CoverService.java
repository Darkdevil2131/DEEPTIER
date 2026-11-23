package com.deeptier.engine;

import com.deeptier.domain.DataClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Deterministic Inventory Cover & Stockout Calculation.
 *
 * Rules:
 *  - cover_days = on_hand_qty / daily_demand
 *  - stockout_date = today + floor(cover_days)
 *  - gap_days = max(0, replenishment_lead_days - cover_days)
 *  - expected_late_loss_inr = gap_days * late_cost_inr_per_day
 *  - If daily_demand is zero or null: cover is UNKNOWN (never divide by zero).
 */
@Service
public class CoverService {

    public record CoverResult(
            int unitsOnHand,
            Integer dailyDemand,
            int replenishmentLeadDays,
            Double coverDays,
            String coverDaysDisplay,
            LocalDate stockoutDate,
            int gapDays,
            BigDecimal lateCostPerDayInr,
            BigDecimal expectedLateLossInr,
            DataClass dataClass,
            String note
    ) {}

    public CoverResult calculateCover(
            int unitsOnHand,
            Integer dailyDemand,
            int replenishmentLeadDays,
            BigDecimal lateCostPerDayInr,
            LocalDate referenceDate) {

        LocalDate baseDate = referenceDate != null ? referenceDate : LocalDate.now();
        BigDecimal dailyLoss = lateCostPerDayInr != null ? lateCostPerDayInr : BigDecimal.valueOf(80_000);

        if (dailyDemand == null || dailyDemand <= 0) {
            return new CoverResult(
                    unitsOnHand,
                    dailyDemand,
                    replenishmentLeadDays,
                    null,
                    "UNKNOWN",
                    null,
                    replenishmentLeadDays,
                    dailyLoss,
                    dailyLoss.multiply(BigDecimal.valueOf(replenishmentLeadDays)),
                    DataClass.ASSUMED,
                    "Zero or missing daily demand: days of cover is UNKNOWN"
            );
        }

        double cover = (double) unitsOnHand / dailyDemand;
        int floorCover = (int) Math.floor(cover);
        LocalDate stockout = baseDate.plusDays(floorCover);
        int gap = (int) Math.max(0, replenishmentLeadDays - cover);
        BigDecimal lateLoss = dailyLoss.multiply(BigDecimal.valueOf(gap));

        return new CoverResult(
                unitsOnHand,
                dailyDemand,
                replenishmentLeadDays,
                BigDecimal.valueOf(cover).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                String.format("%.1f", cover),
                stockout,
                gap,
                dailyLoss,
                lateLoss,
                DataClass.SYNTHETIC,
                "Cover calculated deterministically"
        );
    }
}
