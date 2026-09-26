package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByMilestone_Id(Long milestoneId);
}
