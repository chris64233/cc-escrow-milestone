package com.chris64233.escrowmilestone.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** 服务层入参命令（创建项目）。 */
public final class Commands {

    private Commands() {
    }

    public record CreateProject(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @Positive BigDecimal totalAmount,
            @NotBlank String createdBy,
            @NotEmpty @Valid List<CreateMilestone> milestones) {
    }

    public record CreateMilestone(
            @NotBlank String name,
            @NotNull @Positive BigDecimal plannedAmount,
            @NotEmpty List<@NotBlank String> evidenceTypes,
            @NotEmpty List<@NotBlank String> approvalRoles) {
    }

    public record SubmitEvidence(
            int milestoneSequence,
            @NotBlank String evidenceType,
            @NotBlank String evidenceRef,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record ApprovalDecisionCommand(
            int milestoneSequence,
            @NotBlank String role,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record Disburse(
            @NotBlank String businessNo,
            int milestoneSequence,
            /** 放款金额，必须与里程碑计划金额一致；不一致也用于幂等内容变化判定。 */
            @NotNull @Positive BigDecimal amount,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record Revoke(
            @NotBlank String disbursementBusinessNo,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record Settle(
            @NotBlank String disbursementBusinessNo,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record Compensate(
            @NotBlank String businessNo,
            @NotBlank String disbursementBusinessNo,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }
}
