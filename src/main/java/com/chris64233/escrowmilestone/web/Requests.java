package com.chris64233.escrowmilestone.web;

import com.chris64233.escrowmilestone.domain.ApprovalDecision;
import com.chris64233.escrowmilestone.domain.ApprovalRequirement;
import com.chris64233.escrowmilestone.service.MilestoneSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * REST 请求 DTO。
 */
public final class Requests {

    private Requests() {
    }

    public record CreateProjectRequest(
            @NotBlank String name,
            @NotNull @Positive BigDecimal totalAmount,
            @NotEmpty List<@Valid MilestoneSpecRequest> milestones) {
    }

    public record MilestoneSpecRequest(
            @Positive int sequence,
            String title,
            @NotNull @Positive BigDecimal plannedAmount,
            Set<String> requiredEvidenceTypes,
            List<@Valid ApprovalRequirementRequest> approvalRequirements) {

        MilestoneSpec toSpec() {
            return new MilestoneSpec(sequence, title, plannedAmount,
                    requiredEvidenceTypes == null ? Set.of() : requiredEvidenceTypes,
                    approvalRequirements == null ? List.of()
                            : approvalRequirements.stream().map(ApprovalRequirementRequest::toDomain).toList());
        }
    }

    public record ApprovalRequirementRequest(@NotBlank String role, @Positive int requiredCount) {
        ApprovalRequirement toDomain() {
            return new ApprovalRequirement(role, requiredCount);
        }
    }

    public record EvidenceRequest(
            @NotBlank String type,
            @NotBlank String submittedBy,
            @NotBlank String content) {
    }

    public record ApprovalRequest(
            @NotBlank String role,
            @NotBlank String approver,
            @NotNull ApprovalDecision decision,
            String reason) {
    }

    public record ReleaseRequest(
            @NotBlank String bizNo,
            @NotBlank String operator,
            String reason) {
    }

    public record OperatorRequest(
            @NotBlank String operator,
            String reason) {
    }

    public record CompensateRequest(
            @NotBlank String operator,
            @NotNull @Positive BigDecimal amount,
            String reason) {
    }
}
