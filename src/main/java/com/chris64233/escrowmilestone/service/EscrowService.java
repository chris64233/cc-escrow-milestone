package com.chris64233.escrowmilestone.service;

import com.chris64233.escrowmilestone.domain.Approval;
import com.chris64233.escrowmilestone.domain.ApprovalDecision;
import com.chris64233.escrowmilestone.domain.ApprovalRequirement;
import com.chris64233.escrowmilestone.domain.CompensationRecord;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.Evidence;
import com.chris64233.escrowmilestone.domain.FundRecord;
import com.chris64233.escrowmilestone.domain.FundRecordType;
import com.chris64233.escrowmilestone.domain.Milestone;
import com.chris64233.escrowmilestone.domain.MilestoneStatus;
import com.chris64233.escrowmilestone.domain.Payout;
import com.chris64233.escrowmilestone.domain.PayoutStatus;
import com.chris64233.escrowmilestone.repository.ApprovalRepository;
import com.chris64233.escrowmilestone.repository.CompensationRecordRepository;
import com.chris64233.escrowmilestone.repository.EscrowProjectRepository;
import com.chris64233.escrowmilestone.repository.EvidenceRepository;
import com.chris64233.escrowmilestone.repository.FundRecordRepository;
import com.chris64233.escrowmilestone.repository.PayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;

/**
 * 托管资金核心服务。
 *
 * <p>所有会改变资金结果的操作（放款、撤销、审批决定）都先对项目行加悲观写锁，
 * 保证审批撤回与放款、多个并发放款之间串行化，只形成一种完整结果。</p>
 */
@Service
public class EscrowService {

    private final EscrowProjectRepository projectRepository;
    private final EvidenceRepository evidenceRepository;
    private final ApprovalRepository approvalRepository;
    private final PayoutRepository payoutRepository;
    private final FundRecordRepository fundRecordRepository;
    private final CompensationRecordRepository compensationRepository;

    public EscrowService(EscrowProjectRepository projectRepository,
                         EvidenceRepository evidenceRepository,
                         ApprovalRepository approvalRepository,
                         PayoutRepository payoutRepository,
                         FundRecordRepository fundRecordRepository,
                         CompensationRecordRepository compensationRepository) {
        this.projectRepository = projectRepository;
        this.evidenceRepository = evidenceRepository;
        this.approvalRepository = approvalRepository;
        this.payoutRepository = payoutRepository;
        this.fundRecordRepository = fundRecordRepository;
        this.compensationRepository = compensationRepository;
    }

    // ---------- 项目与里程碑 ----------

    @Transactional
    public EscrowProject createProject(String name, BigDecimal totalAmount, List<MilestoneSpec> milestoneSpecs) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("项目名称不能为空");
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw BusinessException.badRequest("托管金额必须为正数");
        }
        if (milestoneSpecs == null || milestoneSpecs.isEmpty()) {
            throw BusinessException.badRequest("至少需要一个里程碑");
        }
        Set<Integer> sequences = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (MilestoneSpec spec : milestoneSpecs) {
            if (spec.sequence() <= 0) {
                throw BusinessException.badRequest("里程碑序号必须为正整数");
            }
            if (!sequences.add(spec.sequence())) {
                throw BusinessException.badRequest("里程碑序号重复: " + spec.sequence());
            }
            if (spec.plannedAmount() == null || spec.plannedAmount().signum() <= 0) {
                throw BusinessException.badRequest("里程碑计划金额必须为正数");
            }
            for (ApprovalRequirement req : spec.approvalRequirements()) {
                if (req.getRequiredCount() <= 0) {
                    throw BusinessException.badRequest("审批门槛人数必须为正数");
                }
            }
            sum = sum.add(spec.plannedAmount());
        }
        if (sum.compareTo(totalAmount) != 0) {
            throw BusinessException.badRequest(
                    "里程碑计划金额之和 " + sum + " 必须等于托管金额 " + totalAmount);
        }

        EscrowProject project = new EscrowProject(name, totalAmount, Instant.now());
        milestoneSpecs.stream()
                .sorted((a, b) -> Integer.compare(a.sequence(), b.sequence()))
                .forEach(spec -> project.addMilestone(new Milestone(
                        project, spec.sequence(),
                        spec.title() == null || spec.title().isBlank() ? "里程碑-" + spec.sequence() : spec.title(),
                        spec.plannedAmount(),
                        spec.requiredEvidenceTypes() == null ? Set.of() : spec.requiredEvidenceTypes(),
                        spec.approvalRequirements() == null ? List.of() : spec.approvalRequirements())));
        return projectRepository.save(project);
    }

    // ---------- 证据与审批 ----------

    @Transactional
    public Evidence submitEvidence(Long projectId, int sequence, String type, String submittedBy, String content) {
        EscrowProject project = lockProject(projectId);
        Milestone milestone = milestoneOf(project, sequence);
        if (type == null || type.isBlank()) {
            throw BusinessException.badRequest("证据类型不能为空");
        }
        Evidence evidence = new Evidence(milestone, type, submittedBy, content, Instant.now());
        return evidenceRepository.save(evidence);
    }

    /**
     * 登记审批决定（同意或撤回）。与放款共用项目行锁，
     * 撤回与放款并发时按锁顺序串行，只会形成一种完整结果。
     */
    @Transactional
    public Approval decide(Long projectId, int sequence, String role, String approver,
                           ApprovalDecision decision, String reason) {
        EscrowProject project = lockProject(projectId);
        Milestone milestone = milestoneOf(project, sequence);
        boolean roleKnown = milestone.getApprovalRequirements().stream()
                .anyMatch(r -> r.getRole().equals(role));
        if (!roleKnown) {
            throw BusinessException.badRequest("角色 " + role + " 不在该里程碑的审批要求中");
        }
        Approval approval = new Approval(milestone, role, approver, decision,
                reason == null ? "" : reason, Instant.now());
        return approvalRepository.save(approval);
    }

    // ---------- 放款 ----------

    /**
     * 按里程碑放款。幂等：bizNo 相同且内容一致返回首次结果，内容变化返回冲突。
     */
    @Transactional
    public Payout release(Long projectId, int sequence, String bizNo, String operator, String reason) {
        if (bizNo == null || bizNo.isBlank()) {
            throw BusinessException.badRequest("放款业务号不能为空");
        }
        EscrowProject project = lockProject(projectId);
        String requestHash = requestHash(projectId, sequence);

        var existing = payoutRepository.findByBizNo(bizNo);
        if (existing.isPresent()) {
            Payout found = existing.get();
            if (!found.getRequestHash().equals(requestHash)) {
                throw BusinessException.conflict("业务号 " + bizNo + " 已存在且请求内容不一致");
            }
            return found;
        }

        Milestone milestone = milestoneOf(project, sequence);
        if (milestone.getStatus() == MilestoneStatus.RELEASED) {
            throw BusinessException.unprocessable("MILESTONE_ALREADY_RELEASED",
                    "里程碑 " + sequence + " 已放款");
        }
        for (Milestone prior : project.getMilestones()) {
            if (prior.getSequence() < sequence && prior.getStatus() != MilestoneStatus.RELEASED) {
                throw BusinessException.unprocessable("PRIOR_MILESTONE_NOT_RELEASED",
                        "前置里程碑 " + prior.getSequence() + " 尚未放款，不能越过");
            }
        }

        MilestoneStatusView readiness = milestoneStatusView(project, milestone);
        if (!readiness.missingEvidenceTypes().isEmpty()) {
            throw BusinessException.unprocessable("EVIDENCE_INCOMPLETE",
                    "证据不齐全，缺少: " + readiness.missingEvidenceTypes());
        }
        List<String> unsatisfied = readiness.approvalProgress().stream()
                .filter(p -> !p.satisfied())
                .map(p -> p.role() + "(" + p.approvedCount() + "/" + p.requiredCount() + ")")
                .toList();
        if (!unsatisfied.isEmpty()) {
            throw BusinessException.unprocessable("APPROVAL_THRESHOLD_NOT_MET",
                    "审批门槛未满足: " + unsatisfied);
        }

        BigDecimal amount = milestone.getPlannedAmount();
        if (project.getBalance().compareTo(amount) < 0) {
            throw BusinessException.unprocessable("INSUFFICIENT_BALANCE", "托管余额不足");
        }

        Instant now = Instant.now();
        project.debit(amount);
        milestone.markReleased();
        Payout payout = payoutRepository.save(new Payout(
                bizNo, requestHash, project, milestone, amount, operator,
                reason == null ? "" : reason, now));
        fundRecordRepository.save(new FundRecord(project, payout, FundRecordType.RELEASE,
                amount, project.getBalance(), operator, reason == null ? "" : reason, now));
        return payout;
    }

    /**
     * 整笔撤销未结算放款并恢复余额。已结算放款只能登记补偿。
     */
    @Transactional
    public Payout reverse(Long projectId, Long payoutId, String operator, String reason) {
        EscrowProject project = lockProject(projectId);
        Payout payout = payoutOf(projectId, payoutId);
        if (payout.getStatus() == PayoutStatus.SETTLED) {
            throw BusinessException.unprocessable("PAYOUT_ALREADY_SETTLED",
                    "放款已结算，只能登记补偿记录，不能撤销");
        }
        if (payout.getStatus() == PayoutStatus.REVERSED) {
            throw BusinessException.unprocessable("PAYOUT_ALREADY_REVERSED", "放款已撤销，不能重复撤销");
        }
        Instant now = Instant.now();
        project.credit(payout.getAmount());
        payout.markReversed(now);
        payout.getMilestone().markPending();
        fundRecordRepository.save(new FundRecord(project, payout, FundRecordType.REVERSE,
                payout.getAmount(), project.getBalance(), operator,
                reason == null ? "" : reason, now));
        return payout;
    }

    /**
     * 标记放款已对外结算。
     */
    @Transactional
    public Payout settle(Long projectId, Long payoutId, String operator) {
        lockProject(projectId);
        Payout payout = payoutOf(projectId, payoutId);
        if (payout.getStatus() != PayoutStatus.RELEASED) {
            throw BusinessException.unprocessable("PAYOUT_NOT_RELEASED",
                    "只有已放款未结算的放款才能标记结算，当前状态: " + payout.getStatus());
        }
        payout.markSettled(Instant.now());
        return payout;
    }

    /**
     * 已结算放款登记补偿记录，不删除或覆盖原流水，不影响托管余额。
     */
    @Transactional
    public CompensationRecord compensate(Long projectId, Long payoutId, BigDecimal amount,
                                         String operator, String reason) {
        EscrowProject project = lockProject(projectId);
        Payout payout = payoutOf(projectId, payoutId);
        if (payout.getStatus() != PayoutStatus.SETTLED) {
            throw BusinessException.unprocessable("PAYOUT_NOT_SETTLED",
                    "只有已结算的放款才能登记补偿记录");
        }
        if (amount == null || amount.signum() <= 0) {
            throw BusinessException.badRequest("补偿金额必须为正数");
        }
        Instant now = Instant.now();
        CompensationRecord record = compensationRepository.save(
                new CompensationRecord(payout, amount, operator, reason == null ? "" : reason, now));
        fundRecordRepository.save(new FundRecord(project, payout, FundRecordType.COMPENSATE,
                amount, project.getBalance(), operator, reason == null ? "" : reason, now));
        return record;
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public ProjectBalanceView balanceView(Long projectId) {
        EscrowProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.notFound("项目不存在: " + projectId));
        BigDecimal released = payoutRepository.findByProject_IdOrderByIdAsc(projectId).stream()
                .filter(p -> p.getStatus() != PayoutStatus.REVERSED)
                .map(Payout::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProjectBalanceView(project.getId(), project.getName(),
                project.getTotalAmount(), project.getBalance(), released);
    }

    @Transactional(readOnly = true)
    public List<MilestoneStatusView> milestoneViews(Long projectId) {
        EscrowProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.notFound("项目不存在: " + projectId));
        return project.getMilestones().stream()
                .map(m -> milestoneStatusView(project, m))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Evidence> evidences(Long projectId, int sequence) {
        Milestone milestone = milestoneOf(findProject(projectId), sequence);
        return evidenceRepository.findByMilestone_Id(milestone.getId());
    }

    @Transactional(readOnly = true)
    public List<Approval> approvals(Long projectId, int sequence) {
        Milestone milestone = milestoneOf(findProject(projectId), sequence);
        return approvalRepository.findByMilestone_IdOrderByDecidedAtAsc(milestone.getId());
    }

    @Transactional(readOnly = true)
    public List<FundRecord> ledger(Long projectId) {
        findProject(projectId);
        return fundRecordRepository.findByProject_IdOrderByIdAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<Payout> payouts(Long projectId) {
        findProject(projectId);
        return payoutRepository.findByProject_IdOrderByIdAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<CompensationRecord> compensations(Long projectId, Long payoutId) {
        return compensationRepository.findByPayout_IdOrderByIdAsc(payoutOf(projectId, payoutId).getId());
    }

    // ---------- 内部方法 ----------

    private EscrowProject findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.notFound("项目不存在: " + projectId));
    }

    private EscrowProject lockProject(Long projectId) {
        return projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> BusinessException.notFound("项目不存在: " + projectId));
    }

    private Milestone milestoneOf(EscrowProject project, int sequence) {
        return project.milestoneBySequence(sequence)
                .orElseThrow(() -> BusinessException.notFound(
                        "项目 " + project.getId() + " 不存在里程碑 " + sequence));
    }

    private Payout payoutOf(Long projectId, Long payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> BusinessException.notFound("放款单不存在: " + payoutId));
        if (!payout.getProject().getId().equals(projectId)) {
            throw BusinessException.notFound("项目 " + projectId + " 下不存在放款单 " + payoutId);
        }
        return payout;
    }

    /**
     * 计算里程碑当前的有效审批：同一审批人以最新决定为准。
     */
    private Map<String, Set<String>> activeApproversByRole(Milestone milestone) {
        List<Approval> approvals = approvalRepository.findByMilestone_IdOrderByDecidedAtAsc(milestone.getId());
        Map<String, Map<String, ApprovalDecision>> latest = new HashMap<>();
        for (Approval approval : approvals) {
            latest.computeIfAbsent(approval.getRole(), k -> new HashMap<>())
                    .put(approval.getApprover(), approval.getDecision());
        }
        Map<String, Set<String>> active = new HashMap<>();
        latest.forEach((role, byApprover) -> {
            Set<String> approvers = new LinkedHashSet<>();
            byApprover.forEach((approver, decision) -> {
                if (decision == ApprovalDecision.APPROVE) {
                    approvers.add(approver);
                }
            });
            active.put(role, approvers);
        });
        return active;
    }

    private MilestoneStatusView milestoneStatusView(EscrowProject project, Milestone milestone) {
        Set<String> submitted = new LinkedHashSet<>();
        for (Evidence evidence : evidenceRepository.findByMilestone_Id(milestone.getId())) {
            submitted.add(evidence.getType());
        }
        Set<String> missing = new LinkedHashSet<>(milestone.getRequiredEvidenceTypes());
        missing.removeAll(submitted);

        Map<String, Set<String>> active = activeApproversByRole(milestone);
        List<MilestoneStatusView.RoleApprovalProgress> progress = new ArrayList<>();
        for (ApprovalRequirement req : milestone.getApprovalRequirements()) {
            long approved = active.getOrDefault(req.getRole(), Set.of()).size();
            progress.add(new MilestoneStatusView.RoleApprovalProgress(
                    req.getRole(), req.getRequiredCount(), approved, approved >= req.getRequiredCount()));
        }
        boolean ready = missing.isEmpty()
                && progress.stream().allMatch(MilestoneStatusView.RoleApprovalProgress::satisfied)
                && milestone.getStatus() == MilestoneStatus.PENDING;
        return new MilestoneStatusView(milestone.getSequence(), milestone.getTitle(),
                milestone.getPlannedAmount(), milestone.getStatus(),
                submitted, missing, progress, ready);
    }

    private String requestHash(Long projectId, int sequence) {
        String content = projectId + "|" + sequence;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
