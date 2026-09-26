package com.chris64233.escrowmilestone.repo;

import com.chris64233.escrowmilestone.domain.IdempotentRequest;
import com.chris64233.escrowmilestone.domain.IdempotentRequestType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, Long> {

    Optional<IdempotentRequest> findByRequestTypeAndBusinessNo(IdempotentRequestType requestType,
                                                               String businessNo);
}
