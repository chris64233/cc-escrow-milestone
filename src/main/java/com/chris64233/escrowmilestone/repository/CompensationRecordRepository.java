package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.CompensationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, Long> {

    List<CompensationRecord> findByPayout_IdOrderByIdAsc(Long payoutId);
}
