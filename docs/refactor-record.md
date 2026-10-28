# pay-dose Android 工程重构记录

> 执行日期：2026-10-28
> 验证结果：强制真实编译（`--rerun-tasks --no-build-cache`）通过 · APK 打包成功 · 25 项单测全绿 · 后端编译未受影响

## 一、重构总览

| 维度 | 重构前 | 重构后 |
|---|---|---|
| 包名 | `com.dose.pay.android` | `com.dosepay.app` |
| Kotlin 文件数 | 39（main）+ 2（test） | 45（main）+ 2（test） |
| 最大单文件 | 488 行（TxnSheet.kt） | 376 行（LocalPlannerService.kt，数据层） |
| UI 层最大单文件 | 451 行 | 333 行（CalcScreen.kt） |
| 依赖版本管理 | 硬编码在 build.gradle.kts | `gradle/libs.versions.toml` 版本目录 |
| 未使用导入 | 16 处 | 0 处 |

## 二、包名迁移

`com.dose.pay.android` → `com.dosepay.app`，同步更新：

- 源码目录 `app/src/main/java/com/dose/pay/android/` → `com/dosepay/app/`
- 测试目录 `app/src/test/java/com/dose/pay/android/` → `com/dosepay/app/`
- 全部 41 个 Kotlin 文件的 `package` 声明与 `import`
- `build.gradle.kts` 的 `namespace` 与 `applicationId`
- README 中的 adb 启动命令

> **⚠️ 影响说明**：`applicationId` 变更后，已安装旧版（`com.dose.pay.android`）的设备**无法覆盖升级**，需先卸载再装，本地数据库会随之丢失。当前 versionCode=1 未上架，此风险已确认可接受。

## 三、文件合并（零碎文件收敛）

| 原文件 | 处理 | 去向 |
|---|---|---|
| `core/Money.kt`（151 行） | 合并 | `core/Formatters.kt` |
| `core/Dates.kt`（48 行） | 合并 | `core/Formatters.kt` |
| `ui/components/Common.kt`（274 行，10 个组件） | 拆分 | 见下 |
| `Radii` 圆角令牌 | 移出 | `ui/theme/Shape.kt`（设计令牌归主题层） |

**Common.kt 拆分结果**：

| 新文件 | 行数 | 内容 |
|---|---|---|
| `ui/components/StateViews.kt` | 77 | `PayCard`、`EmptyStateBox`、`ErrorStateBox`（容器与状态页） |
| `ui/components/DisplayComponents.kt` | 137 | `Avatar`、`PayChip`、`SectionDivider`、`HintText`、`KeyValueRow`（展示类） |
| `ui/components/Buttons.kt` | 80 | `PrimaryButton`、`SegControl`（交互类） |
| `ui/theme/Shape.kt` | 9 | `Radii` 圆角令牌 |

**合并理由**：Money 与 Dates 同源于原小程序 `utils/format.js`，同属「纯格式化工具」，合并后 165 行，职责单一且内聚。

## 四、巨型文件拆分

| 原文件 | 行数 | 拆分为 | 主文件剩余 |
|---|---|---|---|
| `ui/bills/BillsScreen.kt` | 427 | `BillsScreen.kt` + `BillsComponents.kt`（222 行） | 206 行 |
| `ui/txnsheet/TxnSheet.kt` | 488 | `TxnSheet.kt` + `TxnSheetParts.kt`（148 行） | 335 行 |
| `ui/cardedit/CardEditScreen.kt` | 354 | `CardEditScreen.kt` + `CardEditRows.kt`（83 行） | 262 行 |

**拆分原则**：页面主体（`XxxScreen`，负责状态收集与布局编排）保留在 `XxxScreen.kt`；无状态的私有子组件提取到 `XxxComponents.kt` / `XxxParts.kt` / `XxxRows.kt`，可见性由 `private` 调整为 `internal`（同模块内可见，不扩大 API 面）。

## 五、工程配置规范化

### 5.1 Gradle 版本目录

新增 `android-app/gradle/libs.versions.toml`：

- 集中管理 `agp` / `kotlin` / `ksp` / `composeBom` / `room` / `junit` 等 12 个版本号
- 依赖坐标改为 `libs.*` 别名访问
- 根 `build.gradle.kts` 与 `app/build.gradle.kts` 的 `plugins {}` 全部改用 `alias(libs.plugins.*)`

这样升级依赖只需改一处，且 IDE 能对版本提供补全与校验。

### 5.2 清理残留配置

| 项 | 处理 | 理由 |
|---|---|---|
| `app/proguard-rules.pro` | 删除 | 文件仅 2 行注释，且 `isMinifyEnabled = false`，规则完全不生效 |
| `build.gradle.kts` 的 `proguardFiles(...)` | 移除 | 同上，未启用混淆时该行无实际作用 |
| `res/values/colors.xml` 的 `<color name="primary">` | 删除 | 全库 0 处引用（`bg`、`white`、`ic_launcher_background` 保留在用） |
| 16 处未使用 import | 清理 | 编译警告来源 |

### 5.3 README 更新

- 项目结构树重写，反映新的目录与文件划分
- adb 启动命令包名更新为 `com.dosepay.app`
- 新增「包名」小节说明 `applicationId` 与 `namespace`

## 六、验证记录

```
# 强制真实编译（绕过构建缓存与增量）
./gradlew :app:compileDebugKotlin --offline --no-build-cache --rerun-tasks
→ BUILD SUCCESSFUL in 34s

# APK 打包
./gradlew :app:assembleDebug
→ BUILD SUCCESSFUL

# 单元测试
./gradlew :app:testDebugUnitTest
→ com.dosepay.app.CalculatorTest      tests=20 failures=0 errors=0
→ com.dosepay.app.GoldenSnapshotTest  tests=5  failures=0 errors=0

# 后端未受影响
mvn -o compile
→ 退出码 0
```

## 七、未处理项（明确按需求排除）

- `backend/` 模块：按要求保持原样，未做任何结构调整
- 构建产物清理（`backend/target/` 131 MB、`.gradle/`、`.idea/`）：属「删文件」而非「整理代码」，留待单独确认
- `AndroidManifest.xml` 的 `INTERNET` 权限与 `usesCleartextTraffic`：属功能边界变更，需真机冒烟后再动
