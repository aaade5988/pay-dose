# 数据迁移指南

把原 `credit-payment-planner` 的数据导入 pay-dose 的 SQLite 库。

## 迁移什么

| 原表 | 处理 |
|---|---|
| `payment_methods` | ✅ 迁移（卡片与还款规则） |
| `transactions` | ✅ 迁移（消费/还款流水） |
| `users` / `auth_tokens` | ❌ 丢弃（pay-dose 单用户，无认证） |
| `recommendations` / `recommendation_items` | ❌ 丢弃（计算历史功能已下线） |

**原 id 全部保留**，便于追溯。

## 对账口径

迁移完成后会输出对账报告，包含三层校验：

1. **逐行逐字段比对** — `payment_methods` 与 `transactions` 的每个业务字段与源库完全一致
2. **行数校验** — 源库与目标库行数必须相等
3. **余额一致性** — 每张卡的流水不得出现「还款合计 > 消费合计」的不可能状态

全部通过输出 `RECONCILE OK` 并以退出码 0 结束；任一失败输出差异明细并以退出码 1 结束。

> **关于余额校验的说明**：原 schema 没有「初始已用」字段，无法区分初始值与流水累积值。
> 因此这里校验的是流水自身自洽性，而非 `current_used == Σ消费 − Σ还款`。
> 若要逐卡核对，请看第 1 层的逐字段比对——它保证 `current_used` 原样搬过去。

## 前置

```powershell
cd E:\opc\aaade\pay-dose\backend

$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "D:\Program Files\Java\jdk-17\bin;$env:PATH"

mvn -B -DskipTests package
```

## 用法

### 1. 从 SQLite 冷备份迁移（推荐先试）

原项目可能留有 `data/app.db`（SQLite 冷备份）：

```powershell
# 先预演，不写目标库
java -cp target/pay-dose.jar com.dose.pay.tools.MainKt `
  --source sqlite `
  --source-file "E:\opc\aaade\credit-payment-planner\data\app.db" `
  --dry-run

# 确认无误后正式迁移
java -cp target/pay-dose.jar com.dose.pay.tools.MainKt `
  --source sqlite `
  --source-file "E:\opc\aaade\credit-payment-planner\data\app.db" `
  --target "./data/pay-dose.db" `
  --truncate
```

### 2. 从 MySQL 迁移

源库**只读**（工具只执行 SELECT）：

```powershell
java -cp target/pay-dose.jar com.dose.pay.tools.MainKt `
  --source mysql `
  --host 111.229.64.71 --port 6306 `
  --user root --password "你的密码" `
  --database credit_payment_planner `
  --target "./data/pay-dose.db" `
  --truncate
```

源库不可达时工具会在 5 秒超时后安全退出并给出中文提示，不会挂起。

## 参数说明

| 参数 | 说明 | 默认 |
|---|---|---|
| `--source` | 源库类型：`sqlite` / `mysql` | `sqlite` |
| `--source-file` | SQLite 源文件路径 | 必填（sqlite 模式） |
| `--host` | MySQL 主机 | `127.0.0.1` |
| `--port` | MySQL 端口 | `3306` |
| `--user` | MySQL 用户 | `root` |
| `--password` | MySQL 密码 | 空 |
| `--database` | MySQL 库名 | `credit_payment_planner` |
| `--target` | 目标 SQLite 文件 | `./data/pay-dose.db` |
| `--dry-run` | 只统计不写入 | 关 |
| `--truncate` | 写入前清空目标表 | 关 |
| `--help` | 显示帮助 | — |

## 金额精度注意事项

迁移工具把金额以 **TEXT** 形式写入 SQLite，而不是 `NUMERIC`。

原因：SQLite 的 `NUMERIC` 亲和性会把字符串 `'50000.00'` 自动转成整数 `50000`，
丢失两位小数的表示。虽然数值相等，但会让后续展示与对账出现意外差异。

已验证 JPA 能正确把 TEXT 读成 `BigDecimal`（见 `MigratedDataCompatTest`）。

## 迁移后验证

```powershell
# 启动服务
java -jar target/pay-dose.jar

# 查看卡片（金额应为两位小数）
Invoke-RestMethod http://127.0.0.1:8000/api/methods

# 查看流水
Invoke-RestMethod "http://127.0.0.1:8000/api/transactions?limit=10"
```

## 常见问题

**Q：重复迁移会不会产生重复数据？**

不会。工具用 `INSERT OR REPLACE` 按原 id 写入，配合 `--truncate` 可重复执行且结果一致（有 `migration is idempotent` 测试覆盖）。

**Q：迁移后能改回 MySQL 吗？**

可以，反向迁移只是把 SQLite 读出来写进 MySQL。但 pay-dose 的定位是单机小工具，一般不需要。

**Q：源库是旧版 SQLite，`credit_limit` 存的是 TEXT，会不会读错？**

不会。工具对金额统一用 `BigDecimal(text)` 解析，TEXT 与 DECIMAL 都能处理。
