package com.deeptier.repo;

import com.deeptier.domain.SupplierLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierLinkRepository extends JpaRepository<SupplierLink, String> {
    List<SupplierLink> findByFromSupplierId(String fromSupplierId);
    List<SupplierLink> findByToSupplierId(String toSupplierId);
    List<SupplierLink> findByHsCode(String hsCode);
}
