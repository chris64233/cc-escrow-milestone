package com.chris64233.escrowmilestone.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** 对外请求 DTO。 */
public final class Requests {

    private Requests() {
    }

    public record CreateProjectRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @Positive BigDecimal totalAmount,
            @NotBlank String createdBy,
            @NotEmpty @Valid List<MilestoneSpec> milestones) {
    }

    public record MilestoneSpec(
            @NotBlank String name,
            @NotNull @Positive BigDecimal plannedAmount,
            @NotEmpty List<@NotBlank String> evidenceTypes,
            @NotEmpty List<@NotBlank String> approvalRoles) {
    }

    public record SubmitEvidenceRequest(
            int milestoneSequence,
            @NotBlank String evidenceType,
            @NotBlank String evidenceRef,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record ApprovalRequest(
            int milestoneSequence,
            @NotBlank String role,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record DisburseRequest(
            @NotBlank String businessNo,
            int milestoneSequence,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record RevokeRequest(
            @NotBlank String disbursementBusinessNo,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record SettleRequest(
            @NotBlank String disbursementBusinessNo,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }

    public record CompensateRequest(
            @NotBlank String businessNo,
            @NotBlank String disbursementBusinessNo,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String reason,
            @NotBlank String handledBy) {
    }
}
