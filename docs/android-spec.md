# pay-dose Android 客户端实现规格书

> 本文档是给 Codex CLI 的**唯一任务输入**。请完整阅读后，在 `E:\opc\aaade\pay-dose\android-app\` 目录内实现整个 Android 应用。

## 一、目标

把原微信小程序（`E:\opc\aaade\credit-payment-planner\miniprogram\`，v3「额度看板版」）的**全部界面与交互**复刻为原生 Android 应用（Kotlin + Jetpack Compose）。

与原版的两点差异：
1. **去掉登录体系**（新后端是单用户无认证），打开即用
2. 数据来自新后端 `E:\opc\aaade\pay-dose\backend`（Kotlin + Spring Boot + SQLite）

## 二、必读参考资料

按顺序阅读，这些是行为的事实源：

| 路径 | 内容 |
|---|---|
| `E:\opc\aaade\credit-payment-planner\miniprogram\utils\format.js` | **金额「分」整数运算**：toCents / fromCents / addAmount / subAmount / percentOf / sanitizeAmountInput / finalizeAmountInput / 日期工具 |
| `...\miniprogram\utils\method.js` | **环形与聚合算法**：usageStatus 阈值 / ringArc 弧长 / toDashRow 行模型 / aggregateUsage 聚合 / nextStatementDate / dayBeforeNearestStatement / ruleSummary |
| `...\miniprogram\utils\txn.js` | 流水领域：summarizeMonth 本月聚合 / groupByDay 按天分组 / toRowView 行模型 / shortName |
| `...\miniprogram\pages\index\index.js + .wxml + .wxss` | 计算页（Tab1）完整逻辑与样式 |
| `...\miniprogram\pages\cards\cards.js + .wxml + .wxss` | 卡片列表（Tab2） |
| `...\miniprogram\pages\card-edit\card-edit.js + .wxml + .wxss` | 卡片新增/编辑表单 |
| `...\miniprogram\pages\dashboard\dashboard.js + .wxml + .wxss` | 看板（Tab3，核心页面） |
| `...\miniprogram\pages\flow\flow.js + .wxml + .wxss` | 消费流水页（从看板进入的二级页） |
| `...\miniprogram\pages\profile\profile.js + .wxml + .wxss` | 我的（Tab4） |
| `...\miniprogram\components\txn-sheet\txn-sheet.js + .wxml + .wxss` | **记账弹层**（三入口共用） |
| `...\miniprogram\app.wxss` | 全局色板与公共类 |
| `E:\opc\aaade\credit-payment-planner\design\session-e2657f33-bf92-483f-94a8-11c02c048c09\index.html` | 视觉稿（浏览器可打开对照） |
| `E:\opc\aaade\pay-dose\backend\src\main\kotlin\com\dose\pay\api\Dtos.kt` | **后端契约字段名的权威定义** |

尺寸换算：视觉稿 `px × 2 = rpx`，750rpx 基准。Compose 里按 `dp` 设计，视觉稿 px 直接当 dp 用即可（手机宽度约 360-412dp）。

## 三、技术栈（硬性）

```
Kotlin 2.0.21
AGP 8.7.3
Compose BOM 2024.09.03
Material 3
minSdk 26 / compileSdk 36 / targetSdk 36
Retrofit 2.11.0 + OkHttp 4.12.0
kotlinx.serialization 1.7.3（JSON，字段名严格对齐后端）
Navigation Compose 2.8.3
Lifecycle ViewModel Compose 2.8.6
```

**金额约束（不可妥协）**：全程 `BigDecimal` 或「分」`Long`，**禁止 `Double`/`Float`**。
金额从后端来是字符串（如 `"10000.00"`），加减运算必须先转「分」整数。

DI 方案：手写简单容器（`AppContainer`）/ ViewModel Factory 即可，不必引入 Hilt（减少配置风险）。

## 四、后端契约

Base URL：`http://10.0.2.2:8000`（模拟器访问宿主机），可在「我的」页修改并持久化（DataStore 或 SharedPreferences）。

### 接口

```
GET    /api/health                                   → {"status":"ok"}
GET    /api/methods                                  → {"methods":[MethodDTO]}
POST   /api/methods                                  → 201 {"method":MethodDTO}
PUT    /api/methods/{id}                             → {"method":MethodDTO}
DELETE /api/methods/{id}                             → {"ok":true}
POST   /api/recommend                                → {"results":[RecItem]}
POST   /api/transactions                             → 201 {"transaction":TxnDTO}
GET    /api/transactions?method_id=&limit=50&offset=0 → {"transactions":[TxnDTO]}
DELETE /api/transactions/{id}                        → {"ok":true,"card_current_used":"x.xx"}
```

### 请求/响应体

`MethodInput`（POST/PUT 请求体）：
```json
{
  "name": "工商",
  "method_type": "信用卡",
  "credit_limit": "50000.00",
  "current_used": "0",
  "statement_day": 15,
  "due_day": 8,
  "due_days_after_statement": null,
  "statement_day_inclusive": false
}
```

`MethodDTO`（响应）：
```json
{
  "id": 1,
  "name": "工商",
  "method_type": "信用卡",
  "credit_limit": "50000.00",
  "current_used": "0.00",
  "available_limit": "50000.00",
  "statement_day": 15,
  "due_day": 8,
  "due_days_after_statement": null,
  "statement_day_inclusive": false,
  "active": true
}
```

`RecommendRequest`：`{"payment_date":"2026-07-29","amount":"10000.00","top_n":3}`

`RecItem`：
```json
{
  "payment_method_id": 1,
  "payment_method": "工商",
  "current_used": "0.00",
  "after_used": "10000.00",
  "credit_limit": "50000.00",
  "statement_day": 15,
  "due_date": "2026-09-08",
  "occupied_days": 41,
  "reason": "预计 2026-09-08 还款，占用 41 天"
}
```

`TxnInput`：`{"payment_method_id":1,"type":"消费","amount":"1200.00","txn_date":"2026-07-29","note":"买手机"}`

`TxnDTO`：
```json
{
  "id": 1,
  "payment_method_id": 1,
  "payment_method_name": "工商",
  "type": "消费",
  "amount": "1200.00",
  "txn_date": "2026-07-29",
  "note": "买手机",
  "created_at": "2026-10-27 01:58:04"
}
```

### 枚举
- `method_type` ∈ `信用卡` / `储蓄卡` / `电子钱包` / `其他`
- `type` ∈ `消费` / `还款`

### 错误处理
- 业务错误体：`{"detail": "中文错误信息"}` → 直接 toast 展示 `detail`
- 422 参数错误：`{"detail": "...", "errors":[...]}` → 同样展示 `detail`

## 五、页面规格

### Tab 结构（4 个）

`计算` / `卡片` / `看板` / `我的`
底部导航栏，选中色 `#5b50e6`，未选中 `#9a96a3`。

### 3.1 计算 Tab（首页，最重要）

**输入区**
- 紫色渐变卡片（primary #5b50e6 → 深紫），显示「本次支付金额」
- 大号金额显示（未聚焦时显示格式化文本，聚焦时是输入框）
- 点击卡片聚焦输入，`KeyboardType.Decimal`
- 逐键清洗：只留数字与首个小数点，整数最多 12 位、小数最多 2 位、去前导零（`sanitizeAmountInput`）
- 失焦时补全为两位小数（`finalizeAmountInput`）
- 有内容时显示清除按钮 ✕

**日期区**
- 日期行：📅 支付日期 + `YYYY-MM-DD` + 星期
- 点击弹日期选择器（Material3 DatePicker 或自定义）
- 快捷 chips：`今天` / `明天` / `本周末` / `下账单日前`
  - 「本周末」= 最近的周六（若今天就是周六或周日则取今天）
  - 「下账单日前」= 所有信用卡中**最近一个账单日的前一天**（至少是今天），点了 toast 显示「最近账单日前 · {卡名}」
  - 无信用卡时 toast「请先在「卡片」页添加信用卡」
  - 当前日期命中的 chip 高亮

**主按钮**：「生成前三名推荐」（loading 时显示「正在计算…」并禁用）

**结果区**（状态机：empty / loading / ready / error）
- empty：提示「计算结果将展示在此处 / 输入金额与日期，找出占用资金最久的付款方式」
- loading：「正在按资金占用天数排序…」
- error：⚠️ 无法生成推荐 + 错误信息 + 「去调整卡片额度」/「重新计算」链接
- ready：
  - 顶部摘要条：💰 金额 · 📅 日期
  - 分隔标题「推荐结果」+ 副标「按占用天数降序」
  - **Top1 大卡**：
    - 「🏆 占用最长」徽章
    - 头像圆（名称首字，按名称哈希取色）+ 名称 + 「第 1 名」chip
    - 规则文案（如「账单日 15 日 · 还款日 8 日」）
    - 大数字「{天数}天 资金占用」+ 右侧「预计还款 {日期}」
    - 键值行：支付前已用 / 支付后已用（含额度）/ 支付后可用额度
    - **迷你缺口环**：显示「支付后占用 XX%」，未设额度显示「未设额度」
    - 底部 hint：reason 文案
  - **Top2/3 紧凑行**：序号 + 小头像 + 名称 + `{MM-DD} 还款` + `{n} 天` + ›
    - 点击可置顶（与原版行为一致：把该行提到第一位）
  - 通栏「✏️ 记一笔」按钮 → 打开记账弹层（默认 Top1 卡 / 消费 / 当前金额 / 当前日期）
  - 底部脚注：`本次支付 ¥X · 共 N 种可用方式 · {过滤说明}`
  - 「查看额度看板 ›」链接 → 切到看板 Tab
- 固定底部免责声明：「结果仅供参考 · 节假日还款日可能顺延」

**空结果**：若 `results` 为空数组，显示「没有可用的支付方式：所有方式的「已用 + 本次」都超出额度，请调低金额或到「卡片」页提升额度。」

### 3.2 卡片 Tab

- 列表：每条卡片一行
  - 头像圆（首字 + 哈希色）
  - 名称 + 类型 chip（信用卡=主色 / 电子钱包=橙 / 其他=灰）
  - **一行规则文案**：信用卡 → `账单日 X 日 · 还款日 Y 日 · 额度 ¥Z`（或 `账单日后 N 天`）；非信用卡 → `即时扣款 · 不产生占用`
  - **不显示余额/进度条**（余额只在看板看）
- 点击行 → 进入编辑页
- 左滑露出删除按钮 → 确认弹窗「确定删除「{名称}」吗？删除后无法恢复。」
  - 删除失败（该卡有流水）→ toast 后端 detail「该卡还有 N 笔流水，请先删除流水」
- 顶部右上角「＋ 新增」→ 进入编辑页（新增模式）
- 空态：「还没有支付方式」+ 引导按钮

### 3.3 卡片编辑页

字段：
- 名称（文本输入，最多 30 字）
- 类型：分段控件 4 选 1（信用卡 / 储蓄卡 / 钱包 / 其他）
- 信用额度（金额输入，可空 = 不限额；仅信用卡必填）
- 账单日：picker 1–31（仅信用卡）
- 还款规则：二选一（固定还款日 / 账单日后 N 天）（仅信用卡）
  - 固定还款日 → picker 1–31
  - 账单日后 N 天 → picker 1–60
- 「账单日当天计入本期」开关（仅信用卡）+ 帮助弹窗说明

切到非信用卡时，隐藏账单日/还款规则/开关三组。

**编辑模式注意**：不采集 `current_used`，但保存时必须把后端原值**原样回传**（否则余额会被清零）。

前端校验（先行拦截，后端 400/409 的 detail 也要行内展示）：
- 名称非空、≤30 字
- 额度 ≥0 且 ≤99,999,999
- 信用卡：账单日 1–31、还款日 1–31 或天数 1–60

帮助弹窗文案：「开启后，账单日当天消费计入本期账单（还款更早）；关闭时，账单日当天消费算作下一期（资金占用更久）。」

### 3.4 看板 Tab（本版核心）

**顶部聚合仪表盘**
- 断开式环形（270° 弧，起点 135°），占用率 = Σ已用 / Σ额度
- 只统计「已设额度且额度 > 0」的卡
- 中心大号百分比 + 金额文本
- 副标注：「仅计入 X/Y 张设额度 · 不限额卡以虚线灰环单列」
- 无任何设额卡时中心显示「—」

**单卡行**
- 缺口环（同样 270° 弧），环色按阈值：
  - `< 50%` → 绿 `#13c296`，状态「安全」
  - `>= 50% && < 80%` → 橙 `#faad14`，状态「偏高」
  - `>= 80%` → 红 `#f5533d`，状态「吃紧」
  - 未设额度 / 储蓄卡 → 虚线灰环，状态「未设额度」/「即时扣款」
- 环中心显示占用百分比（如 `62%`），未设额度显示 `—`
- 右侧：名称 + 类型 chip + 状态 chip
- 金额行：`已用 ¥x · 可用 ¥y / 额度 ¥z`；储蓄卡 → `即时扣款 · 不计占用`；未设额 → `不限额 · 不计入聚合 · 虚线灰环`
- 超限时行内红字：`超额度 ¥X`

**点击卡行 → 「更新已用」半屏弹层**
- 顶部：卡名 + 迷你环实时预览（随输入变化）
- 金额输入：「当前已用金额」
- 超限提示：`超额度 ¥X`
- 快捷按钮：
  - 「记一笔 →」→ 打开记账弹层（锁定本卡 + 消费）
  - 「按账单清零」/「记为已还清」→ 确认弹窗 → 提交还款流水（amount = 当前已用，余额归零）
    - 弹窗文案：`将记一笔还款 ¥X（{note}），「{卡名}」余额归零。还款会写入消费流水，可在流水页追溯/回滚。`
    - 当前已用为 0 时 toast「当前无占用 · 无需还款」
- 主按钮「保存」：PUT 全量回传原字段 + 新 current_used
  - 数值未变 → toast「数值未变化」并关闭
  - 后端错误 detail 行内展示

**底部**：「查看消费流水 ›」→ 进入流水页

### 3.5 流水页（从看板进入）

- 顶部三月指标条：本月消费（橙）/ 本月还款（绿）/ 净占用（紫）
  - 选中某卡筛选后，指标随之变为该卡口径
- 筛选横滑：`全部` + 各卡（字头圆 + 名称前 2 字）
- 按天分组：日期头（`MM-DD 周X`）+ 组内流水行
- 流水行：字头小圆 + 卡名 + 类型 chip（消费=橙 / 还款=绿）+ 备注副行 + 右侧金额（消费 `−¥X` 深色，还款 `+¥X` 绿色，省略 `.00`）
- 左滑露出删除 → 确认弹窗：`删除后 {卡名} 余额将回滚 ¥X，确定删除这笔「{类型}」吗？`
  - 成功 toast「已删除，余额已回滚」并刷新
- 右下「＋ 记一笔」→ 记账弹层（不预选卡）
- 下拉刷新 + 触底加载更多（每页 200）

### 3.6 记账弹层（三入口共用）

参数：`methods`（卡列表）、`defaults {methodId, type, amount, date, lockMethod}`

- 遮罩 + 底部白色 sheet
- 类型切换：消费 / 还款分段
- 卡选择：横向滚动卡片（字头 + 名称 + 当前已用）
  - `lockMethod=true` 时禁用，toast「已锁定本卡，如需换卡请先返回」
- 金额输入（同样逐键清洗）
- 日期选择
- 备注输入（≤50 字）
- **余额预览行**：
  `余额预览 · {卡名} 已用 ¥A → ¥B（占用 x%→y%）· {消费允许超限 | 还款即冲减已用}`
  未设额度时显示「不限额」
- **还款超额前端拦截**：还款且金额 > 当前已用 → 行内红字 + 「确认记账」禁用
  - 文案：`还款金额不得超过「{卡名}」当前已用 ¥X`
- 提交：POST /api/transactions → 成功关闭并回调父页面刷新 + toast「已记入 {卡名}」

### 3.7 我的 Tab

- 后端地址配置（可编辑 Base URL + 保存 + 测试连接按钮）
- 版本信息
- 功能说明列表（服务端存储 / 导出备份（可占位）/ 免责声明）
- 免责声明：`计算结果仅供参考 · 还款日节假日可能顺延`

## 六、UI 设计令牌

```
primary      #5b50e6
secondary    #dcd8ff
accent       #f26b38   (橙，消费/警示)
repayGreen   #0d8a63   (还款绿)
bg           #f2f0eb   (页面背景)
surface      #ffffff
text         #171717
textMuted    #66645f
border       #d7d3ca

环形语义色：
  ringOk     #13c296
  ringWarn   #faad14
  ringBad    #f5533d
  ringTrack  #e8e5dd
```

头像色板（按名称首字 codePoint 哈希循环取色）：
```
#5b50e6 #e60012 #95d475 #1677ff #f26b38 #0f9d8f #b0407f #6b7280 #c9a227 #3f51b5
```

圆角：大卡 24dp，普通卡 16dp，chip 全圆角。
阴影：`0 24px 70px rgb(24 20 14 / 10%)`。

## 七、关键算法（必须精确移植）

### 「分」整数运算
```kotlin
// 字符串金额 → 分；非法返回 null
fun toCents(value: String?): Long?
// 分 → 两位小数字符串："12.30"
fun fromCents(cents: Long): String
// 逐键清洗：只留数字与首个小数点，整数≤12位，小数≤2位，去前导零
fun sanitizeAmountInput(raw: String): String
// 定稿："12." → "12.00"，"7.5" → "7.50"
fun finalizeAmountInput(raw: String): String
```

### 占用百分比
```kotlin
// 一位小数，不钳制（超限可 >100）；分母 ≤0 或 null → null
fun percentOf(usedCents: Long, limitCents: Long?): Double? {
    if (limitCents == null || limitCents <= 0) return null
    return Math.round(usedCents * 1000.0 / limitCents) / 10.0
}
```

### 环弧长
```kotlin
// --p = 占用率 × 0.75（整环只有 270°），占用率钳到 0..100
fun ringArc(pct: Double?): Float {
    if (pct == null) return 0f
    return (pct.coerceIn(0.0, 100.0) * 0.75).toFloat()
}
```

### 阈值状态
```kotlin
// 边界：>=80 吃紧，>=50 偏高，否则安全
fun usageStatus(pct: Double?): RingStatus
```

### 本月聚合
```kotlin
// monthKey = txn_date 前 7 位（YYYY-MM）与当前月相同
// 消费合计 / 还款合计 / 净占用 = 消费 − 还款
fun summarizeMonth(txns: List<TxnDTO>): MonthSummary
```

### 下一个账单日（用于「下账单日前」快捷 chip）
与后端同语义：取支付日当天或之后第一个账单日；
`inclusive=false` 时账单日当天算下一期。月末钳制。

## 八、验收标准

1. `.\gradlew.bat assembleDebug` 编译通过
2. APK 产出在 `app/build/outputs/apk/debug/app-debug.apk`
3. `gradlew.bat` + `gradle/wrapper/gradle-wrapper.jar` + `gradle-wrapper.properties` 在仓库内
4. `AndroidManifest.xml` 含 `<uses-permission android:name="android.permission.INTERNET" />`
   且 `<application ... android:usesCleartextTraffic="true">`（因为后端是 http）
5. `local.properties` 写 `sdk.dir=E\:\\Android\\Sdk`
6. 所有页面可通过导航到达，无死路由
7. 代码注释用中文，标识符用英文
8. 不引入未使用的依赖；金额无 Double 参与运算

## 九、构建环境（已验证）

```powershell
# 每次构建前必须设置（系统默认 java 是 1.8，会失败）
$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

cd E:\opc\aaade\pay-dose\android-app

# 本机没有预装 gradle，用这个生成 wrapper（只需一次）
& "$env:TEMP\gradle-8.10\bin\gradle.bat" wrapper --gradle-version 8.10

# 构建
.\gradlew.bat assembleDebug
```

- Android SDK：`E:\Android\Sdk`（platforms/android-36、build-tools/36.1.0 齐全）
- 网络可访问 Maven Central / Google Maven / gradle 分发站
- 首次构建下载依赖需 5–15 分钟，请耐心

## 十、约束与纪律

- **只在 `E:\opc\aaade\pay-dose\android-app\` 内写文件**
- **不要修改** `E:\opc\aaade\pay-dose\backend\`（已完成并有 58 个测试通过）
- **不要修改** `E:\opc\aaade\credit-payment-planner\`（参考用的原始项目，只读）
- **不要执行 `git commit`**（由主流程统一提交）
- git 仓库根是 `E:\opc\aaade\pay-dose`，`.gitignore` 已配置好（`build/`、`.gradle/`、`local.properties` 等已忽略）

## 十一、交付报告要求

完成后请报告：

1. APK 绝对路径 + 文件大小
2. 最后一次 `assembleDebug` 的输出摘要（须有 BUILD SUCCESSFUL）
3. 每个页面/弹层的完成度清单（完成 / 部分完成 / 未完成）
4. 技术决策说明（DI 方案、序列化方式、导航结构、状态管理）
5. 已知问题、未完成项及原因
6. 任何未能跑通的验证环节，说明卡在哪一步、具体错误是什么

## 十二、建议实施顺序

分阶段实现并**每阶段编译验证**，不要全部写完才编译：

1. **M1 骨架**：build.gradle.kts、settings.gradle.kts、AndroidManifest、MainActivity 空壳、主题、颜色 → `assembleDebug` 必须成功
2. **M2 数据层**：DTO（kotlinx.serialization）、Retrofit ApiService、Repository、AppContainer → 编译
3. **M3 计算 Tab**：金额输入 + 日期 + 推荐结果（含 Top1 大卡与迷你环）→ 编译
4. **M4 卡片 Tab + 编辑页** → 编译
5. **M5 看板 Tab**：聚合仪表盘 + 单卡环 + 更新已用弹层 → 编译
6. **M6 流水页 + 记账弹层 + 我的页** → 编译

每一步都跑 `.\gradlew.bat assembleDebug`，失败就修，不要积压。
