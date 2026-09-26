package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 托管项目：记录总托管金额与一组有顺序的里程碑。
 *
 * <p>{@code remainingAmount} 是尚未放款占用的托管余额；
 * 并发安全由数据库行悲观锁保证（见 EscrowProjectRepository 的加锁查询）。
 */
@Entity
@Table(name = "escrow_project")
public class EscrowProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外业务编码，唯一。 */
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 总托管金额，创建后不可变。 */
    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** 剩余可放款余额。 */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingAmount;

    @Column(nullable = false, updatable = false, length = 64)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Milestone> milestones = new ArrayList<>();

    protected EscrowProject() {
    }

    public EscrowProject(String code, String name, BigDecimal totalAmount, String createdBy, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.totalAmount = totalAmount;
        this.remainingAmount = totalAmount;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** 原子扣减托管余额，余额不足时拒绝（在持锁事务内调用）。 */
    public void debit(BigDecimal amount) {
        if (remainingAmount.compareTo(amount) < 0) {
            throw new IllegalStateException("托管余额不足：剩余 " + remainingAmount + "，申请扣减 " + amount);
        }
        remainingAmount = remainingAmount.subtract(amount);
    }

    /** 撤销放款时恢复托管余额。 */
    public void credit(BigDecimal amount) {
        remainingAmount = remainingAmount.add(amount);
    }

    public void addMilestone(Milestone milestone) {
        milestones.add(milestone);
    }

    public List<Milestone> orderedMilestones() {
        return milestones.stream()
                .sorted(Comparator.comparingInt(Milestone::getSequenceNo))
                .toList();
    }

    public List<Milestone> getMilestones() {
        return Collections.unmodifiableList(milestones);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
