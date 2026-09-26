package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 里程碑：按计划顺序排列，包含计划金额、所需证据类型和审批角色门槛。
 */
@Entity
@Table(name = "milestone")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private EscrowProject project;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "milestone_evidence_type", joinColumns = @JoinColumn(name = "milestone_id"))
    @Column(name = "evidence_type", nullable = false)
    private Set<String> requiredEvidenceTypes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "milestone_approval_requirement", joinColumns = @JoinColumn(name = "milestone_id"))
    private List<ApprovalRequirement> approvalRequirements = new ArrayList<>();

    protected Milestone() {
    }

    public Milestone(EscrowProject project, int sequence, String title, BigDecimal plannedAmount,
                     Set<String> requiredEvidenceTypes, List<ApprovalRequirement> approvalRequirements) {
        this.project = project;
        this.sequence = sequence;
        this.title = title;
        this.plannedAmount = plannedAmount;
        this.requiredEvidenceTypes = new LinkedHashSet<>(requiredEvidenceTypes);
        this.approvalRequirements = new ArrayList<>(approvalRequirements);
    }

    public void markReleased() {
        this.status = MilestoneStatus.RELEASED;
    }

    public void markPending() {
        this.status = MilestoneStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public EscrowProject getProject() {
        return project;
    }

    public int getSequence() {
        return sequence;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getPlannedAmount() {
        return plannedAmount;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public Set<String> getRequiredEvidenceTypes() {
        return Collections.unmodifiableSet(requiredEvidenceTypes);
    }

    public List<ApprovalRequirement> getApprovalRequirements() {
        return Collections.unmodifiableList(approvalRequirements);
    }
}
