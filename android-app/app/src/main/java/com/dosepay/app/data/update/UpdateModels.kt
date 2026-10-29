package com.dosepay.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gitee Releases API 响应模型。
 *
 * 只取更新功能用得到的字段，其余忽略（ignoreUnknownKeys = true）。
 * 与 GitHub 的差异：没有 draft / published_at / html_url，
 * 发布时间叫 created_at，Release 网页地址得自己按 tag 拼。
 */
@Serializable
data class GiteeRelease(
    /** 标签名，如 "v1.1.0" */
    @SerialName("tag_name") val tagName: String = "",
    /** 版本标题，如 "v1.1.0" */
    val name: String = "",
    /** 更新说明（Markdown 原文） */
    val body: String = "",
    /** 是否预发布 */
    val prerelease: Boolean = false,
    /** 发布时间 ISO8601 */
    @SerialName("created_at") val createdAt: String = "",
    /** 附件列表（APK 在里面） */
    val assets: List<GiteeAsset> = emptyList(),
) {
    /** 找第一个 .apk 附件 */
    val apkAsset: GiteeAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    /**
     * 从 tag 解析版本号，如 "v1.1.0" → [1, 1, 0]。
     *
     * 容错：容忍前缀 v/V、多余字符，取连续数字段。
     */
    val versionParts: List<Int>
        get() = VERSION_PATTERN.findAll(tagName).map { it.value.toIntOrNull() ?: 0 }.take(3).toList()
}

/** Release 附件 */
@Serializable
data class GiteeAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

/**
 * 更新检查结果。
 *
 * 用密封接口明确区分状态，避免调用方用 null / 布尔混搭判空。
 */
sealed interface UpdateResult {

    /** 检查中 */
    data object Checking : UpdateResult

    /** 已是最新版本 */
    data class UpToDate(val currentVersion: String) : UpdateResult

    /** 有新版本可用 */
    data class Available(
        val currentVersion: String,
        val latestVersion: String,
        val releaseNotes: String,
        /** APK 直链；没有 apk 附件时为 null，此时只能跳网页 */
        val apkDownloadUrl: String?,
        val apkSizeBytes: Long,
        /** Release 网页地址 */
        val releasePageUrl: String,
    ) : UpdateResult

    /** 检查失败（网络 / 解析 / 限流） */
    data class Failed(val message: String) : UpdateResult
}

/**
 * 版本号比较工具。
 *
 * 规则：逐段比较数字，缺位按 0 处理。
 * 例：1.0.0 < 1.0.1 < 1.1.0 < 2.0.0
 */
object VersionComparator {

    /** 解析 "v1.2.3" → [1, 2, 3] */
    fun parse(version: String): List<Int> =
        VERSION_PATTERN.findAll(version).map { it.value.toIntOrNull() ?: 0 }.take(4).toList()

    /**
     * 比较两个版本号。
     * @return 负数表示 a < b，0 表示相等，正数表示 a > b
     */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    /** b 是否比 a 新 */
    fun isNewer(a: String, b: String): Boolean = compare(b, a) > 0
}

/** 内部用的正则（避免重复编译） */
private val VERSION_PATTERN = Regex("\\d+")

/** 更新接口相关的 JSON 解析器 */
internal val updateJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
