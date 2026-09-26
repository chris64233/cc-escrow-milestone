package com.chris64233.escrowmilestone;

import com.chris64233.escrowmilestone.domain.ApprovalDecision;
import com.chris64233.escrowmilestone.domain.ApprovalRequirement;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.FundRecord;
import com.chris64233.escrowmilestone.domain.FundRecordType;
import com.chris64233.escrowmilestone.domain.MilestoneStatus;
import com.chris64233.escrowmilestone.domain.Payout;
import com.chris64233.escrowmilestone.domain.PayoutStatus;
import com.chris64233.escrowmilestone.service.BusinessException;
import com.chris64233.escrowmilestone.service.EscrowService;
import com.chris64233.escrowmilestone.service.MilestoneSpec;
import com.chris64233.escrowmilestone.service.MilestoneStatusView;
import com.chris64233.escrowmilestone.service.ProjectBalanceView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EscrowServiceTest {

    @Autowired
    private EscrowService service;

    private EscrowProject newProject() {
        return service.createProject("托管项目", new BigDecimal("100.00"), List.of(
                new MilestoneSpec(1, "需求确认", new BigDecimal("40.00"),
                        Set.of("验收报告"), List.of(new ApprovalRequirement("PM", 1))),
                new MilestoneSpec(2, "上线交付", new BigDecimal("60.00"),
                        Set.of("测试报告", "上线单"),
                        List.of(new ApprovalRequirement("PM", 1), new ApprovalRequirement("QA", 2)))));
    }

    private void prepareMilestone1(Long projectId) {
        service.submitEvidence(projectId, 1, "验收报告", "bob", "验收通过");
        service.decide(projectId, 1, "PM", "alice", ApprovalDecision.APPROVE, "同意");
    }

    // ---------- 项目创建 ----------

    @Test
    void createProjectRejectsMilestoneSumMismatch() {
        assertThatThrownBy(() -> service.createProject("坏项目", new BigDecimal("100.00"), List.of(
                new MilestoneSpec(1, "m1", new BigDecimal("30.00"), Set.of(), List.of()),
                new MilestoneSpec(2, "m2", new BigDecimal("60.00"), Set.of(), List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须等于托管金额");
    }

    @Test
    void createProjectRejectsDuplicateSequence() {
        assertThatThrownBy(() -> service.createProject("坏项目", new BigDecimal("100.00"), List.of(
                new MilestoneSpec(1, "m1", new BigDecimal("50.00"), Set.of(), List.of()),
                new MilestoneSpec(1, "m2", new BigDecimal("50.00"), Set.of(), List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("序号重复");
    }

    @Test
    void createProjectInitializesBalanceToTotalAmount() {
        EscrowProject project = newProject();
        ProjectBalanceView view = service.balanceView(project.getId());
        assertThat(view.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(view.balance()).isEqualByComparingTo("100.00");
        assertThat(view.releasedAmount()).isEqualByComparingTo("0");
    }

    // ---------- 放款门槛 ----------

    @Test
    void releaseFailsWithoutEvidence() {
        EscrowProject project = newProject();
        Long id = project.getId();
        service.decide(id, 1, "PM", "alice", ApprovalDecision.APPROVE, "同意");
        assertThatThrownBy(() -> service.release(id, 1, "BIZ-1-" + id, "ops", "放款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("证据不齐全");
    }

    @Test
    void releaseFailsWithoutApprovalThreshold() {
        EscrowProject project = newProject();
        Long id = project.getId();
        service.submitEvidence(id, 1, "验收报告", "bob", "验收通过");
        assertThatThrownBy(() -> service.release(id, 1, "BIZ-1-" + id, "ops", "放款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批门槛未满足");
    }

    @Test
    void releaseFailsWhenPriorMilestoneNotReleased() {
        EscrowProject project = newProject();
        Long id = project.getId();
        service.submitEvidence(id, 2, "测试报告", "bob", "ok");
        service.submitEvidence(id, 2, "上线单", "bob", "ok");
        service.decide(id, 2, "PM", "alice", ApprovalDecision.APPROVE, "ok");
        service.decide(id, 2, "QA", "q1", ApprovalDecision.APPROVE, "ok");
        service.decide(id, 2, "QA", "q2", ApprovalDecision.APPROVE, "ok");
        assertThatThrownBy(() -> service.release(id, 2, "BIZ-2-" + id, "ops", "放款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("前置里程碑");
    }

    @Test
    void releaseSucceedsAndWritesImmutableLedger() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);

        Payout payout = service.release(id, 1, "BIZ-1-" + id, "ops", "首期放款");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RELEASED);
        assertThat(payout.getAmount()).isEqualByComparingTo("40.00");
        assertThat(payout.getOperator()).isEqualTo("ops");
        assertThat(payout.getReason()).isEqualTo("首期放款");
        assertThat(payout.getCreatedAt()).isNotNull();

        ProjectBalanceView view = service.balanceView(id);
        assertThat(view.balance()).isEqualByComparingTo("60.00");
        assertThat(view.releasedAmount()).isEqualByComparingTo("40.00");

        List<FundRecord> ledger = service.ledger(id);
        assertThat(ledger).hasSize(1);
        FundRecord record = ledger.get(0);
        assertThat(record.getType()).isEqualTo(FundRecordType.RELEASE);
        assertThat(record.getAmount()).isEqualByComparingTo("40.00");
        assertThat(record.getBalanceAfter()).isEqualByComparingTo("60.00");
        assertThat(record.getOperator()).isEqualTo("ops");
        assertThat(record.getOccurredAt()).isNotNull();

        List<MilestoneStatusView> milestones = service.milestoneViews(id);
        assertThat(milestones.get(0).status()).isEqualTo(MilestoneStatus.RELEASED);
    }

    @Test
    void approvalThresholdRequiresDistinctApprovers() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);
        service.release(id, 1, "BIZ-1-" + id, "ops", "首期");

        service.submitEvidence(id, 2, "测试报告", "bob", "ok");
        service.submitEvidence(id, 2, "上线单", "bob", "ok");
        service.decide(id, 2, "PM", "alice", ApprovalDecision.APPROVE, "ok");
        service.decide(id, 2, "QA", "q1", ApprovalDecision.APPROVE, "ok");
        // QA 需要 2 人，同一审批人重复同意只算一次
        service.decide(id, 2, "QA", "q1", ApprovalDecision.APPROVE, "again");

        assertThatThrownBy(() -> service.release(id, 2, "BIZ-2-" + id, "ops", "二期"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批门槛未满足");

        service.decide(id, 2, "QA", "q2", ApprovalDecision.APPROVE, "ok");
        Payout payout = service.release(id, 2, "BIZ-2-" + id, "ops", "二期");
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RELEASED);
        assertThat(service.balanceView(id).balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void revokedApprovalDoesNotCount() {
        EscrowProject project = newProject();
        Long id = project.getId();
        service.submitEvidence(id, 1, "验收报告", "bob", "ok");
        service.decide(id, 1, "PM", "alice", ApprovalDecision.APPROVE, "ok");
        service.decide(id, 1, "PM", "alice", ApprovalDecision.REVOKE, "发现问题");

        assertThatThrownBy(() -> service.release(id, 1, "BIZ-1-" + id, "ops", "放款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批门槛未满足");
    }

    // ---------- 幂等 ----------

    @Test
    void releaseIsIdempotentForSameBizNoAndContent() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);

        Payout first = service.release(id, 1, "BIZ-1-" + id, "ops", "放款");
        Payout replay = service.release(id, 1, "BIZ-1-" + id, "ops", "放款");

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(service.balanceView(id).balance()).isEqualByComparingTo("60.00");
        assertThat(service.ledger(id)).hasSize(1);
    }

    @Test
    void releaseConflictsWhenSameBizNoWithDifferentContent() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);
        service.release(id, 1, "BIZ-1-" + id, "ops", "放款");

        assertThatThrownBy(() -> service.release(id, 2, "BIZ-1-" + id, "ops", "放款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
    }

    // ---------- 撤销 / 结算 / 补偿 ----------

    @Test
    void reverseRestoresBalanceAndMilestoneBecomesReleasable() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);
        Payout payout = service.release(id, 1, "BIZ-1-" + id, "ops", "放款");

        Payout reversed = service.reverse(id, payout.getId(), "admin", "甲方取消");

        assertThat(reversed.getStatus()).isEqualTo(PayoutStatus.REVERSED);
        assertThat(reversed.getReversedAt()).isNotNull();
        assertThat(service.balanceView(id).balance()).isEqualByComparingTo("100.00");
        assertThat(service.milestoneViews(id).get(0).status()).isEqualTo(MilestoneStatus.PENDING);

        List<FundRecord> ledger = service.ledger(id);
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(1).getType()).isEqualTo(FundRecordType.REVERSE);
        assertThat(ledger.get(1).getReason()).isEqualTo("甲方取消");
        assertThat(ledger.get(1).getBalanceAfter()).isEqualByComparingTo("100.00");

        // 撤销后可用新业务号重新放款
        Payout again = service.release(id, 1, "BIZ-2-" + id, "ops", "重新放款");
        assertThat(again.getStatus()).isEqualTo(PayoutStatus.RELEASED);
    }

    @Test
    void reverseTwiceIsRejected() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);
        Payout payout = service.release(id, 1, "BIZ-1-" + id, "ops", "放款");
        service.reverse(id, payout.getId(), "admin", "取消");

        assertThatThrownBy(() -> service.reverse(id, payout.getId(), "admin", "再次取消"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已撤销");
    }

    @Test
    void settledPayoutCannotBeReversedOnlyCompensated() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);
        Payout payout = service.release(id, 1, "BIZ-1-" + id, "ops", "放款");
        service.settle(id, payout.getId(), "ops");

        assertThatThrownBy(() -> service.reverse(id, payout.getId(), "admin", "想撤销"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能登记补偿");

        var compensation = service.compensate(id, payout.getId(),
                new BigDecimal("10.00"), "admin", "质量扣款补偿");
        assertThat(compensation.getAmount()).isEqualByComparingTo("10.00");

        // 原流水保留，补偿只追加台账，不恢复余额
        List<FundRecord> ledger = service.ledger(id);
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(0).getType()).isEqualTo(FundRecordType.RELEASE);
        assertThat(ledger.get(1).getType()).isEqualTo(FundRecordType.COMPENSATE);
        assertThat(service.balanceView(id).balance()).isEqualByComparingTo("60.00");
        assertThat(service.payouts(id).get(0).getStatus()).isEqualTo(PayoutStatus.SETTLED);
    }

    @Test
    void compensateRequiresSettledPayout() {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);
        Payout payout = service.release(id, 1, "BIZ-1-" + id, "ops", "放款");

        assertThatThrownBy(() -> service.compensate(id, payout.getId(),
                new BigDecimal("5.00"), "admin", "补偿"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结算");
    }

    // ---------- 并发 ----------

    @Test
    void concurrentReleasesOfSameMilestoneOnlyOneSucceeds() throws Exception {
        EscrowProject project = newProject();
        Long id = project.getId();
        prepareMilestone1(id);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.release(id, 1, "BIZ-C-" + n, "ops", "并发");
                    succeeded.incrementAndGet();
                } catch (BusinessException e) {
                    failed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(threads - 1);
        assertThat(service.balanceView(id).balance()).isEqualByComparingTo("60.00");
        assertThat(service.ledger(id)).hasSize(1);
    }

    @Test
    void concurrentRevokeAndReleaseProduceOneConsistentOutcome() throws Exception {
        // 反复验证：审批撤回与放款并发时，只会形成"放款成功"或"放款失败"一种完整结果
        for (int round = 0; round < 10; round++) {
            EscrowProject project = newProject();
            Long id = project.getId();
            service.submitEvidence(id, 1, "验收报告", "bob", "ok");
            service.decide(id, 1, "PM", "alice", ApprovalDecision.APPROVE, "ok");

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger releaseSucceeded = new AtomicInteger();
            final int roundNo = round;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.release(id, 1, "BIZ-R-" + roundNo, "ops", "放款");
                    releaseSucceeded.incrementAndGet();
                } catch (BusinessException ignored) {
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.decide(id, 1, "PM", "alice", ApprovalDecision.REVOKE, "撤回");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            ready.await();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            ProjectBalanceView view = service.balanceView(id);
            List<FundRecord> ledger = service.ledger(id);
            if (releaseSucceeded.get() == 1) {
                // 放款先完成：余额扣减且台账一致，撤回记录保留但不影响已完成的放款
                assertThat(view.balance()).isEqualByComparingTo("60.00");
                assertThat(ledger).hasSize(1);
                assertThat(service.payouts(id).get(0).getStatus()).isEqualTo(PayoutStatus.RELEASED);
            } else {
                // 撤回先生效：放款失败，余额与台账无任何变化
                assertThat(view.balance()).isEqualByComparingTo("100.00");
                assertThat(ledger).isEmpty();
                assertThat(service.payouts(id)).isEmpty();
            }
        }
    }

    // ---------- 查询 ----------

    @Test
    void milestoneViewReportsEvidenceAndApprovalProgress() {
        EscrowProject project = newProject();
        Long id = project.getId();
        service.submitEvidence(id, 2, "测试报告", "bob", "ok");
        service.decide(id, 2, "QA", "q1", ApprovalDecision.APPROVE, "ok");

        MilestoneStatusView view = service.milestoneViews(id).get(1);
        assertThat(view.submittedEvidenceTypes()).containsExactly("测试报告");
        assertThat(view.missingEvidenceTypes()).containsExactly("上线单");
        assertThat(view.readyToRelease()).isFalse();
        assertThat(view.approvalProgress())
                .anySatisfy(p -> {
                    assertThat(p.role()).isEqualTo("QA");
                    assertThat(p.requiredCount()).isEqualTo(2);
                    assertThat(p.approvedCount()).isEqualTo(1);
                    assertThat(p.satisfied()).isFalse();
                });

        assertThat(service.evidences(id, 2)).hasSize(1);
        assertThat(service.approvals(id, 2)).hasSize(1);
    }
}
