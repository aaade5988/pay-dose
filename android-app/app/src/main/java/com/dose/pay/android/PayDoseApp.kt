package com.dose.pay.android

import android.app.Application
import com.dose.pay.android.data.AppContainer

/**
 * 应用入口：在 onCreate 里初始化手写依赖容器 AppContainer。
 */
class PayDoseApp : Application() {

    /** 全局唯一的依赖容器 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
