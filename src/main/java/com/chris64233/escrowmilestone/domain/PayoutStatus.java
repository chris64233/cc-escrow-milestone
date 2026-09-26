package com.chris64233.escrowmilestone.domain;

public enum PayoutStatus {
    /** 已放款，尚未对外结算 */
    RELEASED,
    /** 已对外结算 */
    SETTLED,
    /** 已整笔撤销 */
    REVERSED
}
