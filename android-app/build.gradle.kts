// 根构建脚本：仅声明插件版本（版本统一由 gradle/libs.versions.toml 管理），不在此处 apply
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Room 的注解处理器（KSP）
    alias(libs.plugins.ksp) apply false
}
