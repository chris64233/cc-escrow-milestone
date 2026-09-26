package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByMilestone_IdOrderByDecidedAtAsc(Long milestoneId);
}
