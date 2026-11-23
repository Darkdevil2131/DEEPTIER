package com.deeptier.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockRfqRepository extends JpaRepository<MockRfq, String> {
    Optional<MockRfq> findByApprovalRequestId(String approvalRequestId);
    List<MockRfq> findAllByOrderByIssuedAtDesc();
}
