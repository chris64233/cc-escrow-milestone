package com.chris64233.escrowmilestone.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 测试间数据清理：内存库在同一 Spring 上下文内被多个测试方法复用。 */
@Component
public class DatabaseCleaner {

    private static final String[] TABLES_IN_DELETE_ORDER = {
            "ledger_entry",
            "idempotent_request",
            "approval_decision",
            "disbursement",
            "approval_requirement",
            "required_evidence",
            "milestone",
            "escrow_project"
    };

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES_IN_DELETE_ORDER) {
                jdbcTemplate.update("DELETE FROM " + table);
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
