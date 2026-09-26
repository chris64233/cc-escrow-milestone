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
 * 资金台账记录：不可修改，只追加。记录处理人、原因和时间。
 */
@Entity
@Table(name = "fund_record")
public class FundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private EscrowProject project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FundRecordType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected FundRecord() {
    }

    public FundRecord(EscrowProject project, Payout payout, FundRecordType type, BigDecimal amount,
                      BigDecimal balanceAfter, String operator, String reason, Instant occurredAt) {
        this.project = project;
        this.payout = payout;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.operator = operator;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public EscrowProject getProject() {
        return project;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Payout getPayout() {
        return payout;
    }

    public Long getProjectId() {
        return project.getId();
    }

    public Long getPayoutId() {
        return payout.getId();
    }

    public FundRecordType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
