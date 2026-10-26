# 开发进度

> 最后更新：后端已完成并通过全部验收；Android 客户端由 Codex CLI 实施中。

## 已完成：后端（backend/）

技术栈：Kotlin 1.9.25 + Spring Boot 3.3.5 + Spring Data JPA + SQLite

### 验收状态

| 层级 | 测试 | 数量 | 结果 |
|---|---|---|---|
| 算法单测 | `CalculatorTest` | 19 | ✅ 全过 |
| 接口集成 | `PlannerApiTest` | 39 | ✅ 全过 |
| 迁移工具 | `MigratorTest` | 5 | ✅ 全过 |
| 迁移后兼容 | `MigratedDataCompatTest` | 1 | ✅ 全过 |
| **JUnit 合计** | | **64** | ✅ |
| 端到端冒烟 | `scripts/e2e_smoke.py` | 42 断言 | ✅ 全过 |

### 关键设计

**算法**（`domain/Calculator.kt`，174 行纯函数，无框架依赖）
- 还款日推算：非信用卡即时扣款 → 找下一个账单日 → 固定还款日 or 账单日后 N 天
- 月末钳制：31 日遇 2 月取 28/29 日
- `statement_day_inclusive` 开关：决定账单日当天消费算本期还是下期
- 过滤已用+本次超额度的方式，按占用天数降序取 Top-N（稳定排序，无次级排序）

**金额精度**
- 全程 `BigDecimal` + `RoundingMode.HALF_EVEN`（银行家舍入）
- 注意 `10.125 → 10.12` 而非 `10.13`，有测试锁定
- 严禁 Double/Float（已用脚本验证零命中）

**流水与余额原子性**
- `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` 保证「记账 + 改余额」同事务
- 删除流水反向回滚（删消费 → 占用回降；删还款 → 占用回升）
- 消费允许超限；还款不能超当前已用
- 卡有流水时不可删卡（409）

**契约**（沿用原 FastAPI 设计，前端零改动可对接）
- 路径、字段名（snake_case）、中文枚举、金额字符串全部保持一致
- 400/404/409/422 分级，中文 detail
- 422 附带 pydantic 同形 `errors` 明细
- `StrictStringDeserializer` 拒绝数字入字符串字段（Jackson 默认会宽容转换，破坏契约）

**迁移工具**（`tools/Migrator.kt` + `tools/Main.kt`）
- 源库支持 SQLite / MySQL，只读源库（仅 SELECT）
- 保留原 id；支持 `--dry-run` 预演与 `--truncate` 清空
- 三层对账：逐字段比对 + 行数校验 + 余额一致性
- 通过输出 `RECONCILE OK`，失败退出码 1
- 金额以 TEXT 存储，规避 SQLite NUMERIC 亲和性丢小数位

### 踩过的坑（已修复并有测试保护）

1. **`@RequestBody` 缺 `@Valid`** → `@Min/@Max/@Size` 全部失效，`top_n=200` 错误返回 200
2. **Jackson 宽容转换** → 金额 `100`（数字）被静默转成 `"100"`，违背 pydantic 的 422
3. **`@RestController` 误用于异常处理器** → 只注册路由不拦截异常，业务异常未被映射为 HTTP 状态码
4. **SQLite 不自动创建父目录** → 驱动直接抛错，需在环境准备阶段建目录（`spring.factories` 注册监听器）
5. **SQLite NUMERIC 亲和性** → `'50000.00'` 存成 `50000`，迁移时改用 TEXT
6. **测试上下文共享** → 两个测试类共用同一 SQLite 文件互相污染，用独立 datasource 覆盖解决

## 进行中：Android 客户端（android-app/）

由 Codex CLI 按 `docs/android-spec.md` 实施，进度：

- [x] M1 工程骨架（BUILD SUCCESSFUL，产出 APK）
- [x] M2 数据层（DTO / Retrofit / Repository / AppContainer）
- [x] M3 计算 Tab（金额输入 + 日期 + 推荐结果 + Top1 大卡 + 迷你环）
- [x] M4 卡片 Tab + 编辑页
- [x] M5 看板 Tab（聚合仪表盘 + 单卡环 + 更新已用弹层）
- [ ] M6 流水页 + 记账弹层 + 我的页

技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.09.03 / minSdk 26

## 待办

- [ ] Android 最终 APK 验收
- [ ] 端到端联调（模拟器 + 后端）
- [ ] 更新根 README 的 Android 使用说明
- [ ] 截图归档

## 快速命令

```powershell
# 后端：测试
cd E:\opc\aaade\pay-dose\backend
$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"
mvn -B test

# 后端：构建并启动
mvn -B -DskipTests package
java -jar target/pay-dose.jar

# 后端：端到端冒烟（需服务已启动）
python scripts/e2e_smoke.py

# Android：构建
cd E:\opc\aaade\pay-dose\android-app
.\gradlew.bat assembleDebug
```
