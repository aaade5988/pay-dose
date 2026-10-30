import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ── Release 签名：密码只从环境变量 / keystore.properties 读取，不写进版本控制 ──
val keystoreFile = rootProject.file("keystore/pay-dose.jks")
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
val signingStorePass: String? =
    System.getenv("PAYDOSE_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
val signingKeyPass: String? =
    System.getenv("PAYDOSE_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
val signingKeyAlias: String = keystoreProps.getProperty("keyAlias") ?: "pay-dose"
val hasReleaseSigning = keystoreFile.exists() && signingStorePass != null

android {
    namespace = "com.dosepay.app"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.dosepay.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"
    }

    /**
     * Release 签名。
     *
     * 【极其重要】这个 keystore 必须永久保存、永不更换：
     * Android 判断「是不是同一个 App」看的是 applicationId + 签名证书。
     * 换了签名，系统会拒绝覆盖安装，用户只能卸载重装 —— 那时本地数据库会一起没掉。
     *
     * 文件位置：android-app/keystore/pay-dose.jks（已被 .gitignore 忽略）
     * 请自行多处备份：U 盘 / 云盘 / 密码管理器。
     */
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = keystoreFile
                storePassword = signingStorePass
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPass ?: signingStorePass
                // v3 让未来「密钥轮换」成为可能：换签名后仍能覆盖升级老用户
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 未启用混淆时不需要 proguard 规则文件；开启 minify 后再补上
            // 这里的 debug 分支仅用于无密钥环境跑通构建；真正出包时由下面的
            // taskGraph 钩子直接中止，产物不会被误发布。
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 让 BuildConfig.VERSION_NAME 可用（更新检查需要读取当前版本号）
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

/**
 * 出包闸门：要构建 release 却没有正式签名时，直接让构建失败。
 *
 * 为什么不能沉默地回退 debug 签名：debug 证书的私钥没有保密性
 * （口令固定、密钥库可能被复制），用它发布的 APK 任何人都能做「同应用」覆盖升级。
 * 宁可构建失败，也不产出一个可被顶替的包。
 */
if (!hasReleaseSigning) {
    gradle.taskGraph.whenReady {
        val buildingRelease = allTasks.any { task ->
            task.project == project &&
                (task.name == "assembleRelease" || task.name == "bundleRelease" ||
                    task.name.startsWith("packageRelease"))
        }
        if (buildingRelease) {
            throw GradleException(
                "release 构建缺少签名密钥，已中止。\n" +
                    "需要 android-app/keystore/pay-dose.jks，并设置 PAYDOSE_KEYSTORE_PASSWORD" +
                    "（或写在 keystore/keystore.properties 里）。\n" +
                    "不会回退 debug 签名：那样产出的包任何人都能做覆盖升级。"
            )
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // ── 本地数据库（取代原 Spring Boot 后端）──
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 更新检查（访问 GitHub Releases API）
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // 本地化后仍需测试依赖跑算法单测
    testImplementation(libs.junit)
}
