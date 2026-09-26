package com.chris64233.escrowmilestone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.escrowmilestone.domain.ApprovalStatus;
import com.chris64233.escrowmilestone.domain.EscrowProject;
import com.chris64233.escrowmilestone.domain.LedgerEntry;
import com.chris64233.escrowmilestone.domain.LedgerEntryType;
import com.chris64233.escrowmilestone.domain.MilestoneStatus;
import com.chris64233.escrowmilestone.service.SnapshotReader.MilestoneSnapshot;
import com.chris64233.escrowmilestone.support.DatabaseCleaner;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Lazy;

/**
 * 并发安全测试：
 * 1) 多个里程碑并发放款：严格按顺序成功、不超过托管余额、同业务号只放一笔；
 * 2) 审批撤回与最终放款并发：只能形成“完整放款”或“完整拒绝”一种结果。
 */
@SpringBootTest
class EscrowConcurrencyTest {

    @Autowired
    private EscrowService service;
    @Autowired
    private SnapshotReader snapshots;
    @Autowired
    @Lazy
    private ConcurrencyRunner runner;
    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void clean() {
        cleaner.clean();
    }

    private final AtomicInteger counter = new AtomicInteger();

    private String newProject(int milestones) {
        String code = "P-C" + counter.incrementAndGet();
        BigDecimal total = new BigDecimal(milestones * 100 + ".00");
        List<Commands.CreateMilestone> specs = new java.util.ArrayList<>();
        for (int i = 1; i <= milestones; i++) {
            specs.add(new Commands.CreateMilestone("M" + i, new BigDecimal("100.00"),
                    List.of("验收报告"), List.of("PM")));
        }
        service.createProject(new Commands.CreateProject(code, "并发项目", total, "ops", specs));
        for (int i = 1; i <= milestones; i++) {
            service.submitEvidence(code, new Commands.SubmitEvidence(
                    i, "验收报告", "ref-" + i, "证据", "qa"));
            service.approve(code, new Commands.ApprovalDecisionCommand(i, "PM", "同意", "pm"));
        }
        return code;
    }

    private record Result(boolean success, String businessNo, int sequence, Throwable error) {
    }

    @Test
    void concurrentDisbursementsRespectOrderAndBalance() throws Exception {
        int n = 5;
        String code = newProject(n);

        int threads = n * 3; // 每个里程碑被 3 个不同业务号同时抢放
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();

        for (int attempt = 0; attempt < threads; attempt++) {
            int seq = attempt % n + 1;
            String businessNo = code + "-D" + seq + "-" + attempt;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    // 前置里程碑未完成时短暂等待后重试（模拟客户端有序重试）；
                    // 其他业务失败（如里程碑已被别的业务号抢先放款）为终态。
                    for (int i = 0; ; i++) {
                        try {
                            runner.disburseInNewTx(service, code, new Commands.Disburse(
                                    businessNo, seq, new BigDecimal("100.00"), "并发放款", "teller"));
                            results.add(new Result(true, businessNo, seq, null));
                            return;
                        } catch (Throwable t) {
                            boolean blockedByPrerequisite = t.getMessage() != null
                                    && t.getMessage().contains("前置里程碑");
                            if (!blockedByPrerequisite || i >= 100) {
                                results.add(new Result(false, businessNo, seq, t));
                                return;
                            }
                            Thread.sleep(20);
                        }
                    }
                } catch (Throwable t) {
                    results.add(new Result(false, businessNo, seq, t));
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 成功放款恰好是里程碑 1..n，且每个里程碑只有一笔
        List<Result> successes = results.stream().filter(Result::success).toList();
        assertThat(successes).hasSize(n);
        assertThat(successes).extracting(Result::sequence)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5);

        // 余额与台账核对：累计放款 == 托管额，余额为 0，绝不超额
        EscrowProject project = snapshots.project(code);
        assertThat(project.getRemainingAmount()).isEqualByComparingTo("0.00");
        List<LedgerEntry> disbursementEntries = snapshots.ledger(code).stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DISBURSEMENT)
                .toList();
        assertThat(disbursementEntries).hasSize(n);
        BigDecimal sum = disbursementEntries.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(project.getTotalAmount());

        // 所有里程碑最终均为已放款
        assertThat(snapshots.milestoneSnapshots(code))
                .extracting(MilestoneSnapshot::status)
                .allSatisfy(status -> assertThat(status).isEqualTo(MilestoneStatus.DISBURSED));
    }

    @Test
    void concurrentSameBusinessNoDisbursesExactlyOnce() throws Exception {
        String code = newProject(1);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    // 相同业务号 + 相同内容
                    runner.disburseInNewTx(service, code, new Commands.Disburse(
                            code + "-ONCE", 1, new BigDecimal("100.00"), "幂等放款", "teller"));
                    results.add(new Result(true, code + "-ONCE", 1, null));
                } catch (Throwable t) {
                    results.add(new Result(false, code + "-ONCE", 1, t));
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 全部返回成功（首笔创建 + 后续返回首次结果），但只有一条放款台账、余额只扣一次
        assertThat(results).hasSize(threads);
        assertThat(results).allSatisfy(r -> assertThat(r.success()).isTrue());
        long disbursementCount = snapshots.ledger(code).stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DISBURSEMENT)
                .count();
        assertThat(disbursementCount).isEqualTo(1);
        assertThat(snapshots.project(code).getRemainingAmount())
                .isEqualByComparingTo("0.00");
    }

    @RepeatedTest(12)
    void withdrawCompetingWithDisburseProducesExactlyOneCompleteOutcome() throws Exception {
        String code = newProject(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        pool.submit(() -> {
            ready.countDown();
            await(start);
            try {
                runner.withdrawInNewTx(service, code,
                        new Commands.ApprovalDecisionCommand(1, "PM", "放款前撤回", "pm"));
            } catch (Throwable ignored) {
                // 放款已提交时撤回会失败，属于两种完整结果之一
            }
        });
        pool.submit(() -> {
            ready.countDown();
            await(start);
            try {
                runner.disburseInNewTx(service, code, new Commands.Disburse(
                        code + "-RACE", 1, new BigDecimal("100.00"), "竞态放款", "teller"));
            } catch (Throwable ignored) {
                // 审批先撤回时放款会失败
            }
        });
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 只有一种完整结果：
        // 结果 A：放款台账 1 条、余额 0、里程碑 DISBURSED、审批仍 APPROVED（撤回必然失败）
        // 结果 B：无放款台账、余额 100、里程碑 PENDING、审批为 PENDING（放款必然失败）
        long disbursementCount = snapshots.ledger(code).stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DISBURSEMENT)
                .count();
        MilestoneSnapshot m = snapshots.milestoneSnapshots(code).get(0);
        BigDecimal remaining = snapshots.project(code).getRemainingAmount();

        if (disbursementCount == 1) {
            assertThat(remaining).isEqualByComparingTo("0.00");
            assertThat(m.status()).isEqualTo(MilestoneStatus.DISBURSED);
            assertThat(m.pmApproval()).isEqualTo(ApprovalStatus.APPROVED);
        } else {
            assertThat(disbursementCount).isZero();
            assertThat(remaining).isEqualByComparingTo("100.00");
            assertThat(m.status()).isEqualTo(MilestoneStatus.PENDING);
            assertThat(m.pmApproval()).isEqualTo(ApprovalStatus.PENDING);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
