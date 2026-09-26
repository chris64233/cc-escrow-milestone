package com.chris64233.escrowmilestone.domain;

/**
 * 里程碑放款状态。
 *
 * <p>PENDING：未放款（初始状态；放款被整笔撤销后也回到此状态，允许重新放款）。
 * DISBURSED：已形成放款并扣减托管余额。
 */
public enum MilestoneStatus {
    PENDING,
    DISBURSED
}
