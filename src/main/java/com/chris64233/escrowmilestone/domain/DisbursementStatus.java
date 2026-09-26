package com.chris64233.escrowmilestone.domain;

/**
 * 放款生命周期状态。
 *
 * <p>DISBURSED：已放款、尚未对外结算（可整笔撤销）。
 * REVOKED：未结算放款已整笔撤销，余额恢复。
 * SETTLED：已对外结算，不可撤销/删除/覆盖，只能登记补偿。
 */
public enum DisbursementStatus {
    DISBURSED,
    REVOKED,
    SETTLED
}
