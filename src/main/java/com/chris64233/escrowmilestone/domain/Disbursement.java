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
 * 放款资金记录。
 *
 * <p>核心要素（业务号、里程碑、金额、放款人、放款时间、事由）在创建后不可修改；
 * 仅允许受控的生命周期状态迁移（放款 → 已撤销 / 已结算），
 * 撤销与补偿等后续动作另以只追加的 {@link LedgerEntry} 留痕，原流水不被删除或覆盖。
 */
@Entity
@Table(name = "disbursement")
public class Disbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 放款业务号，全局唯一，幂等键。 */
    @Column(name = "business_no", nullable = false, unique = true, updatable = false, length = 64)
    private String businessNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private EscrowProject project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false, updatable = false)
    private Milestone milestone;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DisbursementStatus status = DisbursementStatus.DISBURSED;

    @Column(nullable = false, updatable = false, length = 64)
    private String disbursedBy;

    @Column(nullable = false, updatable = false, length = 512)
    private String reason;

    @Column(name = "disbursed_at", nullable = false, updatable = false)
    private Instant disbursedAt;

    @Column(name = "settled_by", length = 64)
    private String settledBy;

    @Column(name = "settled_reason", length = 512)
    private String settledReason;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @Column(name = "revoked_reason", length = 512)
    private String revokedReason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Disbursement() {
    }

    public Disbursement(String businessNo, EscrowProject project, Milestone milestone, BigDecimal amount,
                        String disbursedBy, String reason, Instant disbursedAt) {
        this.businessNo = businessNo;
        this.project = project;
        this.milestone = milestone;
        this.amount = amount;
        this.disbursedBy = disbursedBy;
        this.reason = reason;
        this.disbursedAt = disbursedAt;
    }

    /** 标记已对外结算；只有尚未撤销的放款可结算。 */
    public void markSettled(String by, String reason, Instant at) {
        if (status == DisbursementStatus.REVOKED) {
            throw new IllegalStateException("已撤销的放款不能结算：" + businessNo);
        }
        if (status == DisbursementStatus.SETTLED) {
            throw new IllegalStateException("放款已结算，不能重复结算：" + businessNo);
        }
        this.status = DisbursementStatus.SETTLED;
        this.settledBy = by;
        this.settledReason = reason;
        this.settledAt = at;
    }

    /** 整笔撤销；只有尚未对外结算的放款可撤销。 */
    public void markRevoked(String by, String reason, Instant at) {
        if (status != DisbursementStatus.DISBURSED) {
            throw new IllegalStateException("只有尚未结算的放款可以撤销：" + businessNo + "，当前状态 " + status);
        }
        this.status = DisbursementStatus.REVOKED;
        this.revokedBy = by;
        this.revokedReason = reason;
        this.revokedAt = at;
    }

    public boolean isSettled() {
        return status == DisbursementStatus.SETTLED;
    }

    public Long getId() {
        return id;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public EscrowProject getProject() {
        return project;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DisbursementStatus getStatus() {
        return status;
    }

    public String getDisbursedBy() {
        return disbursedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public String getSettledBy() {
        return settledBy;
    }

    public String getSettledReason() {
        return settledReason;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
