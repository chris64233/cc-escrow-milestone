package com.chris64233.escrowmilestone.service;

/**
 * 幂等冲突：同一业务号携带了与首次请求不同的内容。
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
