package com.chris64233.escrowmilestone.repo;

import com.chris64233.escrowmilestone.domain.LedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByProjectIdOrderByIdAsc(Long projectId);

    List<LedgerEntry> findByDisbursementIdOrderByIdAsc(Long disbursementId);
}
