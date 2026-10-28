package com.dosepay.app

import android.app.Application
import com.dosepay.app.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：在 onCreate 里初始化手写依赖容器 AppContainer。
 *
 * 改造后不再需要连接后端，首次启动时把内置的真实数据写入本地数据库。
 * 导入在后台协程里做，不阻塞启动；界面首次读库时数据已就绪（Room 会串行化）。
 */
class PayDoseApp : Application() {

    /** 全局唯一的依赖容器 */
    lateinit var container: AppContainer
        private set

    /** 应用级协程作用域（导入种子数据等后台任务用） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.seedIfNeeded()
        }
    }
}
