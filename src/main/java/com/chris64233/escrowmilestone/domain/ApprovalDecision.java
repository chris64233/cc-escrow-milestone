package com.chris64233.escrowmilestone.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 审批决定流水：只追加，完整记录每次审批通过与撤回的处理人、原因和时间。
 */
@Entity
@Table(name = "approval_decision")
public class ApprovalDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false, updatable = false)
    private ApprovalRequirement requirement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ApprovalDecisionType decisionType;

    @Column(nullable = false, updatable = false, length = 64)
    private String decidedBy;

    @Column(nullable = false, updatable = false, length = 512)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant decidedAt;

    protected ApprovalDecision() {
    }

    public ApprovalDecision(ApprovalRequirement requirement, ApprovalDecisionType decisionType,
                            String decidedBy, String reason, Instant decidedAt) {
        this.requirement = requirement;
        this.decisionType = decisionType;
        this.decidedBy = decidedBy;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public ApprovalRequirement getRequirement() {
        return requirement;
    }

    public ApprovalDecisionType getDecisionType() {
        return decisionType;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
