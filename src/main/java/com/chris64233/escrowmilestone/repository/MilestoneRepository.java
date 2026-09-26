package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
}
