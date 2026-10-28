// 根构建脚本：仅声明插件版本，不在此处 apply
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // Room 的注解处理器（KSP）
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
