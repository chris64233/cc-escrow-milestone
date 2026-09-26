# cc-escrow-milestone

项目托管资金与里程碑管理服务：托管项目记录总托管金额与多个有顺序的里程碑，
按里程碑验收结果分期放款，提供证据/审批门槛、幂等放款、撤销与结算后补偿、
以及项目余额、里程碑状态、审批证据和资金台账查询。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1
- Spring Data JPA + H2（默认内存库，可替换为其他关系库）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

- `EscrowProject` 托管项目：`code`、总托管金额 `totalAmount`、剩余可放款余额
  `remainingAmount`、创建人/时间；下挂有序里程碑。
- `Milestone` 里程碑：顺序号 `sequenceNo`（从 1 开始）、计划金额 `plannedAmount`、
  所需证据清单 `RequiredEvidence`、所需角色审批门槛 `ApprovalRequirement`、状态
  `PENDING / DISBURSED`。
- `ApprovalDecision` 审批决定流水：只追加，记录每次通过/撤回的处理人、原因、时间。
- `Disbursement` 放款资金记录：放款业务号 `businessNo`（全局唯一、幂等键）、金额、
  里程碑、处理人、原因、放款时间；状态 `DISBURSED / REVOKED / SETTLED`，
  核心要素创建后不可修改。
- `LedgerEntry` 资金台账：只追加、不可修改、不可删除。类型
  `DISBURSEMENT`（借方出账）、`REVERSAL`（撤销贷方退回）、
  `COMPENSATION`（已结算后的补偿登记，不动托管余额），每条均带处理人、原因、时间。
- `IdempotentRequest` 幂等记录：业务号 + 请求内容指纹（SHA-256）。

## 主要业务规则

1. **金额一致性**：创建项目时，全部里程碑计划金额之和必须等于总托管金额，
   否则拒绝创建（409）。
2. **放款门槛**：里程碑只有在①所需证据全部提交齐全、②所需角色审批全部通过、
   ③所有序号更小的前置里程碑均已放款时，才能放款。放款金额必须等于该里程碑计划金额。
3. **证据与审批**：证据提交后不可修改；审批可通过、可撤回（撤回有决定流水）。
   里程碑一旦放款，其审批状态不可再变更。
4. **原子放款**：放款在同一事务内完成“校验 → 扣减托管余额 → 里程碑置为已放款 →
   写入不可修改的放款记录 → 追加台账借方”。扣减余额前先取托管项目行悲观写锁，
   并以实体 `@Version` 作为第二道防线；多个里程碑并发放款总额不可能超过托管余额。
5. **审批撤回与最终放款并发**：同一项目的所有写操作都经项目行悲观锁串行化，
   撤回与放款竞争时只会形成一种完整结果——要么撤回先生效、放款被完整拒绝
   （余额不动、无台账），要么放款先生效、撤回被完整拒绝（余额已扣、台账已生成），
   不会出现半成品状态。
6. **放款幂等与冲突**：
   - 相同业务号、相同内容重复提交：返回首次放款结果，不重复扣款、不重复记账；
   - 相同业务号但内容变化（项目/里程碑/金额/原因/处理人任一不同）：返回冲突（409）；
   - 业务号全局唯一，不允许跨项目复用。
7. **顺序约束**：不允许越过尚未完成的前置里程碑放款；后置里程碑已放款后，
   前置里程碑放款不可撤销（已放款里程碑始终构成连续前缀）。
8. **撤销**：尚未对外结算的放款可整笔撤销——原放款标记为 `REVOKED` 但保留不删，
   追加一条 `REVERSAL` 贷方台账，托管余额恢复，里程碑回到待放款，
   之后可在重新满足证据/审批门槛后再次放款。撤销不可重复。
9. **结算与补偿**：放款登记对外结算后状态为 `SETTLED`，不可撤销、不可删除或覆盖原流水，
   只能登记补偿记录（`COMPENSATION`，独立只追加台账，业务号同样幂等），
   补偿不变动托管余额。
10. **审计**：证据提交、审批通过/撤回、放款、撤销、结算、补偿均记录处理人、原因和时间。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/projects` | 创建托管项目（含有序里程碑、证据、审批角色） |
| GET | `/api/projects/{code}` | 项目余额查询（总额/剩余额） |
| GET | `/api/projects/{code}/milestones` | 里程碑状态、证据与审批门槛查询 |
| GET | `/api/projects/{code}/disbursements` | 项目放款流水查询 |
| GET | `/api/projects/{code}/ledger` | 资金台账查询（放款/撤销/补偿，按时间顺序） |
| POST | `/api/projects/{code}/evidence` | 提交里程碑证据 |
| POST | `/api/projects/{code}/approvals` | 角色审批通过 |
| POST | `/api/projects/{code}/approvals/withdrawals` | 撤回角色审批 |
| POST | `/api/projects/{code}/disbursements` | 里程碑放款（业务号幂等） |
| POST | `/api/projects/disbursements/revocations` | 撤销未结算放款（恢复余额） |
| POST | `/api/projects/disbursements/settlements` | 登记放款对外结算 |
| POST | `/api/projects/disbursements/compensations` | 对已结算放款登记补偿（业务号幂等） |

### 示例：创建项目

    POST /api/projects
    {
      "code": "P-001",
      "name": "示例托管项目",
      "totalAmount": 1000.00,
      "createdBy": "ops",
      "milestones": [
        {"name": "一期验收", "plannedAmount": 400.00,
         "evidenceTypes": ["验收报告"], "approvalRoles": ["PM", "FINANCE"]},
        {"name": "二期验收", "plannedAmount": 600.00,
         "evidenceTypes": ["验收报告", "发票"], "approvalRoles": ["PM"]}
      ]
    }

### 示例：里程碑放款

    POST /api/projects/P-001/disbursements
    {
      "businessNo": "DISB-20260927-0001",
      "milestoneSequence": 1,
      "amount": 400.00,
      "reason": "一期验收通过，按约放款",
      "handledBy": "teller-01"
    }

错误响应：业务规则不满足、幂等冲突、资源冲突返回 `409`；资源不存在返回 `404`；
请求参数校验失败返回 `400`，响应体含 `message` 与字段级错误信息。

## 并发与一致性实现说明

- 所有写操作在事务内先执行 `findByCodeForUpdate`（`PESSIMISTIC_WRITE`）锁定项目行，
  同一项目上的放款/撤销/结算/补偿/审批变更严格串行化。
- 放款的全部前置条件校验与余额扣减发生在同一持锁事务中，配合实体乐观版本号，
  杜绝超余额放款与竞态半成品。
- 资金记录与台账均为只追加模型：撤销只新增贷方台账并迁移放款状态，
  补偿只新增独立台账，任何路径都不会更新或删除既有流水。

## 测试

`./mvnw clean test` 覆盖：

- 金额之和不等于托管金额拒绝创建；
- 证据不齐/审批不全/前置里程碑未完成/金额不符拒绝放款；
- 证据不可修改、放款后审批不可变更；
- 放款幂等：相同内容返回首次结果、内容变化冲突；
- 未结算放款整笔撤销恢复余额、台账追加、撤销后可重新放款；
- 已结算放款不可撤销、只能补偿，且补偿幂等、不动余额；
- 并发放款不超额、严格按里程碑顺序、同业务号恰好放款一次；
- 审批撤回与最终放款高并发重复 12 轮，断言每轮只形成一种完整结果；
- REST 层状态码、幂等与冲突语义、查询结果的端到端校验。
