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
 * 审批决定记录（同意/撤回）。同一审批人多次决定时以最新一条为准。
 */
@Entity
@Table(name = "approval")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision decision;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant decidedAt;

    protected Approval() {
    }

    public Approval(Milestone milestone, String role, String approver, ApprovalDecision decision,
                    String reason, Instant decidedAt) {
        this.milestone = milestone;
        this.role = role;
        this.approver = approver;
        this.decision = decision;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Milestone getMilestone() {
        return milestone;
    }

    public Long getMilestoneId() {
        return milestone.getId();
    }

    public String getRole() {
        return role;
    }

    public String getApprover() {
        return approver;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
