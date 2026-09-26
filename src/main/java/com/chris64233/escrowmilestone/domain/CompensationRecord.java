package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 补偿记录：已结算放款不允许撤销，只能登记补偿，不删除或覆盖原流水。
 */
@Entity
@Table(name = "compensation_record")
public class CompensationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CompensationRecord() {
    }

    public CompensationRecord(Payout payout, BigDecimal amount, String operator, String reason, Instant createdAt) {
        this.payout = payout;
        this.amount = amount;
        this.operator = operator;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Payout getPayout() {
        return payout;
    }

    public Long getPayoutId() {
        return payout.getId();
    }

    public BigDecimal getAmount() {
        return amount;
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
}
