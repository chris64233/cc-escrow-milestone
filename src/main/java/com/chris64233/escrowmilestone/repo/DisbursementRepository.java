package com.chris64233.escrowmilestone.repo;

import com.chris64233.escrowmilestone.domain.Disbursement;
import com.chris64233.escrowmilestone.domain.DisbursementStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisbursementRepository extends JpaRepository<Disbursement, Long> {

    Optional<Disbursement> findByBusinessNo(String businessNo);

    List<Disbursement> findByProjectIdOrderByIdAsc(Long projectId);

    List<Disbursement> findByMilestoneIdOrderByIdDesc(Long milestoneId);

    List<Disbursement> findByProjectIdAndStatusOrderByIdAsc(Long projectId, DisbursementStatus status);
}
