package com.chris64233.escrowmilestone.repo;

import com.chris64233.escrowmilestone.domain.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, Long> {
}
