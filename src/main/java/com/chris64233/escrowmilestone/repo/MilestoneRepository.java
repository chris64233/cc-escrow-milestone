package com.chris64233.escrowmilestone.repo;

import com.chris64233.escrowmilestone.domain.Milestone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByProjectIdOrderBySequenceNoAsc(Long projectId);

    Optional<Milestone> findByProjectIdAndSequenceNo(Long projectId, int sequenceNo);
}
