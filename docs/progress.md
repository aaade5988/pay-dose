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
- [x] M6 流水页 + 记账弹层
- [x] Tab4 由「我的」改为「账单」（按月倒序聚合，2026-10 调整，见下）

### Tab4「我的」→「账单」变更（2026-10）

原「我的」页只有后端地址配置 / 功能说明 / 免责声明，作为一级 Tab 价值偏低，
现替换为**账单页**：按自然月倒序聚合，每月展示当月全部消费明细。

| 项 | 改动 |
|---|---|
| 路由 | `Routes.PROFILE("我的")` → `Routes.BILLS("账单")`，图标 `Icons.AutoMirrored.Rounded.ReceiptLong` |
| 新增 | `core/Bills.kt`（按月聚合纯逻辑）、`ui/bills/BillsScreen.kt`、`ui/bills/BillsViewModel.kt` |
| 删除 | `ui/profile/ProfileScreen.kt`、`ui/profile/ProfileViewModel.kt`（含后端地址配置入口） |
| 后端 | **零改动**，复用 `GET /api/transactions` 全量翻页拉取 |

**口径与实现要点**
- 月分桶键 = `txn_date` 前 7 位；**月倒序**，月内按 `txn_date DESC, id DESC`
- 月标题展示「消费 ¥X · N 笔」（消费合计 + 笔数），副行明细含消费与还款
- 明细行：卡名（字头色圆）+ 备注（空则回落到类型）+ 类型 chip + 金额（消费 `−¥` / 还款 `+¥` 绿）+ `MM-DD 周X`
- 金额全程走 `Money` 的「分」整数通道，无 Double/Float
- 首次进入会翻页拉全量（每页 200，上限 50 页）后再聚合，避免「只汇总前 200 笔」的错误月合计
- 支持：下拉刷新、卡筛选、月份折叠/展开、左滑删除（后端同事务回滚余额）

**行为变化提醒**：后端 Base URL 不再有 UI 配置入口（按需求「先直接丢掉」）。
默认仍为模拟器地址 `http://10.0.2.2:8000`（`SettingsStore` 保留该能力）；
真机联调若需改地址，需临时改 `SettingsStore` 默认值或后续补回设置入口。

### 看板口径统一为本期（2026-10-28）

用户两次反馈，最终结论：**看板不做跨周期叙事，全页只说「本期」**。

第一轮反馈「本期一笔没消费，但额度占用里显示了 222」的根因不是脏数据，
而是两套口径并排展示：顶部环用 `Σ current_used`（跨周期累积的全部未还本金），
下方周期块只统计「上一还款日 → 本还款日」。花呗（id=6，10 号账单 / 20 号还款）
2026-09-02 刷的 ¥222 属于上一期，本期消费为 0 而占用仍为 222；民生同理（¥4,444）。
当时补了一张「占用口径」说明卡解释差异，第二轮用户直接要求把差异本身消灭掉。

**最终改动**（只动客户端，后端零改动）

| 项 | 改动 |
|---|---|
| 顶部环 | 口径改为 **本期消费合计 ÷ Σ已设额度**（`Methods.cycleGauge`），本期没刷就是 0%，不再出现「本期 0、环里 222」 |
| 环中心 | `本期消费占用率` + `¥本期消费 / ¥Σ额度`；周期接口失败时环空轨、百分比显示 `—`，不猜数 |
| 看板「本期消费合计」卡 | **删除**（信息并入顶部环） |
| 看板「占用口径 · 额度占用 ≠ 本期消费」卡 | **删除**（口径已统一，无需解释） |
| `Cycles.kt` | 删除 `carriedOverCents` / `carriedOverNames` / `settledNames` / `hasSettled` / `CycleRow.netAmount` 等仅服务于该说明卡的死代码；`CycleAgg` 新增 `totalSpendCents` 供环当分子 |
| 「更新已用」弹层 | 保留，仍是 `current_used`（跨周期未还本金）的手改入口；提示改为「手改只改余额快照 · 本期消费仍按流水统计」 |

`Methods.DashRow` / `toDashRow` 保留（弹层仍要取当前已用与额度），但不再驱动列表渲染；
看板列表只有周期卡，点卡片打开「更新已用」。

### 记一笔：金额 / 备注输入修复（2026-10-28）

用户反馈「备注写不进字，样式也不行」；模拟器实测（`adb` 点击 + 输入）定位到真因：

**`BasicTextField` 内部文本布局的宽度会收缩到文字宽度**，所以 `TextStyle(textAlign = End)`
只在文字宽度内生效，等价于左对齐——光标停在行首（紧贴「备注」标签），与右侧那个
`fillMaxWidth + textAlign End` 的占位符各站一边，看起来像输入框坏了；文字宽度为 0 时
点击命中区也只有一条细线。金额行同病（输入 4040 会贴在「金额 ¥」后面）。

| 项 | 改动 |
|---|---|
| 新增 `SheetInput` | 用 `Box(fillMaxWidth, contentAlignment = CenterEnd)` 把文本布局顶到右端，光标/文字/占位符同一右基线；`clipToBounds` 防长备注向左压标签 |
| 整行可点 | `SheetField` 增加 `focused` / `onClick`：点标签或占位符都能聚焦，聚焦态边框+标签变紫、底色淡紫 |
| 备注 | 保持最普通的文本键盘（不设 imeAction / keyboardActions，中文输入法的回车要留给「提交候选词」），仍限 50 字 |
| 金额 | 同样右对齐，`¥` 符号跟随聚焦态变色 |

**排查结论：备注框本身没有中文 bug**。模拟器实测（`show_ime_with_hard_keyboard=1` 弹出软键盘）：

- Gboard 软键盘敲字母能进备注（`ccv`），敲数字能进金额并触发余额预览 → 输入法通道正常；
- 中文打不出来是因为**模拟器自带的 Gboard 没有 zh 语言子类型**（`ime list -a` 里无 `mSubtypeLocale=zh`），且模拟器无外网无法下载语言包；
- 已给模拟器安装 F-Droid 的开源 Rime 输入法 **Trime**（`com.osfans.trime`，自带朙月拼音），设为默认 IME 后实测：敲 `shuma` → 空格/点候选 → 备注框写入「數碼」，右对齐显示正常。

切回英文输入法：`adb shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`；
注意 Trime 默认把拼音显示在自己的候选栏（不内嵌到输入框），所以打字过程中输入框看起来是空的，选词后才落字——这不是应用的问题。

### 账单总览精简（2026-10-28）

顶部渐变总览卡原来一行三格「月份 / 笔数 / 还款」，信息重复且抢视觉。现在只留金额：

```
全部消费合计
¥8,706
```

| 项 | 改动 |
|---|---|
| 删除 | 总览卡的「月份」「笔数」「还款」三格（月份数与总笔数仍在底部提示「共 N 个月 · N 笔流水」里保留） |
| 笔数 | 先并入金额后面（`¥金额 · N 笔`），后按要求又整体去掉 —— 总览卡只统计金额 |
| 代码 | `OverviewCard(spendText, filtered)`；删掉只服务该行的 `OverviewCell` |

### 看板：下线「余额详情」弹层 + 左滑标记已还清（2026-10-28）

用户三步反馈：①「保存是做啥的」→ ②「保存没啥用，去掉吧」+「看板左滑可以标记已还清」→ ③「整个窗口都不要」。

**最终形态**：看板点卡片**不再弹任何窗口**，只有一个交互——左滑露出绿色「已还清」。

| 项 | 改动 |
|---|---|
| 弹层整块下线 | 删掉 `余额详情` ModalBottomSheet（含迷你环、当前已用信息）、`记一笔` 入口、`按账单清零`/`记为已还清` chip、`还款确认弹窗`、看板内的 `TxnSheet` |
| 先删手改 | 上一步已删「新的已用金额」输入框（`EditAmountRow`）+「保存 · PUT /api/methods」+ VM 的 `onSheetInput`/`recomputeSheet`/`saveSheet` |
| VM 收敛 | 删 `UpdateSheetState`、`state.sheet`、`state.methodList`、`openSheet`/`closeSheet`/`repayAll`/`createTransaction`/`onTxnCreated`；只留 `load` + `markSettled` |
| 左滑已还清 | 周期卡包一层 `SwipeRevealRow`（`actionLabel="已还清"`、绿色）→ 确认框「将记一笔还款 ¥X，「建设」余额归零」→ 写一笔还款流水（金额 = 该卡当前已用） |
| 组件泛化 | `SwipeRevealRow`：`deleteLabel/onDelete` → `actionLabel/onAction`，新增 `actionColor`；账单页/卡片页仍传「删除」 |
| 兜底 | 卡片当前无占用时不弹确认框，直接提示「当前无占用 · 无需标记」 |

注：客户端从此**没有任何手改已用余额的入口**——余额只能由流水推导，纠偏靠「左滑已还清」写还款流水。后端 `PUT /api/methods` 接口保留（卡片编辑仍会原样回传 `current_used`），只是不再有手改 UI。

技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.09.03 / minSdk 26

### 「已还清」改为人工标记 + 数据库加字段（2026-10-28）

用户反馈：「已还清的做法应该是该看板对应**不显示金额**、然后显示**已还清**；**仪表盘冻结对应份额的数据显示别的颜色**；而且**不是记录一条还款金额**，直接就是标记已还清 —— 看看是否是在数据库加多一个字段」。

**结论：确实要加字段**。之前那版「左滑已还清 = 写一笔还款流水」是错的：它会动 `current_used`、会在账单页多出一笔还款、还能被回滚，和用户心里「我标记一下这期已经还了」完全不是一回事。

#### 后端（加字段 + 新接口）

| 项 | 改动 |
|---|---|
| 建表字段 | `payment_methods.settled_until DATE NULL`（Hibernate `ddl-auto=update` 自动迁移，无需手写 SQL） |
| 字段语义 | 存的是**标记有效期 = 标记时所在账单周期的还款日**。写成日期而不是布尔值，是为了让标记**随周期自动失效**：过了本期还款日进入下一期，卡片自动恢复正常占用，不用人工清理；取消标记 = 置 NULL |
| 新接口 | `PUT /api/methods/{id}/settle`，body `{"settled": true/false}` → 返回该卡 `MethodDTO`（含 `settled`） |
| 周期汇总 | `CycleCardDTO` 加 `settled`；`CycleSummaryDTO` 加 `settled_spend`（已还清卡的本期消费合计） |
| 校验 | 非信用卡 / 缺还款规则 → 400「没有账单周期（只有信用卡能标记已还清）」 |
| **不动** | `current_used`、流水表、`PUT /api/methods` 的语义全部不变——标记纯靠这一个新字段 |

#### 客户端

| 项 | 改动 |
|---|---|
| 看板行 | 已还清的卡**不显示金额**，右侧换成绿色「已还清」chip |
| 左滑动作 | `已还清` ⇄ `取消标记`（已标记时按钮变灰）；确认框文案说明「只做标记、不写流水、不动余额，本期还款日后自动失效」 |
| 仪表盘 | `RingGauge` 新增 `frozenP`/`frozenColor`：主色弧 = 待还占用，**后面紧跟一段绿色冻结弧** = 已还清份额；中心大数字改成「本期待还占用率」= (本期消费 − 已还清) ÷ Σ额度，并单列一行绿色「已还清 ¥X」 |
| 图例 | 环下方补一行「● 已还清（冻结，不计入占用）　● 待还占用」（顺序与环上的绘制顺序一致） |
| 口径 | `Methods.cycleGauge(methods, spendCents, settledCents)`；`AggUsage` 增 `frozenP`/`settledText`/`hasSettled`；`CycleRow.settled`、`CycleAgg.settledSpendCents` |

**实测（模拟器 + 真实后端）**：标记「建设」后 → 该行显示绿色「已还清」、环变成 0% 占用 + 2 格绿色冻结段、中心显示「已还清 ¥4,040」、toast「已标记已还清 · 建设」；流水表仍是 3 笔且全是消费（**没写任何还款流水**），`current_used` 不变；左滑再点「取消标记」可还原。

**冻结段固定在左边**（用户反馈「被冻结的能不能都保持在左边」）：原先绿色段是「跟在主色弧后面」，占用率一变它就跟着漂移（同一张卡在不同时间看是两个位置）。改成**锚在弧的起点**（`START_ANGLE=135°`，环的左下角 = 左侧起点）：绿色永远从左端起画，主色紫色接在它后面。图例顺序也跟着调成「已还清 → 待还占用」，与环上的左右顺序一致。

#### 顺带修掉的后端既有 bug

`SqlitePathEnvironmentPostProcessor` 一直是**无效**的：它默认排在 `ConfigDataEnvironmentPostProcessor` 之前，那时 environment 里还没有 `spring.datasource.url`，重写逻辑直接被跳过 → 数据库路径退回「相对启动目录」，在 `backend/` 启动就变成 `backend/data/pay-dose.db`（与仓库根 `data/` 各一份，正是文档里记的「数据没同步」根因）。修法：实现 `Ordered.getOrder() = LOWEST_PRECEDENCE`，让它排在 ConfigData 之后跑。现在无论从哪个目录启动，数据库都固定到**项目根 `data/pay-dose.db`**（实测日志：`[pay-dose] 数据库位置（项目根目录）`）。

技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.09.03 / minSdk 26

### 计算页：删掉「支付后占用」迷你环（2026-10-28）

用户反馈（截图是「北农」这张不限额卡）：推荐结果顶部那个小虚线环 +「支付后占用 未设额度 / 记一笔后「看板」环形将同步此值」**没有意义**，去掉。

| 项 | 改动 |
|---|---|
| 删除 | 计算页 Top1 卡片底部的迷你环区块（小虚线环 + 「支付后占用 X%」+ 那句提示） |
| 理由 | ①不限额卡本来就显示「未设额度」，没有可看的数；②那句「记一笔后『看板』环形将同步此值」**已经不成立**——看板顶环早已改成「本期消费 ÷ Σ已设额度」，跟 `current_used` 无关 |
| 代码 | `CalcScreen` 删掉 `GapRing` 那段；`RecView` 去掉 `ringNone`/`ringP`/`ringColor`/`ringPctText` 四个只服务它的字段，`CalcViewModel` 里 `afterPct`/`ringStatus` 一并清理 |

卡片现在只剩：占用最长 → 卡名/第 N 名/账单日·还款日 → N 天资金占用 / 预计还款 → 支付前已用 / 支付后已用 / 支付后可用额度 → 预计日期还款，占用 N 天。

技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.09.03 / minSdk 26

### 账单明细：左滑删除按钮修复（2026-10-28）

用户反馈「向左滑动应该出一个删除按钮可以删除数据」——实测左滑有位移，但右侧是空白，按钮根本不出现。

**两个叠加的坑**（都在 `ui/components/SwipeReveal.kt`）：

| 坑 | 现象 | 修法 |
|---|---|---|
| 修饰符顺序 | `.background(Surface)` 写在 `.graphicsLayer{}` **前面** → 白色底在图层外，左滑时文字移走、白底留在原地，把底下的删除按钮整块盖住 | 调换顺序：`graphicsLayer` 在前、`background` 在后，底色跟着内容一起平移 |
| 无界高度约束 | 列表在 `verticalScroll` 里高度约束无穷，删除按钮的 `fillMaxHeight()` 退化成 0 高度 → 按钮「有宽度没高度」 | 根 Box 加 `height(IntrinsicSize.Min)`，把行高变成有界约束，按钮才能拿到真实行高 |

验证（模拟器实测）：左滑 → 右侧露出橙色「删除」→ 弹确认框「删除后 建设 余额将回滚 ¥4,040.00」→ 删除后流水消失、总额从 ¥8,706.01 回到 ¥8,706、提示「已删除，余额已回滚」。

技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.09.03 / minSdk 26

## 待办

- [x] Android 最终 APK 验收（clean assembleDebug → BUILD SUCCESSFUL，17.05MB）
- [ ] 端到端联调（模拟器 + 后端）
- [ ] 更新根 README 的 Android 使用说明
- [ ] 截图归档
- [ ] （可选）账单页补回后端地址设置入口

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
