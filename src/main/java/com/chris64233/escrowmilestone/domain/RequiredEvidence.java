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
import java.time.Instant;

/** 里程碑所需证据及其提交情况。 */
@Entity
@Table(name = "required_evidence")
public class RequiredEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false, updatable = false)
    private Milestone milestone;

    /** 证据类型/名称，如“验收报告”。 */
    @Column(nullable = false, updatable = false, length = 128)
    private String evidenceType;

    @Column(nullable = false)
    private boolean provided = false;

    /** 证据内容定位（文件号/哈希/URL 等），提交时写入。 */
    @Column(name = "evidence_ref", length = 512)
    private String evidenceRef;

    /** 提交证据时记录的原因/说明，未提交前为空。 */
    @Column(length = 512)
    private String reason;

    @Column(name = "provided_by", length = 64)
    private String providedBy;

    @Column(name = "provided_at")
    private Instant providedAt;

    protected RequiredEvidence() {
    }

    public RequiredEvidence(Milestone milestone, String evidenceType) {
        this.milestone = milestone;
        this.evidenceType = evidenceType;
    }

    /** 登记证据提交（只追加事实，允许补录定位信息）。 */
    public void markProvided(String evidenceRef, String reason, String providedBy, Instant at) {
        this.provided = true;
        this.evidenceRef = evidenceRef;
        this.reason = reason;
        this.providedBy = providedBy;
        this.providedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public boolean isProvided() {
        return provided;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getReason() {
        return reason;
    }

    public String getProvidedBy() {
        return providedBy;
    }

    public Instant getProvidedAt() {
        return providedAt;
    }
}
