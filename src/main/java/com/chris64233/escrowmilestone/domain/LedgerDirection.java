package com.chris64233.escrowmilestone.domain;

/** 台账记账方向。 */
public enum LedgerDirection {
    /** 借方：资金流出（放款、补偿）。 */
    DEBIT,
    /** 贷方：资金流入（撤销退回）。 */
    CREDIT
}
