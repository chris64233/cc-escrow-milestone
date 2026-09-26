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
 * 角色审批门槛：每个里程碑可配置多个所需审批角色，
 * 放款时所有门槛必须处于 APPROVED。审批可撤回（回到 PENDING）。
 */
@Entity
@Table(name = "approval_requirement")
public class ApprovalRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false, updatable = false)
    private Milestone milestone;

    /** 所需审批角色，如 PM、FINANCE、LEGAL。同一里程碑内唯一。 */
    @Column(name = "required_role", nullable = false, updatable = false, length = 64)
    private String requiredRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "last_decided_by", length = 64)
    private String lastDecidedBy;

    @Column(name = "last_decided_at")
    private Instant lastDecidedAt;

    protected ApprovalRequirement() {
    }

    public ApprovalRequirement(Milestone milestone, String requiredRole) {
        this.milestone = milestone;
        this.requiredRole = requiredRole;
    }

    public void approve(String by, Instant at) {
        this.status = ApprovalStatus.APPROVED;
        this.lastDecidedBy = by;
        this.lastDecidedAt = at;
    }

    public void withdraw(String by, Instant at) {
        this.status = ApprovalStatus.PENDING;
        this.lastDecidedBy = by;
        this.lastDecidedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getLastDecidedBy() {
        return lastDecidedBy;
    }

    public Instant getLastDecidedAt() {
        return lastDecidedAt;
    }
}
