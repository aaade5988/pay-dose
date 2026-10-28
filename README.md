# pay-dose

信用卡支付资金规划器 —— 算出「这笔钱哪张卡刷最晚还」，用资金占用天数换最长免息期。

输入一笔大额支付和日期，自动推算各支付方式的预计还款日，按**资金占用天数降序**推荐，尽量晚还钱。

## 架构现状：纯本地 App（Local-first）

> **2026-10 改造**：原先「App + Spring Boot 后端」的 Client-Server 结构已改为**纯本地单进程**。
> 数据存放在手机内 Room 数据库，算法全部在 App 内，**离线可用、出门能用、更新不丢数据**。

```
改造前（两进程，需联网）          改造后（单进程，纯本地）
┌──────────┐    HTTP    ┌──────────┐      ┌─────────────────────┐
│ App      │ ─────────> │ Spring   │      │  pay-dose APK       │
│ (哑终端) │            │ Boot     │      │  ├── ui/   Compose  │
│ 无数据   │            │ 338 MB   │      │  ├── Repository     │
│ 无算法   │            │ 数据在这 │      │  ├── Room ─> SQLite │
└──────────┘            └──────────┘      │  └── core/  算法    │
                                          └─────────────────────┘
```

| 维度 | 改造后 |
|---|---|
| 进程数 | 1（只有 App） |
| 数据位置 | `/data/data/com.dose.pay.android/databases/pay-dose.db` |
| 网络依赖 | 无（`INTERNET` 权限仅保留给将来的更新检查） |
| 升级丢数据风险 | **零**（签名不变 + Room 迁移） |
| 后端内存 | 从 338 MB 降到 ~3 MB |

### 保留的 backend/ 目录

`backend/` **代码完整保留**，但不再打包、不再发版、不再是运行时依赖。它现在的用途：

1. **数据备份源**：改造前的真实数据出处
2. **迁移工具**：`com.dose.pay.tools.Migrator`
3. **行为规格参考**：42 个接口测试 + 19 个算法单测，是 App 侧实现的对照标准

## 技术栈

| 层 | 技术 |
|---|---|
| 客户端 | Kotlin + Jetpack Compose + Material 3（Android） |
| 本地存储 | Room（Android 内置 SQLite 的类型安全封装） |
| 算法 | 纯 Kotlin，`android-app/.../core/Calculator.kt` |
| 构建 | Gradle（Android） |
| 后端（仅参考） | Kotlin + Spring Boot 3 + Spring Data JPA |

## 目录

```
pay-dose/
├── android-app/              # Android 客户端（主战场）
│   ├── keystore/             # release 签名（不进 git，务必自行备份！）
│   └── app/src/main/
│       ├── assets/seed-data.json   # 首次启动导入的初始数据
│       └── java/com/dose/pay/android/
│           ├── core/         # 算法核心（Calculator 等，纯 Kotlin）
│           ├── data/         # DTO + Repository + 设置
│           │   └── local/    # Room: Entity / DAO / Database / 业务服务
│           └── ui/           # Compose 界面（calc/cards/dashboard/bills/...）
├── backend/                  # Spring Boot 后端（保留为备份/迁移/参考，不参与发布）
├── data/                     # 后端运行期数据与导出备份（不进 git）
├── docs/
└── scripts/
```

## 验收状态

| 层级 | 数量 | 结果 |
|---|---|---|
| App 算法单测（CalculatorTest） | 20 | ✅ |
| 黄金快照对账（与后端逐字段对比） | 5 | ✅ |
| **App 侧测试合计** | **25** | ✅ |
| 真机/模拟器功能验证 | 4 个页面 | ✅ 数据全部正确 |
| Release APK | 10.87 MB | ✅ v2 签名通过 |
| 后端 JUnit（保留参考） | 64 | ✅ |

## 快速开始

### Android（唯一需要构建的东西）

前置：Android SDK（platform 36 + build-tools）、JDK 17

```powershell
cd android-app

$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

.\gradlew.bat assembleRelease
```

APK 产出在 `app/build/outputs/apk/release/app-release.apk`。

安装：

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n com.dose.pay.android/.MainActivity
```

> `local.properties` 需写 `sdk.dir=E\:\\Android\\Sdk`（指向你自己的 SDK 路径）。
> 该文件已被 `.gitignore` 忽略，换机器需重新配置。

### 跑测试

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest          # 25 个用例（纯 JVM，无需设备）
```

### 签名（重要）

Release 签名文件在 `android-app/keystore/pay-dose.jks`，密码默认 `paydose2026`
（可用环境变量 `PAYDOSE_KEYSTORE_PASSWORD` / `PAYDOSE_KEY_PASSWORD` 覆盖）。

**这个文件必须永久保存、永不更换。** Android 判断「是不是同一个 App」看的是
`applicationId + 签名证书`。换签名会导致：

- 系统拒绝覆盖安装，只能卸载重装
- **卸载会清掉本地数据库，全部数据丢失**

请立刻备份到至少两处（U 盘 / 云盘 / 密码管理器）。当前签名指纹：

```
SHA-256: 33e7632f7726232c21ae1649426bee04c32a5de379860941131d7bd55adc37cc
```

### 后端（可选，仅用于数据迁移 / 查阅）

前置：JDK 17、Maven 3.6+

```powershell
cd backend
$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

mvn -B -DskipTests package
java -jar target/pay-dose.jar
```

服务起在 `http://127.0.0.1:8000`，SQLite 文件在 `backend/data/pay-dose.db`。

```powershell
mvn -B test                                    # 全部（64 个用例）
mvn -B test -Dtest=CalculatorTest              # 仅算法（无需数据库）
```

### 从原项目迁移数据

如果你有原 `credit-payment-planner` 的数据，见 [docs/migration.md](docs/migration.md)。

```powershell
cd backend
# 先预演，不写目标库
java -cp target/pay-dose.jar com.dose.pay.tools.MainKt --source sqlite --source-file "<旧库路径>" --dry-run
# 确认后正式迁移
java -cp target/pay-dose.jar com.dose.pay.tools.MainKt --source sqlite --source-file "<旧库路径>" --truncate
```


## 核心算法

还款日推算（`domain/Calculator.kt`）：

1. **非信用卡** → 即时扣款，还款日 = 支付日，占用 0 天
2. **找账单日** → 支付日当天或之后第一个账单日；`statement_day_inclusive=false` 时账单日**当天消费算下一期**
3. **固定还款日模式** → 还款日落在账单月内；若 `还款日 ≤ 账单日` 则顺延一个月
4. **账单日后 N 天模式** → 还款日 = 账单日 + N 天
5. **月末钳制** → 31 日遇 2 月取 28/29 日，遇 4 月取 30 日
6. **占用天数** = max(还款日 − 支付日, 0)
7. **过滤** → 已用 + 本次 > 额度 的方式直接剔除
8. **排序** → 占用天数降序取前 N（稳定排序，同天数保持卡 id 顺序）

示例：7 月 29 日刷 10000 元，账单日 28 / 还款日 17 的卡 → 下一账单日 8 月 28 → 还款日 9 月 17 → **占用 50 天**。

### 一个容易忽略的细节

`statement_day_inclusive` 的语义直接决定免息期长短：

- 账单日 **10 日**，在 **7 月 10 日** 消费
- `false` → 算下一期（账单日 8/10，还款日 8/25），占用 46 天
- `true` → 算本期（账单日 7/10，还款日 7/25），占用 15 天

差一个月。所以这个开关在卡片编辑页是必选项。

## 业务规则

**卡余额由流水原子维护**：`current_used = 初始 + Σ消费 − Σ还款`。

- 记账与余额变更在**同一事务**内完成，失败整体回滚
- 删除流水自动反向回滚（删消费 → 占用回降；删还款 → 占用回升）
- **消费允许超限**（真实场景存在，如实反映为负可用额度）
- **还款不能超过当前已用**（否则 400）
- **卡还有流水时不能删卡**（409，必须先清流水）

金额端到端用 `BigDecimal` + `RoundingMode.HALF_EVEN`，禁止 Double。
注意 Python `Decimal.quantize()` 默认就是银行家舍入，所以 `10.125 → 10.12`（不是 10.13）。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/methods` | 支付方式列表 |
| POST | `/api/methods` | 新增（重名 409） |
| PUT | `/api/methods/{id}` | 更新（不存在 404） |
| DELETE | `/api/methods/{id}` | 删除（有流水 409） |
| POST | `/api/recommend` | 生成推荐（只算不落库） |
| GET | `/api/cycle` | 看板：各卡当前账单周期消费（上一还款日 → 本还款日） |
| POST | `/api/transactions` | 记一笔（同事务改余额） |
| GET | `/api/transactions` | 流水列表（分页/筛选） |
| DELETE | `/api/transactions/{id}` | 删除流水（同事务回滚余额） |

金额一律 JSON **字符串**（`"10000.00"`）；日期 `"YYYY-MM-DD"`。
错误体 `{"detail": "中文说明"}`；类型错误返回 422 并附 `errors` 明细。

### `/api/cycle` 的统计口径

看板**不按自然月**统计，而是按每张信用卡自己的账单周期：

- 区间 `(上一还款日, 本还款日]`，左开右闭；
- 一笔消费算哪一期，**由它的还款日决定**（`Calculator.calculateDueDate`），
  所以月末钳制、跨年、`statement_day_inclusive` 都不会算歪；
- 还款日当天刷的算**下一期**（它的还款日已经滚过去了）；
- 还款日已过的卡显示**进行中的下一期**，不显示刚出账那期；
- 储蓄卡 / 电子钱包没有账单周期，**不进看板、不进合计**；
- 顶层 `total_spend` 是「各卡本期消费之和」——各卡还款日不同，周期区间天然不一致，
  不能理解成某个统一日期区间的合计。

## 免责声明

计算结果仅供参考。真实账单的还款日遇节假日可能顺延，以发卡行账单为准。

本工具不采集卡号、CVV、密码等敏感信息，只需要账单日与还款规则。
