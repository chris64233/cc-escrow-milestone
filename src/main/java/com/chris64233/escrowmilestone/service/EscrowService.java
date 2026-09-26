package com.chris64233.escrowmilestone.service;

import com.chris64233.escrowmilestone.domain.ApprovalDecision;
import com.chris64233.escrowmilestone.domain.ApprovalDecisionType;
import com.chris64233.escrowmilestone.domain.ApprovalRequirement;
import com.chris64233.escrowmilestone.domain.Disbursement;
import com.chris64233.escrowmilestone.domain.DisbursementStatus;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.IdempotentRequest;
import com.chris64233.escrowmilestone.domain.IdempotentRequestType;
import com.chris64233.escrowmilestone.domain.LedgerDirection;
import com.chris64233.escrowmilestone.domain.LedgerEntry;
import com.chris64233.escrowmilestone.domain.LedgerEntryType;
import com.chris64233.escrowmilestone.domain.Milestone;
import com.chris64233.escrowmilestone.domain.RequiredEvidence;
import com.chris64233.escrowmilestone.repo.ApprovalDecisionRepository;
import com.chris64233.escrowmilestone.repo.DisbursementRepository;
import com.chris64233.escrowmilestone.repo.EscrowProjectRepository;
import com.chris64233.escrowmilestone.repo.IdempotentRequestRepository;
import com.chris64233.escrowmilestone.repo.LedgerEntryRepository;
import com.chris64233.escrowmilestone.repo.MilestoneRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 托管资金里程碑放款领域服务。
 *
 * <p>并发模型：所有写操作在事务内先取托管项目行悲观写锁，同一项目的
 * 放款 / 撤销 / 结算 / 补偿 / 审批 / 证据操作严格串行化；
 * 余额扣减在持锁状态下做足��校验，{@code @Version} 乐观版本作为第二道防线。
 * 资金台账只追加，任何后续动作均不修改或删除既有流水。
 */
@Service
public class EscrowService {

    private final EscrowProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final DisbursementRepository disbursementRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ApprovalDecisionRepository decisionRepository;
    private final IdempotentRequestRepository idempotentRequestRepository;
    private final Clock clock;

    public EscrowService(EscrowProjectRepository projectRepository,
                         MilestoneRepository milestoneRepository,
                         DisbursementRepository disbursementRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         ApprovalDecisionRepository decisionRepository,
                         IdempotentRequestRepository idempotentRequestRepository,
                         Clock clock) {
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.disbursementRepository = disbursementRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.decisionRepository = decisionRepository;
        this.idempotentRequestRepository = idempotentRequestRepository;
        this.clock = clock;
    }

    // ---------------- 项目建立 ----------------

    @Transactional
    public EscrowProject createProject(Commands.CreateProject cmd) {
        if (projectRepository.existsByCode(cmd.code())) {
            throw new ConflictException("项目编码已存在：" + cmd.code());
        }
        if (cmd.milestones().isEmpty()) {
            throw new BusinessRuleException("托管项目至少包含一个里程碑");
        }

        Instant now = Instant.now(clock);
        EscrowProject project = new EscrowProject(cmd.code(), cmd.name(), cmd.totalAmount(),
                cmd.createdBy(), now);

        BigDecimal plannedSum = BigDecimal.ZERO;
        int sequence = 1;
        for (Commands.CreateMilestone m : cmd.milestones()) {
            Milestone milestone = new Milestone(project, sequence, m.name(), m.plannedAmount());
            project.addMilestone(milestone);

            Set<String> evidenceTypes = new HashSet<>();
            for (String type : m.evidenceTypes()) {
                if (!evidenceTypes.add(type)) {
                    throw new BusinessRuleException("里程碑 " + sequence + " 证据类型重复：" + type);
                }
                milestone.addRequiredEvidence(new RequiredEvidence(milestone, type));
            }

            Set<String> roles = new HashSet<>();
            for (String role : m.approvalRoles()) {
                if (!roles.add(role)) {
                    throw new BusinessRuleException("里程碑 " + sequence + " 审批角色重复：" + role);
                }
                milestone.addApprovalRequirement(new ApprovalRequirement(milestone, role));
            }

            plannedSum = plannedSum.add(m.plannedAmount());
            sequence++;
        }

        if (plannedSum.compareTo(cmd.totalAmount()) != 0) {
            throw new BusinessRuleException("全部里程碑计划金额之和（" + plannedSum
                    + "）必须等于托管金额（" + cmd.totalAmount() + "）");
        }

        try {
            return projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("项目编码已存在：" + cmd.code());
        }
    }

    // ---------------- 证据 ----------------

    @Transactional
    public RequiredEvidence submitEvidence(String projectCode, Commands.SubmitEvidence cmd) {
        EscrowProject project = lockProject(projectCode);
        Milestone milestone = getMilestone(project.getId(), cmd.milestoneSequence());
        if (milestone.isDisbursed()) {
            throw new BusinessRuleException("里程碑 " + cmd.milestoneSequence()
                    + " 已放款，证据不可再变更");
        }
        RequiredEvidence evidence = milestone.getRequiredEvidences().stream()
                .filter(e -> e.getEvidenceType().equals(cmd.evidenceType()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("里程碑 " + cmd.milestoneSequence()
                        + " 不存在所需证据：" + cmd.evidenceType()));
        if (evidence.isProvided()) {
            throw new BusinessRuleException("证据已提交且不可修改：" + cmd.evidenceType());
        }
        evidence.markProvided(cmd.evidenceRef(), cmd.reason(), cmd.handledBy(), Instant.now(clock));
        return evidence;
    }

    // ---------------- 审批 / 撤回 ----------------

    @Transactional
    public ApprovalRequirement approve(String projectCode, Commands.ApprovalDecisionCommand cmd) {
        return decide(projectCode, cmd, ApprovalDecisionType.APPROVE);
    }

    @Transactional
    public ApprovalRequirement withdrawApproval(String projectCode, Commands.ApprovalDecisionCommand cmd) {
        return decide(projectCode, cmd, ApprovalDecisionType.WITHDRAW);
    }

    private ApprovalRequirement decide(String projectCode, Commands.ApprovalDecisionCommand cmd,
                                       ApprovalDecisionType type) {
        EscrowProject project = lockProject(projectCode);
        Milestone milestone = getMilestone(project.getId(), cmd.milestoneSequence());
        if (milestone.isDisbursed()) {
            throw new BusinessRuleException("里程碑 " + cmd.milestoneSequence()
                    + " 已放款，审批状态不可变更");
        }
        ApprovalRequirement requirement = milestone.getApprovalRequirements().stream()
                .filter(r -> r.getRequiredRole().equals(cmd.role()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("里程碑 " + cmd.milestoneSequence()
                        + " 不存在审批角色：" + cmd.role()));

        Instant now = Instant.now(clock);
        if (type == ApprovalDecisionType.APPROVE) {
            requirement.approve(cmd.handledBy(), now);
        } else {
            requirement.withdraw(cmd.handledBy(), now);
        }
        decisionRepository.save(new ApprovalDecision(requirement, type, cmd.handledBy(),
                cmd.reason(), now));
        return requirement;
    }

    // ---------------- 放款 ----------------

    /**
     * 里程碑放款。同一项目经项目行锁串行化，保证：
     * 证据齐全 + 全部角色审批通过 + 前置里程碑均已放款 + 余额充足，
     * 以上条件在同一持锁事务内判定并扣减余额、生成放款与台账记录。
     *
     * @return 首次放款或（幂等重试时）首次生成的同一笔放款
     */
    @Transactional
    public Disbursement disburse(String projectCode, Commands.Disburse cmd) {
        EscrowProject project = lockProject(projectCode);

        String fingerprint = fingerprint(
                projectCode, cmd.milestoneSequence(), cmd.amount(), cmd.reason(), cmd.handledBy());
        IdempotentRequest existing = idempotentRequestRepository
                .findByRequestTypeAndBusinessNo(IdempotentRequestType.DISBURSEMENT, cmd.businessNo())
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(
                        "放款业务号 " + cmd.businessNo() + " 已用于不同内容的请求");
            }
            return disbursementRepository.findByBusinessNo(cmd.businessNo())
                    .orElseThrow(() -> new IllegalStateException(
                            "幂等记录存在但找不到原放款：" + cmd.businessNo()));
        }

        Milestone milestone = getMilestone(project.getId(), cmd.milestoneSequence());

        if (milestone.isDisbursed()) {
            throw new BusinessRuleException("里程碑 " + cmd.milestoneSequence() + " 已放款，不能重复放款");
        }
        if (!milestone.allEvidenceProvided()) {
            throw new BusinessRuleException("里程碑 " + cmd.milestoneSequence() + " 所需证据尚未齐全，不能放款");
        }
        if (!milestone.allApprovalsGranted()) {
            throw new BusinessRuleException("里程碑 " + cmd.milestoneSequence() + " 角色审批尚未全部通过，不能放款");
        }
        for (Milestone earlier : project.getMilestones()) {
            if (earlier.getSequenceNo() < milestone.getSequenceNo() && !earlier.isDisbursed()) {
                throw new BusinessRuleException("前置里程碑 " + earlier.getSequenceNo()
                        + " 尚未完成放款，不能越过顺序放款");
            }
        }
        if (cmd.amount().compareTo(milestone.getPlannedAmount()) != 0) {
            throw new BusinessRuleException("放款金额（" + cmd.amount()
                    + "）必须等于里程碑计划金额（" + milestone.getPlannedAmount() + "）");
        }

        // 持锁扣减：余额不足直接失败，多个里程碑并发放款总额不可能超过托管余额。
        project.debit(cmd.amount());

        Instant now = Instant.now(clock);
        Disbursement disbursement = new Disbursement(cmd.businessNo(), project, milestone,
                cmd.amount(), cmd.handledBy(), cmd.reason(), now);
        milestone.markDisbursed(disbursement);
        disbursementRepository.save(disbursement);
        ledgerEntryRepository.save(new LedgerEntry(project, cmd.businessNo(),
                LedgerEntryType.DISBURSEMENT, LedgerDirection.DEBIT, cmd.amount(), disbursement,
                cmd.handledBy(), cmd.reason(), now));
        try {
            idempotentRequestRepository.saveAndFlush(new IdempotentRequest(
                    IdempotentRequestType.DISBURSEMENT, cmd.businessNo(), fingerprint,
                    cmd.businessNo(), now));
        } catch (DataIntegrityViolationException e) {
            // 跨项目并��使用相同业务号：业务号全局唯一，拒绝复用。
            throw new ConflictException("放款业务号已被占用：" + cmd.businessNo());
        }
        return disbursement;
    }

    // ---------------- 撤销 ----------------

    /**
     * 整笔撤销尚未对外结算的放款：恢复托管余额、里程碑回到待放款，
     * 原放款标记 REVOKED（保留不删），另追加一条撤销台账。
     */
    @Transactional
    public Disbursement revoke(Commands.Revoke cmd) {
        Disbursement disbursement = getDisbursement(cmd.disbursementBusinessNo());
        EscrowProject project = lockProject(disbursement.getProject().getCode());

        if (disbursement.getStatus() == DisbursementStatus.SETTLED) {
            throw new BusinessRuleException("放款已对外结算，不能撤销，只能登记补偿："
                    + cmd.disbursementBusinessNo());
        }
        if (disbursement.getStatus() == DisbursementStatus.REVOKED) {
            throw new BusinessRuleException("放款已撤销，不能重复撤销：" + cmd.disbursementBusinessNo());
        }
        Milestone target = disbursement.getMilestone();
        for (Milestone later : project.getMilestones()) {
            if (later.getSequenceNo() > target.getSequenceNo() && later.isDisbursed()) {
                throw new BusinessRuleException("后置里程碑 " + later.getSequenceNo()
                        + " 已放款，不能撤销前置里程碑放款：" + cmd.disbursementBusinessNo());
            }
        }

        Instant now = Instant.now(clock);
        disbursement.markRevoked(cmd.handledBy(), cmd.reason(), now);
        project.credit(disbursement.getAmount());
        disbursement.getMilestone().markReopened();
        ledgerEntryRepository.save(new LedgerEntry(project, null,
                LedgerEntryType.REVERSAL, LedgerDirection.CREDIT, disbursement.getAmount(),
                disbursement, cmd.handledBy(), cmd.reason(), now));
        return disbursement;
    }

    // ---------------- 结算 ----------------

    /** 登记放款已对外结算；结算后不可撤销，只能补偿。 */
    @Transactional
    public Disbursement settle(Commands.Settle cmd) {
        Disbursement disbursement = getDisbursement(cmd.disbursementBusinessNo());
        lockProject(disbursement.getProject().getCode());

        if (disbursement.getStatus() == DisbursementStatus.SETTLED) {
            throw new BusinessRuleException("放款已结算，不能重复结算：" + cmd.disbursementBusinessNo());
        }
        disbursement.markSettled(cmd.handledBy(), cmd.reason(), Instant.now(clock));
        return disbursement;
    }

    // ---------------- 补偿 ----------------

    /**
     * 对已结算放款登记补偿记录：只追加台账，不删除/覆盖原放款流水，不变动托管余额。
     */
    @Transactional
    public LedgerEntry compensate(Commands.Compensate cmd) {
        Disbursement original = getDisbursement(cmd.disbursementBusinessNo());
        EscrowProject project = lockProject(original.getProject().getCode());

        String fingerprint = fingerprint(
                cmd.disbursementBusinessNo(), cmd.amount(), cmd.reason(), cmd.handledBy());
        IdempotentRequest existing = idempotentRequestRepository
                .findByRequestTypeAndBusinessNo(IdempotentRequestType.COMPENSATION, cmd.businessNo())
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(
                        "补偿业务号 " + cmd.businessNo() + " 已用于不同内容的请求");
            }
            return ledgerEntryRepository.findByDisbursementIdOrderByIdAsc(original.getId()).stream()
                    .filter(e -> e.getEntryType() == LedgerEntryType.COMPENSATION
                            && cmd.businessNo().equals(e.getBusinessNo()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "幂等记录存在但找不到原补偿台账：" + cmd.businessNo()));
        }

        if (original.getStatus() != DisbursementStatus.SETTLED) {
            throw new BusinessRuleException("只有已结算放款才能登记补偿；未结算放款应整笔撤销："
                    + cmd.disbursementBusinessNo());
        }

        Instant now = Instant.now(clock);
        LedgerEntry entry = new LedgerEntry(project, cmd.businessNo(),
                LedgerEntryType.COMPENSATION, LedgerDirection.DEBIT, cmd.amount(), original,
                cmd.handledBy(), cmd.reason(), now);
        ledgerEntryRepository.save(entry);
        try {
            idempotentRequestRepository.saveAndFlush(new IdempotentRequest(
                    IdempotentRequestType.COMPENSATION, cmd.businessNo(), fingerprint,
                    cmd.businessNo(), now));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("补偿业务号已被占用：" + cmd.businessNo());
        }
        return entry;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public EscrowProject getProject(String code) {
        return projectRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("项目不存在：" + code));
    }

    @Transactional(readOnly = true)
    public List<Milestone> listMilestones(String projectCode) {
        EscrowProject project = getProject(projectCode);
        return milestoneRepository.findByProjectIdOrderBySequenceNoAsc(project.getId());
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger(String projectCode) {
        EscrowProject project = getProject(projectCode);
        return ledgerEntryRepository.findByProjectIdOrderByIdAsc(project.getId());
    }

    @Transactional(readOnly = true)
    public Disbursement getDisbursement(String businessNo) {
        return disbursementRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("放款不存在：" + businessNo));
    }

    @Transactional(readOnly = true)
    public List<Disbursement> listDisbursements(String projectCode) {
        EscrowProject project = getProject(projectCode);
        return disbursementRepository.findByProjectIdOrderByIdAsc(project.getId());
    }

    // ---------------- 内部辅助 ----------------

    private EscrowProject lockProject(String code) {
        return projectRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new NotFoundException("项目不存在：" + code));
    }

    private Milestone getMilestone(Long projectId, int sequenceNo) {
        return milestoneRepository.findByProjectIdAndSequenceNo(projectId, sequenceNo)
                .orElseThrow(() -> new NotFoundException("里程碑不存在：序号 " + sequenceNo));
    }

    private static String fingerprint(Object... parts) {
        String canonical = String.join("",
                java.util.Arrays.stream(parts).map(String::valueOf).toList());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
