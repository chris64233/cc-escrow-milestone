package com.chris64233.escrowmilestone.api;

import com.chris64233.escrowmilestone.api.Requests.ApprovalRequest;
import com.chris64233.escrowmilestone.api.Requests.CompensateRequest;
import com.chris64233.escrowmilestone.api.Requests.CreateProjectRequest;
import com.chris64233.escrowmilestone.api.Requests.DisburseRequest;
import com.chris64233.escrowmilestone.api.Requests.RevokeRequest;
import com.chris64233.escrowmilestone.api.Requests.SettleRequest;
import com.chris64233.escrowmilestone.api.Requests.SubmitEvidenceRequest;
import com.chris64233.escrowmilestone.api.Responses.DisbursementView;
import com.chris64233.escrowmilestone.api.Responses.LedgerView;
import com.chris64233.escrowmilestone.api.Responses.MilestoneView;
import com.chris64233.escrowmilestone.api.Responses.ProjectView;
import com.chris64233.escrowmilestone.service.Commands;
import com.chris64233.escrowmilestone.service.EscrowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 托管项目与里程碑资金接口。 */
@RestController
@RequestMapping("/api/projects")
public class EscrowController {

    private final EscrowService service;

    public EscrowController(EscrowService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView createProject(@Valid @RequestBody CreateProjectRequest req) {
        return ProjectView.of(service.createProject(toCommand(req)));
    }

    // ---------------- 余额 / 状态 / 证据 / 台账查询 ----------------

    @GetMapping("/{projectCode}")
    public ProjectView project(@PathVariable String projectCode) {
        return ProjectView.of(service.getProject(projectCode));
    }

    @GetMapping("/{projectCode}/milestones")
    public List<MilestoneView> milestones(@PathVariable String projectCode) {
        return service.listMilestones(projectCode).stream().map(MilestoneView::of).toList();
    }

    @GetMapping("/{projectCode}/disbursements")
    public List<DisbursementView> disbursements(@PathVariable String projectCode) {
        return service.listDisbursements(projectCode).stream().map(DisbursementView::of).toList();
    }

    @GetMapping("/{projectCode}/ledger")
    public List<LedgerView> ledger(@PathVariable String projectCode) {
        return service.ledger(projectCode).stream().map(LedgerView::of).toList();
    }

    // ---------------- 证据 / 审批 ----------------

    @PostMapping("/{projectCode}/evidence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitEvidence(@PathVariable String projectCode,
                               @Valid @RequestBody SubmitEvidenceRequest req) {
        service.submitEvidence(projectCode, new Commands.SubmitEvidence(
                req.milestoneSequence(), req.evidenceType(), req.evidenceRef(),
                req.reason(), req.handledBy()));
    }

    @PostMapping("/{projectCode}/approvals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable String projectCode,
                        @Valid @RequestBody ApprovalRequest req) {
        service.approve(projectCode, new Commands.ApprovalDecisionCommand(
                req.milestoneSequence(), req.role(), req.reason(), req.handledBy()));
    }

    @PostMapping("/{projectCode}/approvals/withdrawals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawApproval(@PathVariable String projectCode,
                                 @Valid @RequestBody ApprovalRequest req) {
        service.withdrawApproval(projectCode, new Commands.ApprovalDecisionCommand(
                req.milestoneSequence(), req.role(), req.reason(), req.handledBy()));
    }

    // ---------------- 放款 / 撤销 / 结算 / 补偿 ----------------

    @PostMapping("/{projectCode}/disbursements")
    public DisbursementView disburse(@PathVariable String projectCode,
                                     @Valid @RequestBody DisburseRequest req) {
        return DisbursementView.of(service.disburse(projectCode, new Commands.Disburse(
                req.businessNo(), req.milestoneSequence(), req.amount(),
                req.reason(), req.handledBy())));
    }

    @PostMapping("/disbursements/revocations")
    public DisbursementView revoke(@Valid @RequestBody RevokeRequest req) {
        return DisbursementView.of(service.revoke(new Commands.Revoke(
                req.disbursementBusinessNo(), req.reason(), req.handledBy())));
    }

    @PostMapping("/disbursements/settlements")
    public DisbursementView settle(@Valid @RequestBody SettleRequest req) {
        return DisbursementView.of(service.settle(new Commands.Settle(
                req.disbursementBusinessNo(), req.reason(), req.handledBy())));
    }

    @PostMapping("/disbursements/compensations")
    public LedgerView compensate(@Valid @RequestBody CompensateRequest req) {
        return LedgerView.of(service.compensate(new Commands.Compensate(
                req.businessNo(), req.disbursementBusinessNo(), req.amount(),
                req.reason(), req.handledBy())));
    }

    private static Commands.CreateProject toCommand(CreateProjectRequest req) {
        return new Commands.CreateProject(
                req.code(), req.name(), req.totalAmount(), req.createdBy(),
                req.milestones().stream()
                        .map(m -> new Commands.CreateMilestone(
                                m.name(), m.plannedAmount(), m.evidenceTypes(), m.approvalRoles()))
                        .toList());
    }
}
