package com.deeptier.repo;

import com.deeptier.domain.SupplierNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierNodeRepository extends JpaRepository<SupplierNode, String> {
    List<SupplierNode> findBySupplierId(String supplierId);
    List<SupplierNode> findByTier(int tier);
}
