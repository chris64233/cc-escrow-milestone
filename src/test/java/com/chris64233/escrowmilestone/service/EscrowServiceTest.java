package com.chris64233.escrowmilestone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.escrowmilestone.domain.ApprovalRequirement;
import com.chris64233.escrowmilestone.domain.Disbursement;
import com.chris64233.escrowmilestone.domain.DisbursementStatus;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.LedgerDirection;
import com.chris64233.escrowmilestone.domain.LedgerEntry;
import com.chris64233.escrowmilestone.domain.LedgerEntryType;
import com.chris64233.escrowmilestone.domain.Milestone;
import com.chris64233.escrowmilestone.domain.MilestoneStatus;
import com.chris64233.escrowmilestone.support.DatabaseCleaner;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 顺序业务规则测试。 */
@SpringBootTest
class EscrowServiceTest {

    @Autowired
    private EscrowService service;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void clean() {
        cleaner.clean();
    }

    private static final String PROJECT = "P-SEQ";

    private Commands.CreateProject twoMilestoneProject() {
        return new Commands.CreateProject(
                PROJECT, "顺序测试项目", new BigDecimal("1000.00"), "ops",
                List.of(
                        new Commands.CreateMilestone("M1", new BigDecimal("400.00"),
                                List.of("验收报告"), List.of("PM", "FINANCE")),
                        new Commands.CreateMilestone("M2", new BigDecimal("600.00"),
                                List.of("验收报告", "发票"), List.of("PM"))));
    }

    private void readyMilestone1() {
        service.submitEvidence(PROJECT, new Commands.SubmitEvidence(
                1, "验收报告", "ref-ev1", "证据齐全", "qa"));
        service.approve(PROJECT, new Commands.ApprovalDecisionCommand(1, "PM", "同意", "pm"));
        service.approve(PROJECT, new Commands.ApprovalDecisionCommand(1, "FINANCE", "同意", "cfo"));
    }

    @Test
    void rejectsProjectWhenMilestoneSumsDifferFromTotal() {
        Commands.CreateProject cmd = new Commands.CreateProject(
                "P-SUM", "金额不符", new BigDecimal("1000.00"), "ops",
                List.of(new Commands.CreateMilestone("M1", new BigDecimal("999.99"),
                        List.of("验收报告"), List.of("PM"))));
        assertThatThrownBy(() -> service.createProject(cmd))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须等于托管金额");
    }

    @Test
    void fullLifecycleEvidenceApprovalDisbursementSettle() {
        service.createProject(twoMilestoneProject());

        // 证据不齐不能放款
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "D1", 1, new BigDecimal("400.00"), "首次放款", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("证据尚未齐全");

        service.submitEvidence(PROJECT, new Commands.SubmitEvidence(
                1, "验收报告", "ref-ev1", "证据齐全", "qa"));

        // 审批未全部通过不能放款
        service.approve(PROJECT, new Commands.ApprovalDecisionCommand(1, "PM", "同意", "pm"));
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "D1", 1, new BigDecimal("400.00"), "首次放款", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("审批尚未全部通过");

        service.approve(PROJECT, new Commands.ApprovalDecisionCommand(1, "FINANCE", "同意", "cfo"));

        Disbursement d1 = service.disburse(PROJECT, new Commands.Disburse(
                "D1", 1, new BigDecimal("400.00"), "首次放款", "teller"));
        assertThat(d1.getStatus()).isEqualTo(DisbursementStatus.DISBURSED);

        // 原子扣减托管余额
        EscrowProject project = service.getProject(PROJECT);
        assertThat(project.getRemainingAmount()).isEqualByComparingTo("600.00");

        // 前置里程碑未放款，M2 不能放款
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "D2", 2, new BigDecimal("600.00"), "二期", "teller")))
                .isInstanceOf(BusinessRuleException.class);

        // M1 已放款，重复放款被拒；放款后审批不可变更
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "D-AGAIN", 1, new BigDecimal("400.00"), "重复", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已放款");
        assertThatThrownBy(() -> service.withdrawApproval(PROJECT,
                new Commands.ApprovalDecisionCommand(1, "PM", "撤回", "pm")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已放款");

        // 完成 M2 并放款
        service.submitEvidence(PROJECT, new Commands.SubmitEvidence(
                2, "验收报告", "ref-ev2", "证据", "qa"));
        service.submitEvidence(PROJECT, new Commands.SubmitEvidence(
                2, "发票", "inv-2", "证据", "qa"));
        service.approve(PROJECT, new Commands.ApprovalDecisionCommand(2, "PM", "同意", "pm"));
        Disbursement d2 = service.disburse(PROJECT, new Commands.Disburse(
                "D2", 2, new BigDecimal("600.00"), "二期放款", "teller"));

        assertThat(service.getProject(PROJECT).getRemainingAmount())
                .isEqualByComparingTo("0.00");

        // 后置里程碑已放款，不能撤销前置 M1
        assertThatThrownBy(() -> service.revoke(new Commands.Revoke("D1", "想撤销", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("后置里程碑");

        // 结算 D2 后不可撤销、不可重复结算，只能补偿
        service.settle(new Commands.Settle("D2", "对外结算", "settler"));
        assertThatThrownBy(() -> service.revoke(new Commands.Revoke("D2", "想撤销", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已对外结算");
        assertThatThrownBy(() -> service.settle(new Commands.Settle("D2", "重复结算", "settler")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已结算");

        LedgerEntry compensation = service.compensate(new Commands.Compensate(
                "C1", "D2", new BigDecimal("100.00"), "部分补偿", "fin"));
        assertThat(compensation.getEntryType()).isEqualTo(LedgerEntryType.COMPENSATION);
        assertThat(compensation.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        // 补偿不动托管余额
        assertThat(service.getProject(PROJECT).getRemainingAmount())
                .isEqualByComparingTo("0.00");

        // 台账只追加：DISBURSEMENT *2 + COMPENSATION *1
        List<LedgerEntry> ledger = service.ledger(PROJECT);
        assertThat(ledger).hasSize(3);
        assertThat(ledger).extracting(LedgerEntry::getEntryType).containsExactly(
                LedgerEntryType.DISBURSEMENT, LedgerEntryType.DISBURSEMENT,
                LedgerEntryType.COMPENSATION);

        // 原放款流水未被覆盖（重新查询，避免持有过期实体）
        Disbursement settledD2 = service.getDisbursement("D2");
        assertThat(settledD2.getStatus()).isEqualTo(DisbursementStatus.SETTLED);
        assertThat(settledD2.getSettledBy()).isEqualTo("settler");
        assertThat(settledD2.getSettledReason()).isEqualTo("对外结算");

        // 里程碑状态查询
        List<Milestone> milestones = service.listMilestones(PROJECT);
        assertThat(milestones).extracting(Milestone::getStatus)
                .containsExactly(MilestoneStatus.DISBURSED, MilestoneStatus.DISBURSED);
    }

    @Test
    void idempotentDisburseReturnsFirstResultAndDetectsChangedContent() {
        service.createProject(twoMilestoneProject());
        readyMilestone1();

        Commands.Disburse first = new Commands.Disburse(
                "DUP", 1, new BigDecimal("400.00"), "首次放款", "teller");
        Disbursement d1 = service.disburse(PROJECT, first);

        // 相同内容重复提交：返回首次结果（同一实体）
        Disbursement again = service.disburse(PROJECT, first);
        assertThat(again.getId()).isEqualTo(d1.getId());
        assertThat(service.getProject(PROJECT).getRemainingAmount())
                .isEqualByComparingTo("600.00");
        assertThat(service.ledger(PROJECT)).hasSize(1);

        // 内容变化（金额不同）：冲突
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "DUP", 1, new BigDecimal("401.00"), "金额变了", "teller")))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("不同内容");

        // 内容变化（里程碑不同）：冲突
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "DUP", 2, new BigDecimal("400.00"), "首次放款", "teller")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void revokeUnsettledDisbursementRestoresBalanceAndAllowsRedisburse() {
        service.createProject(twoMilestoneProject());
        readyMilestone1();

        Disbursement d1 = service.disburse(PROJECT, new Commands.Disburse(
                "RV", 1, new BigDecimal("400.00"), "首次放款", "teller"));
        assertThat(service.getProject(PROJECT).getRemainingAmount())
                .isEqualByComparingTo("600.00");

        Disbursement revoked = service.revoke(new Commands.Revoke("RV", "验收异议", "manager"));
        assertThat(revoked.getStatus()).isEqualTo(DisbursementStatus.REVOKED);
        assertThat(revoked.getRevokedBy()).isEqualTo("manager");
        assertThat(revoked.getRevokedReason()).isEqualTo("验收异议");
        assertThat(revoked.getRevokedAt()).isNotNull();

        // 余额恢复、里程碑回到待放款
        assertThat(service.getProject(PROJECT).getRemainingAmount())
                .isEqualByComparingTo("1000.00");
        Milestone m1 = service.listMilestones(PROJECT).get(0);
        assertThat(m1.getStatus()).isEqualTo(MilestoneStatus.PENDING);

        // 台账追加撤销贷方，原放款流水保留
        List<LedgerEntry> ledger = service.ledger(PROJECT);
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(1).getEntryType()).isEqualTo(LedgerEntryType.REVERSAL);
        assertThat(ledger.get(1).getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(ledger.get(1).getAmount()).isEqualByComparingTo("400.00");

        // 不能重复撤销
        assertThatThrownBy(() -> service.revoke(new Commands.Revoke("RV", "再撤销", "manager")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已撤销");

        // 审批撤回后需要重新审批方可再次放款
        service.withdrawApproval(PROJECT,
                new Commands.ApprovalDecisionCommand(1, "FINANCE", "撤回复核", "cfo"));
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "RV2", 1, new BigDecimal("400.00"), "重新放款", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("审批尚未全部通过");
        service.approve(PROJECT,
                new Commands.ApprovalDecisionCommand(1, "FINANCE", "重新同意", "cfo"));
        Disbursement reDisbursed = service.disburse(PROJECT, new Commands.Disburse(
                "RV2", 1, new BigDecimal("400.00"), "重新放款", "teller"));
        assertThat(reDisbursed.getBusinessNo()).isEqualTo("RV2");
        assertThat(service.getProject(PROJECT).getRemainingAmount())
                .isEqualByComparingTo("600.00");
    }

    @Test
    void rejectsCompensationBeforeSettlement() {
        service.createProject(twoMilestoneProject());
        readyMilestone1();
        service.disburse(PROJECT, new Commands.Disburse(
                "CP", 1, new BigDecimal("400.00"), "放款", "teller"));

        assertThatThrownBy(() -> service.compensate(new Commands.Compensate(
                "C-NOPE", "CP", new BigDecimal("10.00"), "未结算先补偿", "fin")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有已结算放款才能登记补偿");
    }

    @Test
    void rejectsDisburseAmountDifferentFromPlanned() {
        service.createProject(twoMilestoneProject());
        readyMilestone1();
        assertThatThrownBy(() -> service.disburse(PROJECT, new Commands.Disburse(
                "D-AMT", 1, new BigDecimal("399.99"), "少放款", "teller")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须等于里程碑计划金额");
    }

    @Test
    void evidenceCannotBeModifiedOnceSubmitted() {
        service.createProject(twoMilestoneProject());
        service.submitEvidence(PROJECT, new Commands.SubmitEvidence(
                1, "验收报告", "ref-1", "首次提交", "qa"));
        assertThatThrownBy(() -> service.submitEvidence(PROJECT, new Commands.SubmitEvidence(
                1, "验收报告", "ref-2", "想改证据", "qa")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("证据已提交且不可修改");
    }

    @Test
    void unknownResourcesRaiseNotFound() {
        assertThatThrownBy(() -> service.getProject("NO-SUCH"))
                .isInstanceOf(NotFoundException.class);
    }
}
