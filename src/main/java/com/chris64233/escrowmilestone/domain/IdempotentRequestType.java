package com.chris64233.escrowmilestone.domain;

/** 需要登记幂等指纹的业务请求类型。 */
public enum IdempotentRequestType {
    DISBURSEMENT,
    COMPENSATION
}
