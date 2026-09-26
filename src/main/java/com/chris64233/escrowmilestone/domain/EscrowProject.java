package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 托管项目：记录总托管金额、当前余额和有序里程碑。
 */
@Entity
@Table(name = "escrow_project")
public class EscrowProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence")
    private List<Milestone> milestones = new ArrayList<>();

    protected EscrowProject() {
    }

    public EscrowProject(String name, BigDecimal totalAmount, Instant createdAt) {
        this.name = name;
        this.totalAmount = totalAmount;
        this.balance = totalAmount;
        this.createdAt = createdAt;
    }

    public void addMilestone(Milestone milestone) {
        milestones.add(milestone);
    }

    public Optional<Milestone> milestoneBySequence(int sequence) {
        return milestones.stream().filter(m -> m.getSequence() == sequence).findFirst();
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Milestone> getMilestones() {
        return Collections.unmodifiableList(milestones);
    }
}
