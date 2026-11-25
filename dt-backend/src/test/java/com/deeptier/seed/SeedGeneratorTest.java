package com.deeptier.seed;

import com.deeptier.repo.AuditEventRepository;
import com.deeptier.repo.BackupSupplierRepository;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SeedGeneratorTest {

    @Autowired SeedGenerator seedGenerator;
    @Autowired SupplierNodeRepository nodeRepo;
    @Autowired SupplierLinkRepository linkRepo;
    @Autowired BackupSupplierRepository backupSupplierRepo;
    @Autowired AuditEventRepository auditRepo;

    @Test
    void resetLoads5Nodes4Links4BackupSuppliers() {
        seedGenerator.reset(20261121L);

        assertThat(nodeRepo.count()).isEqualTo(5);
        assertThat(linkRepo.count()).isEqualTo(4);
        assertThat(backupSupplierRepo.count()).isEqualTo(4);
        assertThat(auditRepo.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void stableNodeIdsPresent() {
        seedGenerator.reset(20261121L);

        assertThat(nodeRepo.findById(SeedGenerator.NODE_OEM)).isPresent();
        assertThat(nodeRepo.findById(SeedGenerator.NODE_ALPHA)).isPresent();
        assertThat(nodeRepo.findById(SeedGenerator.NODE_BETA)).isPresent();
        assertThat(nodeRepo.findById(SeedGenerator.NODE_RAJKOT)).isPresent();
        assertThat(nodeRepo.findById(SeedGenerator.NODE_ROTTERDAM)).isPresent();
    }

    @Test
    void linkFromAlphaToBetaHasCostFraction60Percent() {
        seedGenerator.reset(20261121L);

        var link = linkRepo.findById(SeedGenerator.LINK_ALPHA_BETA);
        assertThat(link).isPresent();
        assertThat(link.get().getCostFraction()).isEqualByComparingTo("0.60");
    }

    @Test
    void genesisAuditEventHasCorrectPrevHash() {
        seedGenerator.reset(20261121L);

        var events = auditRepo.findAll();
        assertThat(events).isNotEmpty();
        var genesis = events.stream()
                .filter(e -> "SEED_RESET".equals(e.getEventType()))
                .findFirst();
        assertThat(genesis).isPresent();
        assertThat(genesis.get().getPrevHash()).isEqualTo("0");
    }

    @Test
    void doubleResetIsIdempotent() {
        seedGenerator.reset(20261121L);
        seedGenerator.reset(20261121L);

        assertThat(nodeRepo.count()).isEqualTo(5);
        assertThat(linkRepo.count()).isEqualTo(4);
        assertThat(backupSupplierRepo.count()).isEqualTo(4);
    }
}
