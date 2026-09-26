package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.FundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundRecordRepository extends JpaRepository<FundRecord, Long> {

    List<FundRecord> findByProject_IdOrderByIdAsc(Long projectId);
}
