package com.chris64233.escrowmilestone.service;

import com.chris64233.escrowmilestone.domain.MilestoneStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 里程碑状态视图：包含证据与审批门槛的达成进度。
 */
public record MilestoneStatusView(
        int sequence,
        String title,
        BigDecimal plannedAmount,
        MilestoneStatus status,
        Set<String> submittedEvidenceTypes,
        Set<String> missingEvidenceTypes,
        List<RoleApprovalProgress> approvalProgress,
        boolean readyToRelease) {

    public record RoleApprovalProgress(String role, int requiredCount, long approvedCount, boolean satisfied) {
    }
}
