package com.deeptier.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String> {
    List<ApprovalRequest> findAllByOrderByCreatedAtDesc();
    List<ApprovalRequest> findByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus approvalStatus);
}
