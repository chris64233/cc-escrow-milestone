package com.chris64233.escrowmilestone.web;

import com.chris64233.escrowmilestone.domain.Approval;
import com.chris64233.escrowmilestone.domain.CompensationRecord;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.Evidence;
import com.chris64233.escrowmilestone.domain.FundRecord;
import com.chris64233.escrowmilestone.domain.Payout;
import com.chris64233.escrowmilestone.service.EscrowService;
import com.chris64233.escrowmilestone.service.MilestoneStatusView;
import com.chris64233.escrowmilestone.service.ProjectBalanceView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class EscrowController {

    private final EscrowService escrowService;

    public EscrowController(EscrowService escrowService) {
        this.escrowService = escrowService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EscrowProject createProject(@Valid @RequestBody Requests.CreateProjectRequest request) {
        return escrowService.createProject(request.name(), request.totalAmount(),
                request.milestones().stream().map(Requests.MilestoneSpecRequest::toSpec).toList());
    }

    @GetMapping("/{projectId}/balance")
    public ProjectBalanceView balance(@PathVariable Long projectId) {
        return escrowService.balanceView(projectId);
    }

    @GetMapping("/{projectId}/milestones")
    public List<MilestoneStatusView> milestones(@PathVariable Long projectId) {
        return escrowService.milestoneViews(projectId);
    }

    @PostMapping("/{projectId}/milestones/{sequence}/evidences")
    @ResponseStatus(HttpStatus.CREATED)
    public Evidence submitEvidence(@PathVariable Long projectId, @PathVariable int sequence,
                                   @Valid @RequestBody Requests.EvidenceRequest request) {
        return escrowService.submitEvidence(projectId, sequence,
                request.type(), request.submittedBy(), request.content());
    }

    @PostMapping("/{projectId}/milestones/{sequence}/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public Approval decide(@PathVariable Long projectId, @PathVariable int sequence,
                           @Valid @RequestBody Requests.ApprovalRequest request) {
        return escrowService.decide(projectId, sequence,
                request.role(), request.approver(), request.decision(), request.reason());
    }

    @GetMapping("/{projectId}/milestones/{sequence}/evidences")
    public List<Evidence> evidences(@PathVariable Long projectId, @PathVariable int sequence) {
        return escrowService.evidences(projectId, sequence);
    }

    @GetMapping("/{projectId}/milestones/{sequence}/approvals")
    public List<Approval> approvals(@PathVariable Long projectId, @PathVariable int sequence) {
        return escrowService.approvals(projectId, sequence);
    }

    @PostMapping("/{projectId}/milestones/{sequence}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public Payout release(@PathVariable Long projectId, @PathVariable int sequence,
                          @Valid @RequestBody Requests.ReleaseRequest request) {
        return escrowService.release(projectId, sequence, request.bizNo(), request.operator(), request.reason());
    }

    @GetMapping("/{projectId}/payouts")
    public List<Payout> payouts(@PathVariable Long projectId) {
        return escrowService.payouts(projectId);
    }

    @PostMapping("/{projectId}/payouts/{payoutId}/reverse")
    public Payout reverse(@PathVariable Long projectId, @PathVariable Long payoutId,
                          @Valid @RequestBody Requests.OperatorRequest request) {
        return escrowService.reverse(projectId, payoutId, request.operator(), request.reason());
    }

    @PostMapping("/{projectId}/payouts/{payoutId}/settle")
    public Payout settle(@PathVariable Long projectId, @PathVariable Long payoutId,
                         @Valid @RequestBody Requests.OperatorRequest request) {
        return escrowService.settle(projectId, payoutId, request.operator());
    }

    @PostMapping("/{projectId}/payouts/{payoutId}/compensations")
    @ResponseStatus(HttpStatus.CREATED)
    public CompensationRecord compensate(@PathVariable Long projectId, @PathVariable Long payoutId,
                                         @Valid @RequestBody Requests.CompensateRequest request) {
        return escrowService.compensate(projectId, payoutId, request.amount(),
                request.operator(), request.reason());
    }

    @GetMapping("/{projectId}/payouts/{payoutId}/compensations")
    public List<CompensationRecord> compensations(@PathVariable Long projectId, @PathVariable Long payoutId) {
        return escrowService.compensations(projectId, payoutId);
    }

    @GetMapping("/{projectId}/ledger")
    public List<FundRecord> ledger(@PathVariable Long projectId) {
        return escrowService.ledger(projectId);
    }
}
