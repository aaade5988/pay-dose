package com.dosepay.app

import com.dosepay.app.data.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 下载地址白名单测试。
 *
 * 系统签名校验负责「装不上假包」，这里负责「不把用户带去假站点」：
 * 只有 https + Gitee 官方域名才放行，其余一律退回 Release 网页。
 */
class UpdateUrlTest {

    private val realApkUrl =
        "https://gitee.com/mechanical-running-roll-wang/pay-dose/releases/download/v1.0.5/pay-dose-1.0.5.apk"

    @Test
    fun `官方 https 附件地址放行`() {
        assertEquals(realApkUrl, UpdateChecker.trustedDownloadUrl(realApkUrl))
    }

    @Test
    fun `官方子域放行`() {
        val url = "https://files.gitee.com/attach_files/abc.apk"
        assertEquals(url, UpdateChecker.trustedDownloadUrl(url))
    }

    @Test
    fun `明文 http 拒绝`() {
        assertNull(UpdateChecker.trustedDownloadUrl(realApkUrl.replace("https://", "http://")))
    }

    @Test
    fun `非官方域名拒绝`() {
        assertNull(UpdateChecker.trustedDownloadUrl("https://gitee.com.evil.example/pay-dose.apk"))
        assertNull(UpdateChecker.trustedDownloadUrl("https://evil.example/gitee.com/pay-dose.apk"))
        assertNull(UpdateChecker.trustedDownloadUrl("https://notgitee.com/pay-dose.apk"))
    }

    @Test
    fun `userinfo 伪装拒绝`() {
        // URI.host 是 evil.example，不是 gitee.com
        assertNull(UpdateChecker.trustedDownloadUrl("https://gitee.com@evil.example/pay-dose.apk"))
    }

    @Test
    fun `空值与畸形地址拒绝`() {
        assertNull(UpdateChecker.trustedDownloadUrl(null))
        assertNull(UpdateChecker.trustedDownloadUrl(""))
        assertNull(UpdateChecker.trustedDownloadUrl("   "))
        assertNull(UpdateChecker.trustedDownloadUrl("gitee.com/pay-dose.apk"))
        assertNull(UpdateChecker.trustedDownloadUrl("https:///nohost.apk"))
    }

    @Test
    fun `首尾空白被裁剪后仍放行`() {
        assertEquals(realApkUrl, UpdateChecker.trustedDownloadUrl("  $realApkUrl\n"))
    }
}
