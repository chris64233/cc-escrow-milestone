package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 里程碑：属于某个托管项目，按 {@code sequenceNo} 构成严格先后顺序。
 *
 * <p>放款前置条件：所需证据全部提交齐全、所需角色审批全部处于 APPROVED，
 * 且所有序号更小的里程碑已放款。
 */
@Entity
@Table(name = "milestone")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private EscrowProject project;

    /** 从 1 开始的顺序号，同一项目内唯一。 */
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(nullable = false, updatable = false, length = 128)
    private String name;

    /** 计划放款金额，创建后不可变；所有里程碑计划金额之和必须等于托管金额。 */
    @Column(name = "planned_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal plannedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    /** 最近一次放款；放款撤销后该里程碑可再次放款，此处更新为最新放款。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_disbursement_id")
    private Disbursement currentDisbursement;

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RequiredEvidence> requiredEvidences = new ArrayList<>();

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ApprovalRequirement> approvalRequirements = new ArrayList<>();

    protected Milestone() {
    }

    public Milestone(EscrowProject project, int sequenceNo, String name, BigDecimal plannedAmount) {
        this.project = project;
        this.sequenceNo = sequenceNo;
        this.name = name;
        this.plannedAmount = plannedAmount;
    }

    public void addRequiredEvidence(RequiredEvidence evidence) {
        requiredEvidences.add(evidence);
    }

    public void addApprovalRequirement(ApprovalRequirement requirement) {
        approvalRequirements.add(requirement);
    }

    /** 证据是否全部提交齐全。 */
    public boolean allEvidenceProvided() {
        return !requiredEvidences.isEmpty()
                && requiredEvidences.stream().allMatch(RequiredEvidence::isProvided);
    }

    /** 角色审批是否全部满足（且无任一审批仍处于待审批）。 */
    public boolean allApprovalsGranted() {
        return !approvalRequirements.isEmpty()
                && approvalRequirements.stream().allMatch(r -> r.getStatus() == ApprovalStatus.APPROVED);
    }

    public boolean isDisbursed() {
        return status == MilestoneStatus.DISBURSED;
    }

    public void markDisbursed(Disbursement disbursement) {
        this.status = MilestoneStatus.DISBURSED;
        this.currentDisbursement = disbursement;
    }

    public void markReopened() {
        this.status = MilestoneStatus.PENDING;
        this.currentDisbursement = null;
    }

    public List<RequiredEvidence> getRequiredEvidences() {
        return Collections.unmodifiableList(requiredEvidences);
    }

    public List<ApprovalRequirement> getApprovalRequirements() {
        return Collections.unmodifiableList(approvalRequirements);
    }

    public Long getId() {
        return id;
    }

    public EscrowProject getProject() {
        return project;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPlannedAmount() {
        return plannedAmount;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public Disbursement getCurrentDisbursement() {
        return currentDisbursement;
    }
}
