# cc-escrow-milestone

项目托管资金与里程碑管理服务：托管项目按里程碑验收结果分期放款，提供证据提交、角色审批、
幂等放款、撤销/结算/补偿和资金台账查询能力。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring MVC + Spring Data JPA + H2）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 主要业务规则

### 项目与里程碑

- 托管项目记录**总托管金额**、当前托管余额和若干**有顺序的里程碑**（sequence 为正整数且不重复）。
- 每个里程碑包含**计划金额**、**所需证据类型**和**审批角色门槛**（角色 + 所需审批人数）。
- 创建项目时校验：**全部里程碑计划金额之和必须等于托管金额**，否则拒绝创建（400）。
- 项目初始余额等于托管金额。

### 放款条件

里程碑放款必须同时满足：

1. 该里程碑所需证据类型**全部已提交**；
2. 每个审批角色的**有效审批人数达到门槛**——同一审批人以最新决定为准，
   撤回（REVOKE）后不再计入，重复同意只算一人；
3. 所有序号更小的**前置里程碑均已放款**，不允许越过；
4. 托管余额不小于里程碑计划金额。

放款在事务内**原子扣减托管余额**，同时生成一条**不可修改的资金台账记录**
（类型 RELEASE，含金额、变动后余额、处理人、原因、时间），里程碑状态变为已放款。

### 幂等与并发

- 放款请求必须携带**业务号 bizNo**（全局唯一）。相同业务号且内容（项目 + 里程碑）一致的
  重复提交**返回首次结果**；内容不一致返回 **409 冲突**。
- 放款、撤销、审批决定等改变资金结果的操作都对项目行加**悲观写锁**串行执行：
  - 多个并发放款不会超过托管余额，同一里程碑并发放款只有一笔成功；
  - **审批撤回与最终放款并发时只会形成一种完整结果**——要么放款先完成（撤回记录保留但
    不影响已完成的放款），要么撤回先生效（放款因门槛不满足而失败），不会出现中间态。

### 撤销、结算与补偿

- **未对外结算**的放款可以**整笔撤销**：恢复托管余额、里程碑回到待放款状态（可重新放款），
  并追加一条 REVERSE 台账记录。已撤销的放款不能重复撤销。
- 放款可标记为**已结算**（settle）。**已结算放款不能撤销**，只能**登记补偿记录**
  （COMPENSATE 台账），不删除、不覆盖原流水，也不改变托管余额。
- 所有决定（审批、放款、撤销、结算、补偿）都记录**处理人、原因和时间**。

### 查询

- `GET /api/projects/{id}/balance` — 项目总额、余额、已放款金额
- `GET /api/projects/{id}/milestones` — 各里程碑状态、证据缺口、审批门槛进度
- `GET /api/projects/{id}/milestones/{seq}/evidences|approvals` — 证据与审批记录
- `GET /api/projects/{id}/payouts` — 放款单列表
- `GET /api/projects/{id}/payouts/{payoutId}/compensations` — 补偿记录
- `GET /api/projects/{id}/ledger` — 资金台账（只追加，不可修改）

### 主要写接口

- `POST /api/projects` — 创建托管项目（含里程碑定义）
- `POST /api/projects/{id}/milestones/{seq}/evidences` — 提交证据
- `POST /api/projects/{id}/milestones/{seq}/approvals` — 审批决定（APPROVE / REVOKE）
- `POST /api/projects/{id}/milestones/{seq}/payouts` — 放款（携带 bizNo）
- `POST /api/projects/{id}/payouts/{payoutId}/reverse|settle|compensations` — 撤销 / 结算 / 补偿

错误响应统一为 `{"code", "message", "timestamp"}`：参数错误 400、资源不存在 404、
幂等冲突 409、业务规则不满足（证据不全 / 审批不足 / 前置未完成 / 余额不足 / 状态非法）422。

## 代码结构

    src/main/java/com/chris64233/escrowmilestone/
    ├── domain/        实体：EscrowProject、Milestone、Evidence、Approval、Payout、
    │                  FundRecord、CompensationRecord 及枚举
    ├── repository/    Spring Data JPA 仓储（项目行悲观写锁）
    ├── service/       EscrowService 核心业务与查询视图、BusinessException
    └── web/           REST 控制器、请求 DTO、全局异常处理

测试：`EscrowServiceTest`（业务规则、幂等、撤销/结算/补偿、并发放款、撤回与放款并发）、
`EscrowControllerTest`（REST 全流程与错误码）。
