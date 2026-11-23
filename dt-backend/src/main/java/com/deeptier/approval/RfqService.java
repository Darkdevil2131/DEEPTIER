package com.deeptier.approval;

import com.deeptier.api.dto.MockRfqResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RfqService {

    private final MockRfqRepository mockRfqRepo;

    @Transactional(readOnly = true)
    public List<MockRfqResponse> listAll() {
        return mockRfqRepo.findAllByOrderByIssuedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    private MockRfqResponse toDto(MockRfq r) {
        return new MockRfqResponse(
                r.getId(),
                r.getApprovalRequestId(),
                r.getTargetSupplierId(),
                r.getPartCode(),
                r.getRequestedQty(),
                r.getQuotedLandedCost(),
                r.getExpectedLeadTimeDays(),
                r.getRfqStatus(),
                r.getIssuedBy(),
                r.getIssuedAt()
        );
    }
}
