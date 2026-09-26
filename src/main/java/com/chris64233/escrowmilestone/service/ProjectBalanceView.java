package com.chris64233.escrowmilestone.service;

import java.math.BigDecimal;

/**
 * 项目余额视图。
 */
public record ProjectBalanceView(
        Long projectId,
        String name,
        BigDecimal totalAmount,
        BigDecimal balance,
        BigDecimal releasedAmount) {
}
