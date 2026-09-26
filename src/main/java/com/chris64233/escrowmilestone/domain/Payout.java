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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 放款单。bizNo 为幂等业务号，requestHash 记录首次提交内容指纹，
 * 相同业务号内容一致返回首次结果，内容变化判定为冲突。
 */
@Entity
@Table(name = "payout")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String bizNo;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private EscrowProject project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant settledAt;

    private Instant reversedAt;

    protected Payout() {
    }

    public Payout(String bizNo, String requestHash, EscrowProject project, Milestone milestone,
                  BigDecimal amount, String operator, String reason, Instant createdAt) {
        this.bizNo = bizNo;
        this.requestHash = requestHash;
        this.project = project;
        this.milestone = milestone;
        this.amount = amount;
        this.status = PayoutStatus.RELEASED;
        this.operator = operator;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void markSettled(Instant settledAt) {
        this.status = PayoutStatus.SETTLED;
        this.settledAt = settledAt;
    }

    public void markReversed(Instant reversedAt) {
        this.status = PayoutStatus.REVERSED;
        this.reversedAt = reversedAt;
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public String getRequestHash() {
        return requestHash;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public EscrowProject getProject() {
        return project;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Milestone getMilestone() {
        return milestone;
    }

    public Long getProjectId() {
        return project.getId();
    }

    public Long getMilestoneId() {
        return milestone.getId();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }
}
