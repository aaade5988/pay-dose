# pay-dose 无用代码清理记录

> 执行日期：2026-10-28
> 验证结果：安卓端编译通过 + 单元测试通过；后端编译通过 + 73 项测试全绿

## 一、清理总量

| 指标 | 数值 |
|---|---|
| 净减少行数 | **413 行**（删除 537 / 新增 124，新增含格式化重排） |
| 删除文件 | 2 个（`SettingsStore.kt`、`Previews.kt`） |
| 清理未使用导入 | 16 处 |
| 删除未调用函数 | 13 个 |
| 删除未使用 DTO | 11 个 |

## 二、验证命令与结果

```
# 安卓端
./gradlew :app:compileDebugKotlin --offline   → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --offline    → BUILD SUCCESSFUL

# 后端
mvn -o compile                                 → 退出码 0
mvn -o test                                    → Tests run: 73, Failures: 0, Errors: 0
```

## 三、逐项清理明细

### 3.1 core 层（领域逻辑）

| 文件 | 删除内容 |
|---|---|
| `core/Txns.kt` | `MonthSummary` 数据类、`monthKeyOf()`、`currentMonthKey()`、`summarizeMonth()`、`import java.time.LocalDate` |
| `core/Methods.kt` | `METHOD_TYPE_LABELS`、`dueSummary()` |
| `core/Bills.kt` | `BillMonth.totalCount` |
| `core/Money.kt` | `addAmount()`、`sumAmounts()` |

> **连带删除说明**：`MonthSummary` 仅被 `summarizeMonth()` 使用，`monthKeyOf()` / `currentMonthKey()` 仅被 `summarizeMonth()` 使用，属级联孤儿，一并清理。月份聚合实际走 `Bills.groupByMonth`。

### 3.2 UI 层

| 文件 | 删除内容 |
|---|---|
| `ui/components/Common.kt` | `OutlineButton()` 组件、`FormFieldRow()` 组件、`RowScope` / `fillMaxSize` 导入 |
| `ui/theme/Color.kt` | `PayColors.Slot` 常量 |
| `ui/components/Previews.kt` | **整个文件删除**（仅 2 个 `@Preview`，且文件编码已损坏中文乱码） |

### 3.3 data 层

| 文件 | 删除内容 |
|---|---|
| `data/Dto.kt` | `SettleRequest`、`MethodsResponse`、`MethodResponse`、`OkResponse`、`HealthResponse`、`RecommendResponse`、`TxnCreateResponse`、`TxnsResponse`、`TxnDeleteResponse`、`CycleResponse`、`ErrorResponse`（11 个旧 REST 包装壳） |
| `data/SettingsStore.kt` | **整个文件删除**（`baseUrl` 无任何读取点，无设置页） |
| `data/PlannerRepository.kt` | `health()` 方法、`AppContainer.settings` 字段；顺带修正注释中已过时的 Retrofit 描述 |

### 3.4 未使用导入（16 处）

| 文件 | 删除的导入 |
|---|---|
| `ui/PayDoseRoot.kt` | `background`、`Box`、`fillMaxSize`、`MaterialTheme` |
| `ui/calc/CalcScreen.kt` | `Spacer`、`height`、`width` |
| `ui/dashboard/DashboardScreen.kt` | `HintText` |
| `ui/components/Common.kt` | `RowScope`、`fillMaxSize` |
| `core/Txns.kt` | `java.time.LocalDate` |
| `backend/.../PayDoseApplication.kt` | `Files`、`Path`、`Paths` |
| `backend/.../tools/Migrator.kt` | `Path`、`DriverManager` |

> `getValue` / `setValue` 属 Kotlin 委托属性的隐式使用，已核实各文件确实存在 `by` 委托，**保留不动**。

## 四、未执行的清理项（需另行决策）

以下项本轮**未动**，详见 `docs/dead-code-audit.md`：

1. `backend/target/`（131.3 MB）、`android-app/.gradle/`（3.9 MB）、`.idea/`、`data/backup/` 等构建产物与备份
2. `AndroidManifest.xml` 的 `INTERNET` 权限与 `usesCleartextTraffic="true"`
3. `res/values/colors.xml` 的 `<color name="primary">`
4. `app/proguard-rules.pro`（未启用 minify，规则不生效）
5. `backend/` 模块是否归档、`scripts/inspect_source_db.py` 与 `scripts/run.py` 去留

## 五、安全边界确认

- **未触碰** `android-app/keystore/pay-dose.jks` 与 `keystore.properties` —— 换签名会导致无法覆盖安装、用户本地数据丢失
- 删除项均为 0 引用，编译与测试双重验证通过，无功能性回退风险
