# pay-dose 死代码 / 冗余资产审计报告

> 审计范围：`E:\opc\aaade\pay-dose`（git 跟踪的 76 个文件 + 工作区未跟踪产物）
> 审计方式：静态引用计数（全仓库含测试）+ 逐项人工复核调用点
> 审计日期：2026-10-28

## 一、总体结论

| 分类 | 数量 | 可安全删除 | 需确认 | 明确保留 |
|---|---|---|---|---|
| 未调用的 Kotlin 函数/组件 | 8 | 8 | 0 | 0 |
| 未使用的 DTO 数据类 | 11 | 11 | 0 | 0 |
| 未使用的常量 / 字段 | 4 | 4 | 0 | 0 |
| 整文件冗余（UI/配置） | 2 | 2 | 0 | 0 |
| 未使用的 import 语句 | 16 | 16 | 0 | 0 |
| 废弃的构建/运行产物与备份 | 4 类 | 4 类 | 0 | 0 |
| 已归档的 backend 模块 | 1 个目录 | — | 决策项 | 视需求 |

可安全删除的代码规模约 **420 行**（不含 backend 归档与构建产物）。

---

## 二、确认无任何引用的代码（可安全删除）

### 2.1 未调用的函数与 Composable

| 位置 | 符号 | 结论 |
|---|---|---|
| `ui/components/Common.kt:241` | `OutlineButton` | 全库（含测试）0 处调用 |
| `ui/components/Common.kt:299` | `FormFieldRow` | 全库 0 处调用，且依赖 `getValue`/`setValue` 的旧表单写法 |
| `core/Money.kt:135` | `Money.addAmount` | 仅定义处出现，`subAmount` 才是实际在用 |
| `core/Money.kt:141` | `Money.sumAmounts` | 仅定义处出现 |
| `core/Txns.kt:58` | `Txns.summarizeMonth` | 仅定义处出现，月份汇总实际走 `Bills.groupByMonth` |
| `core/Bills.kt:51` | `Bills.totalCount` | 仅定义处出现 |
| `core/Methods.kt:123` | `Methods.dueSummary` | 仅定义处出现 |
| `core/Methods.kt:76` | `Methods.METHOD_TYPE_LABELS` | 仅定义处出现，卡类型文案改由 `Calculator` 常量拼装 |

### 2.2 未使用的 DTO 数据类（`data/Dto.kt`）

这些 DTO 是旧 Retrofit 版 REST 契约的**响应包装壳**。改造为本地 Room 后，`PlannerRepository` 直接返回裸对象（`List<MethodDto>`、`CycleSummaryDto`、`String`），包装层再无人构造：

| 行号 | 符号 | 说明 |
|---|---|---|
| 47 | `SettleRequest` | `setMethodSettled(id, settled)` 直接传布尔，不走请求体 |
| 50 | `MethodsResponse` | 返回 `List<MethodDto>` |
| 53 | `MethodResponse` | 返回 `MethodDto` |
| 56 | `OkResponse` | 已无统一 `{ok:true}` 响应 |
| 60 | `HealthResponse` | `health()` 已固定返回 `"ok"` 字符串 |
| 85 | `RecommendResponse` | 返回 `List<RecItem>` |
| 110 | `TxnCreateResponse` | 返回 `TxnDto` |
| 117 | `TxnDeleteResponse` | 返回删除后已用金额 `String` |
| 113 | `TxnsResponse` | 返回 `List<TxnDto>` |
| 164 | `CycleResponse` | 返回 `CycleSummaryDto` |
| 168 | `ErrorResponse` | 错误统一走 `ApiError` 异常，不再解析 `{detail}` |

**保留项**：`MethodInput`、`RecommendRequest`、`TxnInput`、`MethodDto`、`RecItem`、`TxnDto`、`CycleCardDto`、`CycleSummaryDto` 都在 `LocalPlannerService` 与界面上真实使用。

### 2.3 未使用的常量与字段

| 位置 | 符号 | 说明 |
|---|---|---|
| `ui/theme/Color.kt:39` | `PayColors.Slot` | 全库 0 处引用（`Border`、`RingNoneTrack` 有用，需保留） |
| `data/SettingsStore.kt:13` | `var baseUrl` 及 `DEFAULT_BASE_URL` | 无设置页，无任何读取点；本地 Room 后不再需要服务端地址 |
| `data/PlannerRepository.kt:61` | `AppContainer.settings` | 仅声明，无外部访问 |
| `data/PlannerRepository.kt:23` | `health()` | 无调用点（旧联网健康检查残留） |

---

## 三、整文件级冗余

| 文件 | 判定 | 依据 |
|---|---|---|
| `ui/components/Previews.kt` | **建议删除** | 仅含 `PayChipPreview`、`GapRingPreview` 两个 `@Preview`，不参与运行；组件已在真实页面渲染，预览价值低，且文件编码已损坏（中文乱码） |
| `data/SettingsStore.kt` | **建议删除** | 配合 §2.3，删掉 `baseUrl` 后该类只剩空壳 |
| `app/proguard-rules.pro` | 可删 | 仅 2 行注释；`build.gradle.kts` 未启用 `minifyEnabled`，规则不生效 |

---

## 四、未使用的 import（16 处，编译期警告来源）

| 文件 | 未使用符号 |
|---|---|
| `core/Txns.kt` | `java.time.LocalDate` |
| `ui/PayDoseRoot.kt` | `background`、`Box`、`fillMaxSize`、`MaterialTheme`、`getValue` |
| `ui/bills/BillsScreen.kt` | `getValue` |
| `ui/calc/CalcScreen.kt` | `Spacer`、`height`、`width` |
| `ui/dashboard/DashboardScreen.kt` | `HintText` |
| `ui/components/Common.kt` | `fillMaxSize`（随 `FormFieldRow` 一并清理） |
| `backend/.../PayDoseApplication.kt` | `Files`、`Path`、`Paths` |
| `backend/.../tools/Migrator.kt` | `Path`、`DriverManager` |

> `getValue`/`setValue` 属 `by delegate` 隐式使用，若该文件确无 `by` 委托则一并删除。

---

## 五、废弃的构建产物与备份文件（磁盘占用约 135 MB）

| 路径 | 体积 | 说明 |
|---|---|---|
| `backend/target/` | **131.3 MB / 158 文件** | Maven 构建产物，已在 `.gitignore`，可随时 `mvn clean` 再生 |
| `android-app/.gradle/` | 3.9 MB | Gradle 缓存，可删 |
| `.idea/` | 0.1 MB | IDE 缓存，可删 |
| `data/backup/*.json`（3 个） | 18 KB | 一次性导出备份，`data/` 已整体不入库 |
| `data/pay-dose.db.bak-20261028-021112` | 12 KB | 手工备份，与主库同版本冗余 |
| `target/`（根目录） | 0 | 空目录，无实际内容 |

**⚠️ 不可删除**：`android-app/keystore/pay-dose.jks` + `keystore.properties` —— 换签名会导致无法覆盖安装、用户本地数据丢失。

---

## 六、需要决策项（不建议直接删）

### 6.1 `backend/` 整个模块（约 6000 行）

README 第 142/147 行已明确：**旧版 Spring Boot 后端，仅存档，不参与构建**。但仍有实际价值：

- `tools/Migrator.kt`（451 行）+ `tools/Main.kt`（167 行）：数据迁移工具，`mvn exec:java` 调用，用于把原 `credit-payment-planner` 的 MySQL/SQLite 数据导入
- `domain/Calculator.kt`（211 行）：与 `android-app/core/Calculator.kt` 是**双份实现**，可作为 Android 端的口径对照基准
- `CalculatorTest.kt` / `PlannerApiTest.kt` 等测试：口径回归基线

**建议**：保留，但在 `backend/pom.xml` 注释中标注「归档」，或整体移入 `archive/backend/` 目录，避免被误认为在役代码。

### 6.2 `scripts/inspect_source_db.py`

硬编码绝对路径 `E:\opc\aaade\credit-payment-planner\data\app.db`（当前确实存在），无任何文档或脚本引用。属一次性排查工具。

**建议**：迁入 `backend/scripts/` 或删除。

### 6.3 `scripts/run.py`

启动的是 `backend/target/pay-dose.jar`（旧后端），而 App 已完全本地化。若确认不再需要联调后端，此脚本随之失效。

### 6.4 `docs/progress.md`

内容为「后端已完成、Android 由 Codex CLI 实施中」的历史进度，与当前状态（Android 已完成本地化改造）已脱节。

**建议**：归档或更新，避免误导。

---

## 七、权限与清单中的遗留（可选清理）

| 位置 | 项 | 说明 |
|---|---|---|
| `AndroidManifest.xml:5` | `INTERNET` 权限 | 本地 Room 无需联网，代码中无任何 HTTP 调用。删除可降低安全审计面 |
| `AndroidManifest.xml:15` | `usesCleartextTraffic="true"` | 仅为旧的 `http://10.0.2.2:8000` 开放明文，随 `SettingsStore.baseUrl` 一起失效 |
| `res/values/colors.xml:3` | `<color name="primary">` | 全库 0 处引用（`bg`、`white`、`ic_launcher_background` 在用） |

> `PlannerRepository.kt:14` 的注释里仍提到 Retrofit，属文字残留，可顺手改写。

---

## 八、建议的执行顺序（按风险从低到高）

1. **零风险**：清 16 处未使用 import → 删 8 个未调用函数/组件 → 删 11 个未使用 DTO → 删 `PayColors.Slot`
2. **低风险**：删 `Previews.kt`、`proguard-rules.pro`、`colors.xml` 的 `primary`；清理 `SettingsStore` 与 `AppContainer.settings`、`health()`
3. **磁盘回收**：清 `backend/target/`、`android-app/.gradle/`、`.idea/`、`data/backup/`、`data/*.bak-*`、根 `target/`
4. **需先验证**：移除 `INTERNET` 权限与 `usesCleartextTraffic` —— 建议先跑一次 `assembleDebug` + 真机冒烟
5. **决策后再动**：`backend/` 是否归档、`scripts/inspect_source_db.py` 与 `scripts/run.py` 去留

> 第 1、2 步完成后执行 `./gradlew :app:assembleDebug test` 与 `mvn -f backend/pom.xml test` 验证；预期无编译错误（删除项均无引用）。
