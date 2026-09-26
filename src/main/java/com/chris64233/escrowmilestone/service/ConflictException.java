package com.chris64233.escrowmilestone.service;

/** 创建时唯一约束冲突（如项目编码、业务号已被其他类型请求占用）。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
