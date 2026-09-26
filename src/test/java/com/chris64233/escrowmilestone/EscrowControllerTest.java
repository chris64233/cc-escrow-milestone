package com.chris64233.escrowmilestone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EscrowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String PROJECT_JSON = """
            {
              "name": "网站建设项目",
              "totalAmount": 100.00,
              "milestones": [
                {"sequence": 1, "title": "设计稿", "plannedAmount": 30.00,
                 "requiredEvidenceTypes": ["设计确认单"],
                 "approvalRequirements": [{"role": "PM", "requiredCount": 1}]},
                {"sequence": 2, "title": "开发上线", "plannedAmount": 70.00,
                 "requiredEvidenceTypes": [],
                 "approvalRequirements": []}
            ]
            }
            """;

    @Test
    void fullFlowOverRest() throws Exception {
        String location = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content(PROJECT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(100.00))
                .andReturn().getResponse().getContentAsString();
        Long projectId = Long.valueOf(
                com.jayway.jsonpath.JsonPath.read(location, "$.id").toString());

        mockMvc.perform(get("/api/projects/{id}/balance", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        // 证据与审批未齐全时放款 -> 422
        mockMvc.perform(post("/api/projects/{id}/milestones/1/payouts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizNo\":\"BIZ-1\",\"operator\":\"ops\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVIDENCE_INCOMPLETE"));

        mockMvc.perform(post("/api/projects/{id}/milestones/1/evidences", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"设计确认单\",\"submittedBy\":\"bob\",\"content\":\"已确认\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/{id}/milestones/1/approvals", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PM\",\"approver\":\"alice\",\"decision\":\"APPROVE\",\"reason\":\"ok\"}"))
                .andExpect(status().isCreated());

        // 越过后置里程碑 -> 422
        mockMvc.perform(post("/api/projects/{id}/milestones/2/payouts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizNo\":\"BIZ-2\",\"operator\":\"ops\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRIOR_MILESTONE_NOT_RELEASED"));

        mockMvc.perform(post("/api/projects/{id}/milestones/1/payouts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizNo\":\"BIZ-1\",\"operator\":\"ops\",\"reason\":\"首期\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.amount").value(30.00));

        // 幂等重放 -> 返回首次结果
        mockMvc.perform(post("/api/projects/{id}/milestones/1/payouts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizNo\":\"BIZ-1\",\"operator\":\"ops\",\"reason\":\"首期\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(30.00));

        // 同业务号不同内容 -> 409
        mockMvc.perform(post("/api/projects/{id}/milestones/2/payouts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizNo\":\"BIZ-1\",\"operator\":\"ops\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/projects/{id}/balance", projectId))
                .andExpect(jsonPath("$.balance").value(70.00))
                .andExpect(jsonPath("$.releasedAmount").value(30.00));

        mockMvc.perform(get("/api/projects/{id}/ledger", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("RELEASE"));
    }

    @Test
    void createProjectWithMismatchedSumReturns400() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "坏项目",
                                  "totalAmount": 100.00,
                                  "milestones": [
                                    {"sequence": 1, "plannedAmount": 40.00,
                                     "requiredEvidenceTypes": [], "approvalRequirements": []}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
