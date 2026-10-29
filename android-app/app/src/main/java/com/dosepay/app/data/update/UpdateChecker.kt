package com.dosepay.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gitee Releases 更新检查器。
 *
 * 独立持有 OkHttp 实例——不要复用业务层客户端，
 * 因为业务层已经完全本地化、不再有任何网络代码。
 *
 * 匿名访问 Gitee OpenAPI 即可，不需要 access_token；
 * 代价是限流，因此超时给得短，失败就当没有更新。
 *
 * 失败一律转成 [UpdateResult.Failed]，绝不抛异常打扰用户：
 * 更新检查失败不应该影响 App 正常使用。
 */
class UpdateChecker(
    private val context: Context,
    /** Gitee 仓库，格式 "owner/repo" */
    private val repo: String = DEFAULT_REPO,
) {

    companion object {
        /** 你的 Gitee 仓库 */
        const val DEFAULT_REPO = "mechanical-running-roll-wang/pay-dose"

        private const val API_BASE = "https://gitee.com/api/v5"
        private const val WEB_BASE = "https://gitee.com"

        /** 更新检查是锦上添花，超时短一点，失败就算了 */
        private const val TIMEOUT_SECONDS = 12L

        /** 回退列表接口时一次拉多少条 */
        private const val FALLBACK_PAGE_SIZE = 10
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** 一次 HTTP 结果：只关心状态码与正文 */
    private data class ApiResponse(val code: Int, val body: String?)

    /**
     * 检查更新。
     *
     * @param currentVersion 当前 App 的 versionName（如 "1.0.0"）
     * @return 检查结果，永不抛异常
     */
    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestRelease()
                ?: return@withContext UpdateResult.Failed("没有找到发布版本")

            // 预发布不算可更新，避免把没做完的版本推给用户
            if (release.prerelease) {
                return@withContext UpdateResult.UpToDate(currentVersion)
            }

            val latest = release.tagName.ifBlank { release.name }
            if (latest.isBlank()) {
                return@withContext UpdateResult.Failed("发布版本缺少标签名")
            }

            if (!VersionComparator.isNewer(currentVersion, latest)) {
                return@withContext UpdateResult.UpToDate(currentVersion)
            }

            val apk = release.apkAsset
            val pageUrl = "$WEB_BASE/$repo/releases/tag/$latest"
            UpdateResult.Available(
                currentVersion = currentVersion,
                latestVersion = latest,
                releaseNotes = release.body.trim(),
                // 没有 apk 附件就跳 Release 网页，至少让用户看得到说明
                apkDownloadUrl = apk?.downloadUrl?.ifBlank { null } ?: pageUrl,
                apkSizeBytes = apk?.size ?: 0L,
                releasePageUrl = pageUrl,
            )
        } catch (e: IOException) {
            UpdateResult.Failed("网络不可用：" + (e.message ?: "连接失败"))
        } catch (e: Exception) {
            UpdateResult.Failed("检查更新失败：" + (e.message ?: "未知错误"))
        }
    }

    /**
     * 取最新 Release。
     *
     * 先请求 `/releases/latest`；若该接口 404（部分仓库未开放，或压根没发过正式版），
     * 退回 `/releases` 列表自己挑第一条非预发布。
     */
    private fun fetchLatestRelease(): GiteeRelease? {
        val direct = request("/repos/$repo/releases/latest")
        if (direct.code == 200 && !direct.body.isNullOrBlank()) {
            return updateJson.decodeFromString<GiteeRelease>(direct.body)
        }
        if (direct.code != 404) throw IOException(hintFor(direct.code))

        val list = request("/repos/$repo/releases?per_page=$FALLBACK_PAGE_SIZE")
        if (list.code != 200 || list.body.isNullOrBlank()) throw IOException(hintFor(list.code))

        val releases = updateJson.decodeFromString<List<GiteeRelease>>(list.body)
        return releases.firstOrNull { !it.prerelease } ?: releases.firstOrNull()
    }

    /** 发一次 GET，不把非 2xx 当异常，交给调用方判断 */
    private fun request(path: String): ApiResponse {
        val req = Request.Builder()
            .url(API_BASE + path)
            .header("User-Agent", "pay-dose-android")
            .header("Accept", "application/json")
            .build()

        client.newCall(req).execute().use { response ->
            val body = if (response.isSuccessful) response.body?.string() else null
            return ApiResponse(response.code, body)
        }
    }

    /** 状态码 → 用户看得懂的中文提示 */
    private fun hintFor(code: Int): String = when (code) {
        401, 403 -> "查询被拒绝：仓库可能未公开，或请求过于频繁"
        404 -> "仓库还没有发布版本（或仓库未公开）"
        else -> "服务返回 $code"
    }

    /**
     * 打开 APK 下载地址（交给浏览器）。
     *
     * 之所以不在 App 内下载，是为了不引入「安装未知来源应用」权限
     * 与 FileProvider 配置——那套流程在国产 ROM 上兼容性坑很多。
     * 浏览器下载后用户直接点安装，体验损失很小，但稳定性高得多。
     */
    fun openDownload(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
