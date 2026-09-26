package com.chris64233.escrowmilestone.service;

import com.chris64233.escrowmilestone.domain.ApprovalRequirement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 创建项目时的里程碑定义。
 */
public record MilestoneSpec(
        int sequence,
        String title,
        BigDecimal plannedAmount,
        Set<String> requiredEvidenceTypes,
        List<ApprovalRequirement> approvalRequirements) {
}
