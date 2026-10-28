package com.dose.pay

import com.dose.pay.config.SqlitePathEnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 应用入口。
 *
 * 启动：`mvn spring-boot:run` 或 `java -jar target/pay-dose.jar`
 * 默认端口 8000。数据落盘在 SQLite 文件（默认 <项目根>/data/pay-dose.db）。
 */
@SpringBootApplication
class PayDoseApplication

fun main(args: Array<String>) {
    val app = SpringApplication(PayDoseApplication::class.java)
    // 显式注册：`META-INF/spring.factories` 在 Spring Boot 可执行 jar 中会被打到
    // BOOT-INF/classes 之外，导致加载不到。直接在 main 里挂监听器最稳。
    app.addListeners(ApplicationListener<ApplicationEnvironmentPreparedEvent> { event ->
        SqlitePathEnvironmentPostProcessor().postProcessEnvironment(event.environment, app)
    })
    app.run(*args)
}
