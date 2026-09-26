package com.chris64233.escrowmilestone.domain;

/** 资金台账变动类型。台账记录只追加，永不修改、不删除。 */
public enum LedgerEntryType {
    /** 里程碑放款，资金出托管。 */
    DISBURSEMENT,
    /** 未结算放款整笔撤销，资金退回托管。 */
    REVERSAL,
    /** 已结算放款的补偿登记，资金在托管账户外发生，不影响托管余额。 */
    COMPENSATION
}
