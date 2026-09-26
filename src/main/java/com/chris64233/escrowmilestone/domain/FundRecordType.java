package com.chris64233.escrowmilestone.domain;

public enum FundRecordType {
    /** 放款，扣减托管余额 */
    RELEASE,
    /** 撤销放款，恢复托管余额 */
    REVERSE,
    /** 已结算放款的补偿登记，不影响托管余额 */
    COMPENSATE
}
