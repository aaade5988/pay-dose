# pay-dose

信用卡支付资金规划器。算出「这笔钱刷哪张卡最晚还」，用资金占用天数换最长免息期。

输入一笔金额和日期，它会推算出每张卡的预计还款日，按**资金占用天数从多到少**排列，告诉你刷哪张最划算。

> **示例**：7 月 29 日刷 10000 元，账单日 28 号、还款日 17 号的卡 → 下一账单日是 8 月 28 日 → 还款日 9 月 17 日 → **占用 50 天**。

## 功能

- **计算推荐**：输入金额和日期，给出占用资金最久的 3 种支付方式
- **卡片管理**：维护各卡的账单日、还款日、信用额度
- **账单看板**：按各卡自己的账单周期统计本期消费，显示距还款日还有几天
- **消费流水**：记账、查账、删账，卡余额自动维护
- **已还清标记**：手动标记某卡本期已还，标记过还款日自动失效

数据全部存在手机本地，**不需要联网，不需要连电脑，装好就能用**。

## 安装

下载最新的 `app-release.apk` 直接安装。

系统要求：Android 8.0（API 26）及以上。

> 首次安装需要允许「安装未知来源应用」。这是旁路安装的正常要求，不是应用权限。

## 使用

### 计算

1. 输入这次要刷的金额
2. 选择支付日期（默认今天，点日期行可改）
3. 点「生成前三名推荐」

结果按占用天数排序，第一名是能让钱在手里待最久的那张卡。

### 添加卡片

进入「卡片」页 → 「新增」，填这些信息：

| 字段 | 说明 |
|---|---|
| 名称 | 如「招行」「花呗」 |
| 类型 | 信用卡 / 储蓄卡 / 电子钱包 / 其他 |
| 信用额度 | 信用卡必填；不限额就留空 |
| 账单日 | 每月出账单的日期（1-31） |
| 还款日 | 固定在每月几号还款（1-31） |
| 或：账单日后 N 天 | 如果还款日是「账单日 + N 天」，用这个代替固定的还款日 |

**账单日当天计入本期** 这个开关很关键，它决定免息期长短：

- 账单日 10 号，在 7 月 10 号消费
- **关**（默认）→ 算下一期，账单日 8/10，还款日 8/25，占用 **46 天**
- **开** → 算本期，账单日 7/10，还款日 7/25，占用 **15 天**

差一个月，按你发卡行的规则设置。

### 看板怎么看

看板**不按自然月统计**，而是按每张卡自己的账单周期：

```
周期区间 = (上一还款日, 本还款日]
```

一笔消费算哪一期，取决于**它什么时候要还**，不是它发生在几号。

- 还款日当天刷的，算**下一期**
- 还款日已过的卡，显示**进行中的下一期**
- 只有信用卡进看板；储蓄卡和电子钱包没有账单周期

各卡还款日不同，周期长短也不一样，所以顶部的合计是「各卡本期之和」，不是某个统一日期区间的总额。

每张卡行末的金额是**本期待还 = 本期消费 − 本期还款**，不是本期消费总额。记一笔还款后这个数字和顶部占用率会立刻下降，同时环上的绿色「已还」段会变长。

环上两段：绿色 = 已还（人工标记「已还清」的卡 + 本期实际还款流水），紫色 = 待还占用。左滑卡片的「已还清」是人工标记，不写流水、不动余额，只改变它算在绿色还是紫色里。

## 常见问题

**换了手机数据怎么办？**

目前数据只存在本机，没有云端同步。换机前需要用备份工具迁移数据库文件，或等后续版本支持导出。

**计算结果和银行账单对不上？**

本工具的计算仅供参考。真实还款日遇节假日可能顺延，一切以发卡行账单为准。

**会上传我的卡片信息吗？**

不会。应用不采集卡号、CVV、密码，只需要账单日和还款规则。所有数据仅在本地存储，全程不联网。

## 开发

技术栈：Kotlin + Jetpack Compose + Room（本地 SQLite）

### 构建

需要 JDK 17 和 Android SDK（platform 36）。

```powershell
cd android-app

$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

.\gradlew.bat assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

`assembleRelease` 需要 `keystore/pay-dose.jks` 和密码，缺任何一样都会**直接失败**，
不会回退 debug 签名：debug 证书没有私密性，用它发布的包任何人都能做「同应用」覆盖升级。
只跑 `assembleDebug` / 单元测试不受影响。

APK 使用 v2 + v3 签名；v3 为将来轮换密钥留出覆盖升级的可能。

### 安装到设备

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n com.dosepay.app/.MainActivity
```

### 测试

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest
```

41 个用例，纯 JVM 运行，不需要设备。其中 5 个是黄金快照对账，用来保证算法输出稳定。

### 项目结构

```
pay-dose/
├── android-app/                  主工程
│   ├── gradle/libs.versions.toml 依赖与插件版本集中管理
│   ├── keystore/                 release 签名（不入 git，自行备份）
│   └── app/src/
│       ├── main/assets/seed-data.json   首启导入的初始数据
│       ├── main/java/com/dosepay/app/
│       │   ├── MainActivity.kt / PayDoseApp.kt   入口
│       │   ├── core/             算法核心（纯 Kotlin，无框架依赖）
│       │   │   ├── Calculator.kt   还款日推算与推荐排序
│       │   │   ├── Formatters.kt   Money 金额 + Dates 日期工具
│       │   │   ├── Bills.kt / Cycles.kt / Txns.kt / Methods.kt   展示模型与聚合
│       │   ├── data/             Repository + Room 数据层
│       │   │   ├── Dto.kt / PlannerRepository.kt / UiEventBus.kt
│       │   │   └── local/          Room 实体、DAO、数据库、种子导入、本地服务
│       │   └── ui/               Compose 界面
│       │       ├── PayDoseRoot.kt        底部导航与页面宿主
│       │       ├── nav/                   路由表
│       │       ├── theme/                 配色、排版、圆角令牌
│       │       ├── components/            通用组件（卡片/徽标/按钮/状态/环图/日期选择）
│       │       ├── calc/ cards/ dashboard/ bills/ cardedit/ txnsheet/   各功能页
│       │       │   └── （XxxScreen.kt 页面 + XxxViewModel.kt 状态；拆出的子组件在 XxxComponents.kt）
│       └── test/                 单元测试
├── backend/                      旧版 Spring Boot 后端（仅存档，不参与构建）
├── data/                         本地数据与导出备份（不入 git）
└── docs/                         规格书、迁移指南、清理审计记录
```

`backend/` 是历史上的服务端实现，现在**不再使用也不参与发布**，只作为数据迁移工具和行为参考保留。

### 包名

`applicationId` 与 `namespace` 均为 `com.dosepay.app`。

### 签名

Release 签名文件在 `android-app/keystore/`，密码从环境变量或 `keystore/keystore.properties` 读取。

**这个密钥库必须永久保存。** Android 靠「applicationId + 签名」判定是不是同一个应用，换了签名就无法覆盖安装，用户只能卸载重装，本地数据会一起丢失。

### 版本发布

1. 修改 `app/build.gradle.kts` 里的 `versionCode`（必须递增）和 `versionName`
2. `.\gradlew.bat assembleRelease`
3. 在 Gitee 上打 tag（如 `v1.0.1`，与 `versionName` 对齐）并创建 Release
4. 把 APK 作为 Release 附件上传，文件名以 `.apk` 结尾

> 更新检查走 Gitee OpenAPI（`/repos/{owner}/{repo}/releases/latest`），**仓库必须公开**，否则 App 匿名请求只会拿到 404。Release 没挂 APK 附件时，「去下载」会打开 Release 网页。

## 免责声明

计算结果仅供参考。真实账单的还款日遇节假日可能顺延，以发卡行账单为准。

本工具不采集卡号、CVV、密码等敏感信息，只需要账单日与还款规则。
