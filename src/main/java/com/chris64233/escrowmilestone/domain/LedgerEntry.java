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
 * 资金台账条目：只追加、不可修改、不可删除。
 *
 * <p>放款记借方出账；未结算放款撤销记贷方退回（恢复托管余额）；
 * 已结算放款的补偿记一条独立借方/贷方说明记录，不改动原放款流水与托管余额。
 * 每条记录均带处理人、原因与时间。
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private EscrowProject project;

    /** 台账业务号（如补偿业务号），可为空（系统生成的放款/撤销流水）。 */
    @Column(name = "business_no", updatable = false, length = 64)
    private String businessNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 16)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    private LedgerDirection direction;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** 关联放款流水；撤销与补偿均回指原放款。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_id", updatable = false)
    private Disbursement disbursement;

    @Column(nullable = false, updatable = false, length = 64)
    private String handledBy;

    @Column(nullable = false, updatable = false, length = 512)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant handledAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(EscrowProject project, String businessNo, LedgerEntryType entryType,
                       LedgerDirection direction, BigDecimal amount, Disbursement disbursement,
                       String handledBy, String reason, Instant handledAt) {
        this.project = project;
        this.businessNo = businessNo;
        this.entryType = entryType;
        this.direction = direction;
        this.amount = amount;
        this.disbursement = disbursement;
        this.handledBy = handledBy;
        this.reason = reason;
        this.handledAt = handledAt;
    }

    public Long getId() {
        return id;
    }

    public EscrowProject getProject() {
        return project;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Disbursement getDisbursement() {
        return disbursement;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
