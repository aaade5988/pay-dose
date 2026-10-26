package com.dose.pay.config

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import java.nio.file.Files
import java.nio.file.Path

/**
 * SQLite 数据文件目录初始化。
 *
 * SQLite JDBC 驱动不会自动创建父目录，若 `./data/` 不存在会直接抛
 * `path to './data/pay-dose.db': '...' does not exist`。
 *
 * 这里在 Spring 环境准备阶段（数据源建立之前）就把目录建好。
 *
 * 注意：此监听器通过 `META-INF/spring.factories` 注册，而不是 @Component——
 * 因为 ApplicationEnvironmentPreparedEvent 触发时组件扫描尚未发生。
 */
class SqliteDirectoryInitializer : ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        val url = event.environment.getProperty("spring.datasource.url") ?: return
        ensureParentDirectory(url)
    }

    companion object {
        /** 从 jdbc:sqlite:<path>?<params> 中解析出文件路径并创建其父目录 */
        fun ensureParentDirectory(jdbcUrl: String) {
            val prefix = "jdbc:sqlite:"
            if (!jdbcUrl.startsWith(prefix)) return
            val raw = jdbcUrl.removePrefix(prefix).substringBefore('?')
            // 内存库无需处理
            if (raw.isBlank() || raw == ":memory:") return
            val parent = Path.of(raw).toAbsolutePath().parent ?: return
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }
    }
}
