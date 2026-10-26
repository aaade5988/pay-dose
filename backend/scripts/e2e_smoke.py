# -*- coding: utf-8 -*-
"""pay-dose 后端端到端冒烟测试。

模拟 Android 客户端的真实调用序列，验证后端在 SQLite 上的完整行为：
健康检查 → 建卡 → 推荐 → 记账 → 余额 → 流水 → 删除回滚 → 卡片编辑 → 删卡护栏。

金额全程字符串，与契约一致。
"""

import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8000"
PASS = 0
FAIL = 0


def call(method, path, body=None, expect=200):
    global PASS, FAIL
    url = BASE + path
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            payload = json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        status = e.code
        payload = json.loads(e.read().decode("utf-8") or "{}")

    tag = "OK  " if status == expect else "FAIL"
    if status == expect:
        PASS += 1
    else:
        FAIL += 1
    print(f"[{tag}] {method:6s} {path:46s} -> {status} (期望 {expect})")
    if status != expect:
        print(f"        响应: {json.dumps(payload, ensure_ascii=False)}")
    return payload


def section(title):
    print(f"\n{'=' * 70}\n{title}\n{'=' * 70}")


def main():
    section("1. 健康检查")
    body = call("GET", "/api/health")
    assert body.get("status") == "ok", body

    # 每次运行使用独立的卡名，避免与上一次残留冲突
    suffix = str(int(time.time()) % 100000)

    section("2. 卡片管理")
    card = call("POST", "/api/methods", {
        "name": f"工商{suffix}", "method_type": "信用卡",
        "credit_limit": "50000.00", "current_used": "0",
        "statement_day": 15, "due_day": 8,
        "due_days_after_statement": None, "statement_day_inclusive": False,
    }, expect=201)
    card_id = card["method"]["id"]
    print(f"        建卡 id={card_id} 可用额度={card['method']['available_limit']}")
    assert card["method"]["available_limit"] == "50000.00"

    # 长账期卡（用于验证推荐排序）
    call("POST", "/api/methods", {
        "name": f"广发{suffix}", "method_type": "信用卡",
        "credit_limit": "80000.00", "current_used": "0",
        "statement_day": 28, "due_day": 17,
    }, expect=201)

    # 储蓄卡（即时扣款）
    call("POST", "/api/methods", {
        "name": f"招行储蓄{suffix}", "method_type": "储蓄卡",
        "credit_limit": None, "current_used": "0",
        "statement_day": None, "due_day": None,
    }, expect=201)

    # 错误路径
    call("POST", "/api/methods", {
        "name": f"工商{suffix}", "method_type": "信用卡", "credit_limit": "1.00",
        "current_used": "0", "statement_day": 1, "due_day": 1,
    }, expect=409)
    call("POST", "/api/methods", {
        "name": f"缺额度{suffix}", "method_type": "信用卡", "credit_limit": None,
        "current_used": "0", "statement_day": 1, "due_day": 1,
    }, expect=400)
    call("POST", "/api/methods", {
        "name": f"缺账单日{suffix}", "method_type": "信用卡", "credit_limit": "1",
        "current_used": "0", "statement_day": None, "due_day": 1,
    }, expect=400)
    call("POST", "/api/methods", {
        "name": f"怪类型{suffix}", "method_type": "外币卡", "credit_limit": "1",
        "current_used": "0", "statement_day": 1, "due_day": 1,
    }, expect=422)

    section("3. 推荐计算")
    rec = call("POST", "/api/recommend", {"amount": "10000.00", "payment_date": "2026-07-29"})
    results = rec["results"]
    print(f"        结果数={len(results)}")
    for i, r in enumerate(results, 1):
        print(f"        {i}. {r['payment_method']:14s} 占用 {r['occupied_days']:3d} 天 "
              f"还款 {r['due_date']}  {r['reason']}")
    days = [r["occupied_days"] for r in results]
    assert days == sorted(days, reverse=True), f"应按占用天数降序，实际 {days}"
    assert results[0]["payment_method"].startswith("广发"), \
        f"第一名应为广发（账期最长），实际 {results[0]['payment_method']}"

    # 错误路径
    call("POST", "/api/recommend", {"amount": 100, "payment_date": "2026-07-29"}, expect=422)
    call("POST", "/api/recommend", {"amount": "0.00", "payment_date": "2026-07-29"}, expect=400)
    call("POST", "/api/recommend", {"amount": "100.00", "payment_date": "2026/07/29"}, expect=400)
    call("POST", "/api/recommend",
         {"amount": "100.00", "payment_date": "2026-07-29", "top_n": 200}, expect=422)

    section("4. 记账与余额原子维护")
    txn = call("POST", "/api/transactions", {
        "payment_method_id": card_id, "type": "消费", "amount": "1200.00",
        "txn_date": "2026-07-29", "note": "买手机",
    }, expect=201)
    txn_id = txn["transaction"]["id"]
    print(f"        消费流水 id={txn_id} 金额={txn['transaction']['amount']}")

    methods = call("GET", "/api/methods")
    m = [x for x in methods["methods"] if x["id"] == card_id][0]
    print(f"        消费后已用={m['current_used']} 可用={m['available_limit']}")
    assert m["current_used"] == "1200.00", m["current_used"]
    assert m["available_limit"] == "48800.00", m["available_limit"]

    # 还款（金额不带小数，验证归一化）
    call("POST", "/api/transactions", {
        "payment_method_id": card_id, "type": "还款", "amount": "200",
        "txn_date": "2026-07-29", "note": "",
    }, expect=201)
    methods = call("GET", "/api/methods")
    m = [x for x in methods["methods"] if x["id"] == card_id][0]
    print(f"        还款后已用={m['current_used']}")
    assert m["current_used"] == "1000.00", m["current_used"]

    # 还款超额 → 400，余额不变
    call("POST", "/api/transactions", {
        "payment_method_id": card_id, "type": "还款", "amount": "99999.00",
        "txn_date": "2026-07-29",
    }, expect=400)
    methods = call("GET", "/api/methods")
    m = [x for x in methods["methods"] if x["id"] == card_id][0]
    assert m["current_used"] == "1000.00", f"回滚失败，余额变成 {m['current_used']}"

    # 消费允许超限
    call("POST", "/api/transactions", {
        "payment_method_id": card_id, "type": "消费", "amount": "60000.00",
        "txn_date": "2026-07-30", "note": "超限消费",
    }, expect=201)
    methods = call("GET", "/api/methods")
    m = [x for x in methods["methods"] if x["id"] == card_id][0]
    print(f"        超限后已用={m['current_used']} 可用={m['available_limit']}")
    assert m["available_limit"].startswith("-"), "超限时可用额度应为负"

    # 其他错误路径
    call("POST", "/api/transactions", {
        "payment_method_id": card_id, "type": "消费", "amount": "1.00",
        "txn_date": "2026-07-30", "note": "x" * 51,
    }, expect=400)
    call("POST", "/api/transactions", {
        "payment_method_id": card_id, "type": "取现", "amount": "1.00",
        "txn_date": "2026-07-30",
    }, expect=422)
    call("POST", "/api/transactions", {
        "payment_method_id": 999999, "type": "消费", "amount": "1.00",
        "txn_date": "2026-07-30",
    }, expect=404)

    section("5. 流水列表与分页")
    txns = call("GET", "/api/transactions?limit=50")
    lst = txns["transactions"]
    print(f"        流水总数={len(lst)}")
    for t in lst:
        print(f"        #{t['id']} {t['txn_date']} {t['type']} {t['amount']:>10s} "
              f"{t['payment_method_name']} {t['note']}")
    dates = [t["txn_date"] for t in lst]
    assert dates == sorted(dates, reverse=True), f"应按日期降序，实际 {dates}"

    page1 = call("GET", "/api/transactions?limit=1")
    page2 = call("GET", "/api/transactions?limit=1&offset=1")
    assert page1["transactions"][0]["id"] != page2["transactions"][0]["id"], "分页应返回不同记录"

    filtered = call("GET", f"/api/transactions?method_id={card_id}&limit=50")
    assert all(t["payment_method_id"] == card_id for t in filtered["transactions"])

    call("GET", "/api/transactions?limit=0", expect=400)
    call("GET", "/api/transactions?limit=201", expect=400)
    call("GET", "/api/transactions?offset=-1", expect=400)

    section("6. 删除流水回滚余额")
    before = [x for x in call("GET", "/api/methods")["methods"] if x["id"] == card_id][0]
    print(f"        删除前已用={before['current_used']}")
    big = [t for t in lst if t["note"] == "超限消费"][0]
    deleted = call("DELETE", f"/api/transactions/{big['id']}")
    print(f"        删除后 card_current_used={deleted['card_current_used']}")
    assert deleted["card_current_used"] == "1000.00", deleted
    call("DELETE", f"/api/transactions/{big['id']}", expect=404)

    section("7. 卡片编辑")
    updated = call("PUT", f"/api/methods/{card_id}", {
        "name": f"工商改名{suffix}", "method_type": "信用卡",
        "credit_limit": "60000.00", "current_used": "1000.00",
        "statement_day": 12, "due_day": 20,
        "due_days_after_statement": None, "statement_day_inclusive": True,
    })
    print(f"        改后名称={updated['method']['name']} 账单日={updated['method']['statement_day']}")
    assert updated["method"]["name"] == f"工商改名{suffix}"
    # 编辑不应清空余额
    assert updated["method"]["current_used"] == "1000.00", updated["method"]["current_used"]
    call("PUT", "/api/methods/999999", {
        "name": "x", "method_type": "信用卡", "credit_limit": "1",
        "current_used": "0", "statement_day": 1, "due_day": 1,
    }, expect=404)

    section("8. 删卡护栏")
    call("DELETE", f"/api/methods/{card_id}", expect=409)
    for t in call("GET", f"/api/transactions?method_id={card_id}&limit=200")["transactions"]:
        call("DELETE", f"/api/transactions/{t['id']}")
    call("DELETE", f"/api/methods/{card_id}")
    call("DELETE", f"/api/methods/{card_id}", expect=404)

    section("结果")
    total = PASS + FAIL
    print(f"通过 {PASS}/{total}，失败 {FAIL}")
    if FAIL:
        print("存在失败用例")
        return 1
    print("全部通过 — 端到端冒烟 OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
