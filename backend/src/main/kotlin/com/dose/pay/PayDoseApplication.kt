package com.dose.pay

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 应用入口。
 *
 * 启动：`mvn spring-boot:run` 或 `java -jar target/pay-dose.jar`
 * 默认端口 8000。数据落盘在 SQLite 文件（默认 ./data/pay-dose.db）。
 */
@SpringBootApplication
class PayDoseApplication

fun main(args: Array<String>) {
    runApplication<PayDoseApplication>(*args)
}
