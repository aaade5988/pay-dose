# -*- coding: utf-8 -*-
"""查看原项目 SQLite 库的表结构与数据量。"""

import sqlite3
import sys

DB = r"E:\opc\aaade\credit-payment-planner\data\app.db"

conn = sqlite3.connect(DB)

print("=" * 60)
print("原项目数据库:", DB)
print("=" * 60)

# 表清单与行数
tables = [r[0] for r in conn.execute(
    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
)]
print("\n表清单:")
for t in tables:
    try:
        n = conn.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
    except Exception as e:
        n = f"读取失败 {e}"
    print(f"  {t:28s} {n} 行")

# 支付方式明细
if "payment_methods" in tables:
    print("\n" + "-" * 60)
    print("payment_methods 明细:")
    cols = [d[1] for d in conn.execute("PRAGMA table_info(payment_methods)")]
    print("  列:", cols)
    rows = conn.execute(
        "SELECT id, name, method_type, credit_limit, current_used, "
        "statement_day, due_day, due_days_after_statement, statement_day_inclusive, active "
        "FROM payment_methods ORDER BY id"
    ).fetchall()
    for r in rows:
        (mid, name, mtype, limit, used, sday, dday, after, incl, active) = r
        rule = f"账单日{sday}"
        if dday is not None:
            rule += f" 还款日{dday}"
        elif after is not None:
            rule += f" 账单日后{after}天"
        else:
            rule = "即时扣款"
        limit_text = "不限额" if limit is None else f"额度{limit}"
        print(f"  #{mid} {name:12s} {mtype:6s} 已用{used:>10s} {limit_text:16s} {rule}")
    print(f"  小计: {len(rows)} 张卡")
    total_used = sum(float(r[4] or 0) for r in rows)
    print(f"  已用合计: {total_used:.2f}")

# 流水明细
if "transactions" in tables:
    print("\n" + "-" * 60)
    print("transactions 明细:")
    cnt = conn.execute("SELECT COUNT(*) FROM transactions").fetchone()[0]
    if cnt == 0:
        print("  （无流水）")
    else:
        rows = conn.execute(
            "SELECT id, payment_method_id, type, amount, txn_date, note "
            "FROM transactions ORDER BY txn_date DESC, id DESC LIMIT 30"
        ).fetchall()
        for r in rows:
            print(f"  #{r[0]} 卡{r[1]} {r[2]} {r[3]:>10s} {r[4]} {r[5] or ''}")
        if cnt > 30:
            print(f"  ... 共 {cnt} 条，仅显示前 30 条")
        print(f"  小计: {cnt} 条流水")

# 其他表的有效内容
for t in ("users", "auth_tokens", "recommendations", "recommendation_items"):
    if t in tables:
        n = conn.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
        print(f"\n  {t}: {n} 行" + ("（不迁移到 pay-dose）" if t in ("users", "auth_tokens", "recommendations", "recommendation_items") else ""))

conn.close()
print("\n" + "=" * 60)
