package com.chris64233.escrowmilestone.service;

import com.chris64233.escrowmilestone.domain.Disbursement;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 并发测试辅助：让每个线程的操作在独立事务中提交/回滚，且经过 Spring 事务代理。 */
@Component
public class ConcurrencyRunner {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Disbursement disburseInNewTx(EscrowService service, String code, Commands.Disburse cmd) {
        return service.disburse(code, cmd);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void withdrawInNewTx(EscrowService service, String code,
                                Commands.ApprovalDecisionCommand cmd) {
        service.withdrawApproval(code, cmd);
    }
}
