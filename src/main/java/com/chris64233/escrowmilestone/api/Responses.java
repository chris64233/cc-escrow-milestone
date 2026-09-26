package com.chris64233.escrowmilestone.api;

import com.chris64233.escrowmilestone.domain.ApprovalStatus;
import com.chris64233.escrowmilestone.domain.Disbursement;
import com.chris64233.escrowmilestone.domain.DisbursementStatus;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.LedgerDirection;
import com.chris64233.escrowmilestone.domain.LedgerEntry;
import com.chris64233.escrowmilestone.domain.LedgerEntryType;
import com.chris64233.escrowmilestone.domain.Milestone;
import com.chris64233.escrowmilestone.domain.MilestoneStatus;
import com.chris64233.escrowmilestone.domain.RequiredEvidence;
import com.chris64233.escrowmilestone.domain.ApprovalRequirement;
import java.math.BigDecimal;
import java.time.Instant;

/** 对外响应 DTO。 */
public final class Responses {

    private Responses() {
    }

    public record ProjectView(Long id, String code, String name, BigDecimal totalAmount,
                              BigDecimal remainingAmount, String createdBy, Instant createdAt) {

        public static ProjectView of(EscrowProject p) {
            return new ProjectView(p.getId(), p.getCode(), p.getName(), p.getTotalAmount(),
                    p.getRemainingAmount(), p.getCreatedBy(), p.getCreatedAt());
        }
    }

    public record EvidenceView(String evidenceType, boolean provided, String evidenceRef,
                               String reason, String providedBy, Instant providedAt) {

        public static EvidenceView of(RequiredEvidence e) {
            return new EvidenceView(e.getEvidenceType(), e.isProvided(), e.getEvidenceRef(),
                    e.getReason(), e.getProvidedBy(), e.getProvidedAt());
        }
    }

    public record ApprovalView(String requiredRole, ApprovalStatus status,
                               String lastDecidedBy, Instant lastDecidedAt) {

        public static ApprovalView of(ApprovalRequirement r) {
            return new ApprovalView(r.getRequiredRole(), r.getStatus(),
                    r.getLastDecidedBy(), r.getLastDecidedAt());
        }
    }

    public record MilestoneView(int sequenceNo, String name, BigDecimal plannedAmount,
                                MilestoneStatus status,
                                String currentDisbursementBusinessNo,
                                java.util.List<EvidenceView> evidences,
                                java.util.List<ApprovalView> approvals) {

        public static MilestoneView of(Milestone m) {
            return new MilestoneView(
                    m.getSequenceNo(),
                    m.getName(),
                    m.getPlannedAmount(),
                    m.getStatus(),
                    m.getCurrentDisbursement() == null ? null : m.getCurrentDisbursement().getBusinessNo(),
                    m.getRequiredEvidences().stream().map(EvidenceView::of).toList(),
                    m.getApprovalRequirements().stream().map(ApprovalView::of).toList());
        }
    }

    public record DisbursementView(String businessNo, int milestoneSequence, BigDecimal amount,
                                   DisbursementStatus status, String disbursedBy, String reason,
                                   Instant disbursedAt,
                                   String settledBy, String settledReason, Instant settledAt,
                                   String revokedBy, String revokedReason, Instant revokedAt) {

        public static DisbursementView of(Disbursement d) {
            return new DisbursementView(
                    d.getBusinessNo(),
                    d.getMilestone().getSequenceNo(),
                    d.getAmount(),
                    d.getStatus(),
                    d.getDisbursedBy(),
                    d.getReason(),
                    d.getDisbursedAt(),
                    d.getSettledBy(),
                    d.getSettledReason(),
                    d.getSettledAt(),
                    d.getRevokedBy(),
                    d.getRevokedReason(),
                    d.getRevokedAt());
        }
    }

    public record LedgerView(Long id, String businessNo, LedgerEntryType entryType,
                             LedgerDirection direction, BigDecimal amount,
                             String disbursementBusinessNo,
                             String handledBy, String reason, Instant handledAt) {

        public static LedgerView of(LedgerEntry e) {
            return new LedgerView(
                    e.getId(),
                    e.getBusinessNo(),
                    e.getEntryType(),
                    e.getDirection(),
                    e.getAmount(),
                    e.getDisbursement() == null ? null : e.getDisbursement().getBusinessNo(),
                    e.getHandledBy(),
                    e.getReason(),
                    e.getHandledAt());
        }
    }
}
