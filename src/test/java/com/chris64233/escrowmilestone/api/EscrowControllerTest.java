package com.chris64233.escrowmilestone.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chris64233.escrowmilestone.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** REST 接口端到端测试（含状态码与幂等/冲突语义）。 */
@SpringBootTest
@AutoConfigureMockMvc
class EscrowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void clean() {
        cleaner.clean();
    }

    private static final String PROJECT = """
            {
              "code": "P-WEB",
              "name": "接口测试项目",
              "totalAmount": 1000,
              "createdBy": "ops",
              "milestones": [
                {"name": "M1", "plannedAmount": 400,
                 "evidenceTypes": ["验收报告"], "approvalRoles": ["PM", "FINANCE"]},
                {"name": "M2", "plannedAmount": 600,
                 "evidenceTypes": ["验收报告"], "approvalRoles": ["PM"]}
              ]
            }
            """;

    @Test
    void fullHttpFlow() throws Exception {
        // 金额之和不等于托管金额 → 409
        String badProject = PROJECT.replace("1000", "1001").replace("P-WEB", "P-BAD");
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content(badProject))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("必须等于托管金额")));

        // 参数校验失败 → 400
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        // 创建项目 → 201
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content(PROJECT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingAmount").value(1000.00));

        // 余额查询
        mockMvc.perform(get("/api/projects/P-WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("P-WEB"))
                .andExpect(jsonPath("$.totalAmount").value(1000.00));

        // 里程碑与证据状态查询
        mockMvc.perform(get("/api/projects/P-WEB/milestones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].evidences[0].provided").value(false))
                .andExpect(jsonPath("$[0].approvals.length()").value(2));

        // 未满足条件直接放款 → 409
        mockMvc.perform(post("/api/projects/P-WEB/disbursements")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"W1","milestoneSequence":1,"amount":400,
                                 "reason":"放款","handledBy":"teller"}
                                """))
                .andExpect(status().isConflict());

        // 提交证据 + 审批
        mockMvc.perform(post("/api/projects/P-WEB/evidence")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"milestoneSequence":1,"evidenceType":"验收报告","evidenceRef":"ref-1",
                                 "reason":"齐全","handledBy":"qa"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/projects/P-WEB/approvals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"milestoneSequence":1,"role":"PM","reason":"同意","handledBy":"pm"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/projects/P-WEB/approvals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"milestoneSequence":1,"role":"FINANCE","reason":"同意","handledBy":"cfo"}
                                """))
                .andExpect(status().isNoContent());

        // 放款成功 → 200，余额变 600
        mockMvc.perform(post("/api/projects/P-WEB/disbursements")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"W1","milestoneSequence":1,"amount":400,
                                 "reason":"首次放款","handledBy":"teller"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"));
        mockMvc.perform(get("/api/projects/P-WEB"))
                .andExpect(jsonPath("$.remainingAmount").value(600.00));

        // 相同内容重复提交 → 200 返回首次结果；金额变化 → 409 冲突
        mockMvc.perform(post("/api/projects/P-WEB/disbursements")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"W1","milestoneSequence":1,"amount":400,
                                 "reason":"首次放款","handledBy":"teller"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessNo").value("W1"));
        mockMvc.perform(post("/api/projects/P-WEB/disbursements")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"W1","milestoneSequence":1,"amount":401,
                                 "reason":"内容变化","handledBy":"teller"}
                                """))
                .andExpect(status().isConflict());

        // 台账查询：仅一条放款
        mockMvc.perform(get("/api/projects/P-WEB/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entryType").value("DISBURSEMENT"))
                .andExpect(jsonPath("$[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$[0].handledBy").value("teller"));

        // 结算后撤销 → 409；登记补偿 → 200
        mockMvc.perform(post("/api/projects/disbursements/settlements")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"disbursementBusinessNo":"W1","reason":"已对外结算","handledBy":"settler"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
        mockMvc.perform(post("/api/projects/disbursements/revocations")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"disbursementBusinessNo":"W1","reason":"想撤销","handledBy":"teller"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/projects/disbursements/compensations")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WC1","disbursementBusinessNo":"W1","amount":50,
                                 "reason":"差额补偿","handledBy":"fin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryType").value("COMPENSATION"));

        // 补偿幂等：同内容返回首次，改金额冲突
        mockMvc.perform(post("/api/projects/disbursements/compensations")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WC1","disbursementBusinessNo":"W1","amount":50,
                                 "reason":"差额补偿","handledBy":"fin"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/disbursements/compensations")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"WC1","disbursementBusinessNo":"W1","amount":51,
                                 "reason":"变了","handledBy":"fin"}
                                """))
                .andExpect(status().isConflict());

        // 不存在资源 → 404
        mockMvc.perform(get("/api/projects/NO-SUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeFlowRestoresBalance() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content(PROJECT.replace("P-WEB", "P-REV")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/P-REV/evidence")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"milestoneSequence":1,"evidenceType":"验收报告","evidenceRef":"r",
                                 "reason":"齐全","handledBy":"qa"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/projects/P-REV/approvals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"milestoneSequence":1,"role":"PM","reason":"ok","handledBy":"pm"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/projects/P-REV/approvals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"milestoneSequence":1,"role":"FINANCE","reason":"ok","handledBy":"cfo"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/projects/P-REV/disbursements")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo":"R1","milestoneSequence":1,"amount":400,
                                 "reason":"放款","handledBy":"teller"}
                                """))
                .andExpect(status().isOk());

        // 未结算可整笔撤销 → 余额恢复
        mockMvc.perform(post("/api/projects/disbursements/revocations")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"disbursementBusinessNo":"R1","reason":"验收异议","handledBy":"manager"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        mockMvc.perform(get("/api/projects/P-REV"))
                .andExpect(jsonPath("$.remainingAmount").value(1000.00));
        mockMvc.perform(get("/api/projects/P-REV/milestones"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}
