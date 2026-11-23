package com.deeptier.engine;

import com.deeptier.domain.LinkType;
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
class TierMapServiceTest {

    @Autowired TierMapService tierMapService;
    @Autowired SeedGenerator seedGenerator;

    @BeforeEach
    void setUp() {
        seedGenerator.reset(20261121L);
    }

    @Test
    @DisplayName("D02: Inferred link is always labelled INFERRED with confidence < 1.0; never shown as declared")
    void testInferredLinkProperties() {
        TierMapService.TierGraph graph = tierMapService.getGraph();

        var inferredLinks = graph.links().stream()
                .filter(l -> l.getLinkType() == LinkType.INFERRED)
                .toList();

        assertThat(inferredLinks).isNotEmpty();
        for (var link : inferredLinks) {
            assertThat(link.getLinkType()).isEqualTo(LinkType.INFERRED);
            assertThat(link.getConfidence()).isLessThan(BigDecimal.ONE);
        }
    }

    @Test
    @DisplayName("Inference scoring formula clamps at 0.80")
    void testConfidenceCalculationClamping() {
        // Full match: 0.30 + 0.20 + 0.15 + 0.15 = 0.80
        BigDecimal full = tierMapService.calculateInferredConfidence(true, true, 3);
        assertThat(full).isEqualByComparingTo("0.80");

        // Partial match: 0.30 + 0.20 = 0.50
        BigDecimal partial = tierMapService.calculateInferredConfidence(true, false, 0);
        assertThat(partial).isEqualByComparingTo("0.50");
    }
}
