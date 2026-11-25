package com.deeptier.repo;

import com.deeptier.domain.BackupSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackupSupplierRepository extends JpaRepository<BackupSupplier, String> {
    List<BackupSupplier> findByPartCodeAndIsVettedTrueOrderByUnitCostInrAsc(String partCode);
    List<BackupSupplier> findByIsVettedTrueOrderByUnitCostInrAsc();
}
