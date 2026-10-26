# pay-dose

信用卡支付资金规划器 —— 算出「这笔钱哪张卡刷最晚还」，用资金占用天数换最长免息期。

输入一笔大额支付和日期，自动推算各支付方式的预计还款日，按**资金占用天数降序**推荐，尽量晚还钱。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Kotlin + Spring Boot 3 + Spring Data JPA |
| 数据库 | SQLite（单文件，零运维） |
| 客户端 | Kotlin + Jetpack Compose + Material 3（Android） |
| 构建 | Maven（后端） / Gradle（Android） |

单用户单机应用：无登录、无服务端部署负担，数据存本地 SQLite 文件。

## 目录

```
pay-dose/
├── backend/                  # Kotlin + Spring Boot 后端
│   ├── pom.xml
│   ├── scripts/e2e_smoke.py  # 端到端冒烟（42 断言）
│   ├── src/main/kotlin/com/dose/pay/
│   │   ├── domain/           # 算法核心 + 实体 + 仓储
│   │   ├── api/              # DTO + 业务服务
│   │   ├── web/              # REST 路由 + 全局异常映射
│   │   ├── config/           # Jackson 严格反序列化 + SQLite 目录初始化
│   │   └── tools/            # 数据迁移工具（Migrator + CLI）
│   └── src/test/             # 算法 19 + 接口 39 + 迁移 5 + 兼容 1
├── android-app/              # Android Compose 客户端（37 文件 5578 行）
│   └── app/src/main/java/com/dose/pay/android/
│       ├── core/             # 金额「分」运算 + 环形算法 + 日期工具
│       ├── data/             # DTO + Retrofit + Repository
│       └── ui/               # calc / cards / cardedit / dashboard / flow / profile
├── docs/
│   ├── android-spec.md       # Android 实现规格书
│   ├── migration.md          # 数据迁移指南
│   └── progress.md           # 开发进度与踩坑记录
└── scripts/run.py            # 一键启动后端
```

## 验收状态

| 层级 | 数量 | 结果 |
|---|---|---|
| 算法单测 | 19 | ✅ |
| 接口集成（SQLite） | 39 | ✅ |
| 迁移工具 | 5 | ✅ |
| 迁移后兼容 | 1 | ✅ |
| **JUnit 合计** | **64** | ✅ |
| 端到端冒烟 | 42 断言 | ✅ |
| Android 构建 | assembleDebug | ✅ 17MB APK |

## 快速开始

### 后端

前置：JDK 17、Maven 3.6+

```powershell
cd backend

# 必须指定 JDK 17（系统默认可能是 1.8）
$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

mvn -B -DskipTests package
java -jar target/pay-dose.jar
```

服务起在 `http://127.0.0.1:8000`，SQLite 文件自动创建在 `backend/data/pay-dose.db`。

冒烟验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/health
```

### 跑测试

```powershell
cd backend
mvn -B test                                    # 全部
mvn -B test -Dtest=CalculatorTest              # 仅算法（无需数据库）
mvn -B test -Dtest=PlannerApiTest              # 仅接口（SQLite 临时库）
```

### Android

前置：Android SDK（platform 36 + build-tools）、JDK 17

```powershell
cd android-app

$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

.\gradlew.bat assembleDebug
```

APK 产出在 `app/build/outputs/apk/debug/app-debug.apk`（约 17 MB）。

安装到模拟器或真机：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.dose.pay.android/.MainActivity
```

> `local.properties` 需写 `sdk.dir=E\:\\Android\\Sdk`（指向你自己的 SDK 路径）。
> 该文件已被 `.gitignore` 忽略，换机器需重新配置。

**模拟器访问宿主机后端**：App 里默认 Base URL 是 `http://10.0.2.2:8000`（`10.0.2.2` 是 Android 模拟器映射宿主机的地址）。如果后端跑在别的端口或用真机调试，在「我的」页里改 Base URL 即可。

### 跑测试

```powershell
cd backend
mvn -B test                                    # 全部（64 个用例）
mvn -B test -Dtest=CalculatorTest              # 仅算法（无需数据库）
mvn -B test -Dtest=PlannerApiTest              # 仅接口（SQLite 临时库）
```

### 端到端冒烟（需后端已启动）

```powershell
cd backend
python scripts/e2e_smoke.py
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
| POST | `/api/transactions` | 记一笔（同事务改余额） |
| GET | `/api/transactions` | 流水列表（分页/筛选） |
| DELETE | `/api/transactions/{id}` | 删除流水（同事务回滚余额） |

金额一律 JSON **字符串**（`"10000.00"`）；日期 `"YYYY-MM-DD"`。
错误体 `{"detail": "中文说明"}`；类型错误返回 422 并附 `errors` 明细。

## 免责声明

计算结果仅供参考。真实账单的还款日遇节假日可能顺延，以发卡行账单为准。

本工具不采集卡号、CVV、密码等敏感信息，只需要账单日与还款规则。
