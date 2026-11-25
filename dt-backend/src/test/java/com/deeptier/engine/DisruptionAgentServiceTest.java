package com.deeptier.engine;

import com.deeptier.api.dto.AnalyzeDisruptionRequest;
import com.deeptier.api.dto.AnalyzeDisruptionResponse;
import com.deeptier.audit.AuditEvent;
import com.deeptier.audit.AuditService;
import com.deeptier.domain.DisruptionEvent;
import com.deeptier.domain.LinkType;
import com.deeptier.domain.SupplierLink;
import com.deeptier.domain.SupplierNode;
import com.deeptier.repo.AuditEventRepository;
import com.deeptier.repo.BackupSupplierRepository;
import com.deeptier.repo.DisruptionEventRepository;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import com.deeptier.sap.AgentGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisruptionAgentServiceTest {

    @Test
    void usesPythonBrainResultAndExecutesJavaMath() {
        AgentGateway gateway = new AgentGateway() {
            @Override
            public Optional<DeepTierBrainResult> analyzeDisruption(String alertText, String partCode, String scenarioId) {
                return Optional.of(new DeepTierBrainResult(
                        "7325",
                        "CN",
                        BigDecimal.valueOf(25),
                        7,
                        "TARIFF_SHOCK",
                        BigDecimal.valueOf(0.92),
                        "gemini-2.5-flash",
                        "Python grounded brief citing EV-BOM-001",
                        List.of("EV-BOM-001")
                ));
            }
            @Override public String classifySignal(String signalText, String hsCode) { return "{}"; }
            @Override public String explainResults(String whatIfResultJson) { return ""; }
            @Override public String draftRfq(String context) { return ""; }
            @Override public String runtimeName() { return "python"; }
        };

        DisruptionEventRepository repo = mock(DisruptionEventRepository.class);
        when(repo.save(any(DisruptionEvent.class))).thenAnswer(inv -> {
            DisruptionEvent ev = inv.getArgument(0);
            ev.setId("EV-123");
            return ev;
        });

        AuditEventRepository auditRepo = mock(AuditEventRepository.class);
        when(auditRepo.findFirstByOrderByOccurredAtDesc()).thenReturn(Optional.empty());
        when(auditRepo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        DisruptionAgentService service = new DisruptionAgentService(
                gateway, realWhatIfService(), repo, new AuditService(auditRepo));

        AnalyzeDisruptionResponse res = service.analyzeDisruption(new AnalyzeDisruptionRequest(
                "25% tariff hike on HS 7325 castings from China with 7 days delay",
                "HPH-1042", "SCEN-1", 300, 20, 30, null, null, null, null));

        assertEquals("EV-123", res.eventId());
        assertEquals("gemini-2.5-flash", res.extractionEngine());
        assertEquals("7325", res.affectedHsCode());
        assertEquals(7, res.delayDays());
        assertEquals("Python grounded brief citing EV-BOM-001", res.executiveBrief());
        assertTrue(res.evidenceIds().contains("EV-BOM-001"));
        assertNotNull(res.simulation());
        assertTrue(res.simulation().tariffShock().totalExposureInr().compareTo(BigDecimal.ZERO) > 0);
        verify(auditRepo).save(any(AuditEvent.class));
    }

    @Test
    void fallsBackToDeterministicJavaWhenPythonOffline() {
        AgentGateway offlineGateway = new AgentGateway() {
            @Override
            public String classifySignal(String signalText, String hsCode) {
                return "{}";
            }

            @Override
            public String explainResults(String whatIfResultJson) {
                return "";
            }

            @Override
            public String draftRfq(String context) {
                return "";
            }

            @Override
            public String runtimeName() {
                return "python";
            }
        };

        DisruptionEventRepository repo = mock(DisruptionEventRepository.class);
        when(repo.save(any(DisruptionEvent.class))).thenAnswer(inv -> {
            DisruptionEvent ev = inv.getArgument(0);
            ev.setId("EV-FB-1");
            return ev;
        });

        AuditEventRepository auditRepo = mock(AuditEventRepository.class);
        when(auditRepo.findFirstByOrderByOccurredAtDesc()).thenReturn(Optional.empty());
        when(auditRepo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        DisruptionAgentService service = new DisruptionAgentService(
                offlineGateway, realWhatIfService(), repo, new AuditService(auditRepo));

        AnalyzeDisruptionResponse res = service.analyzeDisruption(new AnalyzeDisruptionRequest(
                "Urgent: 20% customs duty on HS 7325 castings from China causing 5 days delay",
                "HPH-1042", null, 300, 20, 30, null, null, null, null));

        assertEquals(DisruptionAgentService.JAVA_FALLBACK_ENGINE, res.extractionEngine());
        assertEquals("7325", res.affectedHsCode());
        assertEquals("CN", res.originCountry());
        assertEquals(0, new BigDecimal("20").compareTo(res.tariffDeltaPercent()));
        assertEquals(5, res.delayDays());
        assertNotNull(res.executiveBrief());
        assertTrue(res.executiveBrief().contains("JAVA_DETERMINISTIC_FALLBACK"));
        verify(auditRepo).save(any(AuditEvent.class));
    }

    private static WhatIfService realWhatIfService() {
        SupplierNodeRepository nodeRepo = mock(SupplierNodeRepository.class);
        SupplierLinkRepository linkRepo = mock(SupplierLinkRepository.class);
        BackupSupplierRepository backupRepo = mock(BackupSupplierRepository.class);

        SupplierNode beta = SupplierNode.builder()
                .id("SUP-NGB-BETA").name("Beta Foundry").tier(2).country("CN")
                .linkType(LinkType.INFERRED).confidence(new BigDecimal("0.60")).build();
        SupplierLink link = SupplierLink.builder()
                .id("LNK-1").fromSupplierId("SUP-CBE-ALPHA").toSupplierId("SUP-NGB-BETA")
                .hsCode("7325")
                .annualSpendInr(BigDecimal.valueOf(4_800_000))
                .costFraction(new BigDecimal("0.60"))
                .linkType(LinkType.INFERRED).confidence(new BigDecimal("0.60")).build();

        when(nodeRepo.findAll()).thenReturn(List.of(beta));
        when(linkRepo.findByHsCode("7325")).thenReturn(List.of(link));
        when(linkRepo.findAll()).thenReturn(List.of(link));
        when(backupRepo.findByPartCodeAndIsVettedTrueOrderByUnitCostInrAsc(any())).thenReturn(List.of());

        return new WhatIfService(
                new ExposureService(nodeRepo, linkRepo),
                new CoverService(),
                new MonteCarloService(),
                new OptimizerService(backupRepo));
    }
}
