package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 里程碑审批门槛：某个角色需要的有效审批人数。
 */
@Embeddable
public class ApprovalRequirement {

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "required_count", nullable = false)
    private int requiredCount;

    protected ApprovalRequirement() {
    }

    public ApprovalRequirement(String role, int requiredCount) {
        this.role = Objects.requireNonNull(role, "role");
        this.requiredCount = requiredCount;
    }

    public String getRole() {
        return role;
    }

    public int getRequiredCount() {
        return requiredCount;
    }
}
