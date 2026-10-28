import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
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
    namespace = "com.dose.pay.android"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.dose.pay.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 有 keystore + 密码就用正式签名，否则回退 debug 签名（便于无密钥环境构建）
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
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── 本地数据库（取代原 Spring Boot 后端）──
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    // 本地化后仍需测试依赖跑算法单测
    testImplementation("junit:junit:4.13.2")
}
