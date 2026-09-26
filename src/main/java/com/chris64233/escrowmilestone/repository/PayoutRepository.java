package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Optional<Payout> findByBizNo(String bizNo);

    List<Payout> findByProject_IdOrderByIdAsc(Long projectId);
}
