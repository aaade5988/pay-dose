package com.dose.pay.tools

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.system.exitProcess

/**
 * 数据迁移命令行入口。
 *
 * 用法：
 * ```
 * java -cp pay-dose.jar com.dose.pay.tools.MainKt --help
 *
 * # 从原 MySQL 库迁移（只读源库）
 * java -cp pay-dose.jar com.dose.pay.tools.MainKt `
 *   --source mysql --host 111.229.64.71 --port 6306 --user root --password xxx --database credit_payment_planner
 *
 * # 从 SQLite 冷备份迁移
 * java -cp pay-dose.jar com.dose.pay.tools.MainKt --source sqlite --source-file data/app.db
 *
 * # 目标库（默认 ./data/pay-dose.db）
 *   --target ./data/pay-dose.db
 *
 * # 预演（不写目标库）
 *   --dry-run
 *
 * # 清空目标表后再写入
 *   --truncate
 * ```
 */
fun main(args: Array<String>) {
    val opts = parseArgs(args)

    if (opts.containsKey("help") || opts.containsKey("h")) {
        printHelp()
        return
    }

    val sourceKind = when (opts["source"]?.lowercase()) {
        "mysql" -> SourceKind.MYSQL
        "sqlite", null -> SourceKind.SQLITE
        else -> {
            System.err.println("未知 --source 取值：${opts["source"]}（支持 mysql / sqlite）")
            exitProcess(2)
        }
    }

    val dryRun = opts.containsKey("dry-run")
    val truncate = opts.containsKey("truncate")
    val targetPath = opts["target"] ?: "./data/pay-dose.db"

    println("=".repeat(70))
    println("pay-dose 数据迁移工具")
    println("=".repeat(70))
    println("源类型  ：$sourceKind")
    println("目标库  ：$targetPath")
    println("模式    ：${if (dryRun) "预演（dry-run，不写目标库）" else "正式迁移"}")
    println("清空目标：${if (truncate) "是" else "否"}")
    println("-".repeat(70))

    // -------------------- 打开源库（只读语义：只执行 SELECT）--------------------
    val sourceConn = try {
        when (sourceKind) {
            SourceKind.MYSQL -> {
                val host = opts["host"] ?: "127.0.0.1"
                val port = opts["port"] ?: "3306"
                val user = opts["user"] ?: "root"
                val password = opts["password"] ?: ""
                val database = opts["database"] ?: "credit_payment_planner"
                val url = "jdbc:mysql://$host:$port/$database" +
                    "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" +
                    "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000"
                println("连接源库：mysql://$host:$port/$database")
                DriverManager.getConnection(url, user, password)
            }
            SourceKind.SQLITE -> {
                val file = opts["source-file"] ?: run {
                    System.err.println("--source sqlite 时必须指定 --source-file")
                    exitProcess(2)
                }
                val p = Path.of(file)
                if (!Files.exists(p)) {
                    System.err.println("源 SQLite 文件不存在：${p.toAbsolutePath()}")
                    exitProcess(2)
                }
                println("连接源库：sqlite://${p.toAbsolutePath()}")
                DriverManager.getConnection("jdbc:sqlite:${p.toAbsolutePath()}")
            }
        }
    } catch (e: Exception) {
        System.err.println("\n[错误] 无法连接源库：${e.javaClass.simpleName}: ${e.message}")
        System.err.println("       请检查网络与凭据；源库不可达时工具会在此安全退出，不会挂起。")
        exitProcess(1)
    }

    // -------------------- 打开目标库 --------------------
    val targetConn = try {
        val p = Path.of(targetPath).toAbsolutePath()
        p.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
        DriverManager.getConnection("jdbc:sqlite:$p")
    } catch (e: Exception) {
        System.err.println("[错误] 无法打开目标库：${e.message}")
        sourceConn.close()
        exitProcess(1)
    }

    // -------------------- 执行迁移 --------------------
    val report = try {
        Migrator(
            sourceKind = sourceKind,
            sourceConn = sourceConn,
            targetConn = targetConn,
            dryRun = dryRun,
            truncate = truncate,
        ).run()
    } catch (e: Exception) {
        System.err.println("\n[错误] 迁移失败：${e.javaClass.simpleName}: ${e.message}")
        e.stackTrace.take(5).forEach { println("   at $it") }
        sourceConn.close()
        targetConn.close()
        exitProcess(1)
    } finally {
        runCatching { sourceConn.close() }
    }

    targetConn.close()

    // -------------------- 输出对账 --------------------
    report.print()

    if (dryRun) {
        println("\n提示：这是 --dry-run 预演，目标库未被写入。")
        println("      去掉 --dry-run 即可执行正式迁移。")
        return
    }

    if (!report.ok) {
        println("\n对账未通过，请检查上方差异明细。")
        exitProcess(1)
    }
}

private fun printHelp() {
    println(
        """
        pay-dose 数据迁移工具

        用途：把原 credit-payment-planner 的卡片与流水数据导入 pay-dose 的 SQLite 库。
              users / auth_tokens / recommendations 等表不迁移（pay-dose 无认证、无计算历史）。

        参数：
          --source <sqlite|mysql>   源库类型，默认 sqlite
          --source-file <路径>      SQLite 源文件（--source sqlite 时必填）
          --host <主机>             MySQL 主机，默认 127.0.0.1
          --port <端口>             MySQL 端口，默认 3306
          --user <用户>             MySQL 用户，默认 root
          --password <密码>         MySQL 密码
          --database <库名>         MySQL 库名，默认 credit_payment_planner
          --target <路径>           目标 SQLite 文件，默认 ./data/pay-dose.db
          --dry-run                 预演：只统计不写入
          --truncate                写入前清空目标表
          --help                    显示本帮助

        示例：
          # 预演（推荐先跑）
          java -cp pay-dose.jar com.dose.pay.tools.MainKt --source sqlite --source-file data/app.db --dry-run

          # 正式迁移
          java -cp pay-dose.jar com.dose.pay.tools.MainKt --source sqlite --source-file data/app.db --truncate

          # 从 MySQL 迁移（源库只读）
          java -cp pay-dose.jar com.dose.pay.tools.MainKt --source mysql --host 1.2.3.4 --port 3306 --user root --password xxx --database credit_payment_planner
        """.trimIndent()
    )
}

/** 解析 --key value 与 --flag 两种形式 */
private fun parseArgs(args: Array<String>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (!a.startsWith("--")) {
            i++
            continue
        }
        val key = a.removePrefix("--")
        // flag 形式：下一个参数是另一个 --xxx 或已到末尾
        if (i + 1 >= args.size || args[i + 1].startsWith("--")) {
            out[key] = "true"
            i++
        } else {
            out[key] = args[i + 1]
            i += 2
        }
    }
    return out
}
