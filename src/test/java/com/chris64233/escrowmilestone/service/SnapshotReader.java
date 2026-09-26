package com.chris64233.escrowmilestone.service;

import com.chris64233.escrowmilestone.domain.ApprovalStatus;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.LedgerEntry;
import com.chris64233.escrowmilestone.domain.Milestone;
import com.chris64233.escrowmilestone.domain.MilestoneStatus;
import com.chris64233.escrowmilestone.repo.EscrowProjectRepository;
import com.chris64233.escrowmilestone.repo.LedgerEntryRepository;
import com.chris64233.escrowmilestone.repo.MilestoneRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 并发测试辅助：在独立只读事务中取快照，避免跨线程懒加载。 */
@Component
public class SnapshotReader {

    /** 脱离持久化会话使用的里程碑快照。 */
    public record MilestoneSnapshot(int sequence, MilestoneStatus status,
                                    ApprovalStatus pmApproval) {
    }

    private final EscrowProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public SnapshotReader(EscrowProjectRepository projectRepository,
                          MilestoneRepository milestoneRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public EscrowProject project(String code) {
        return projectRepository.findByCode(code).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger(String code) {
        Long id = projectId(code);
        return ledgerEntryRepository.findByProjectIdOrderByIdAsc(id);
    }

    @Transactional(readOnly = true)
    public List<MilestoneSnapshot> milestoneSnapshots(String code) {
        Long id = projectId(code);
        return milestoneRepository.findByProjectIdOrderBySequenceNoAsc(id).stream()
                .map(m -> new MilestoneSnapshot(m.getSequenceNo(), m.getStatus(),
                        m.getApprovalRequirements().stream()
                                .filter(r -> r.getRequiredRole().equals("PM"))
                                .map(r -> r.getStatus())
                                .findFirst()
                                .orElse(null)))
                .toList();
    }

    private Long projectId(String code) {
        return projectRepository.findByCode(code).orElseThrow().getId();
    }
}
