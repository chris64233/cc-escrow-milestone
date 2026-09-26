package com.chris64233.escrowmilestone.service;

/** 业务规则不满足（前置里程碑未完成、证据/审批不齐全、非法状态迁移等）。 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
