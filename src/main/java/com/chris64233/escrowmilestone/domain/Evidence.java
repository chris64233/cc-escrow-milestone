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

/**
 * 里程碑验收证据。
 */
@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String submittedBy;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    protected Evidence() {
    }

    public Evidence(Milestone milestone, String type, String submittedBy, String content, Instant submittedAt) {
        this.milestone = milestone;
        this.type = type;
        this.submittedBy = submittedBy;
        this.content = content;
        this.submittedAt = submittedAt;
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

    public String getType() {
        return type;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public String getContent() {
        return content;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
