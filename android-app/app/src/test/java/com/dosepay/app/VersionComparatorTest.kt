package com.dosepay.app

import com.dosepay.app.data.update.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本号比较逻辑测试（更新功能的核心判断） */
class VersionComparatorTest {

    @Test
    fun `相同版本不算更新`() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun `补丁版本更高`() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.1"))
        assertTrue(VersionComparator.isNewer("1.0.0", "v1.0.1"))
    }

    @Test
    fun `次版本高于补丁`() {
        assertTrue(VersionComparator.isNewer("1.0.9", "1.1.0"))
    }

    @Test
    fun `主版本最高`() {
        assertTrue(VersionComparator.isNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun `旧版本不提示更新`() {
        assertFalse(VersionComparator.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `带v前缀能正确解析`() {
        assertEquals(listOf(1, 2, 3), VersionComparator.parse("v1.2.3"))
        assertEquals(listOf(1, 2, 3), VersionComparator.parse("V1.2.3"))
        assertEquals(listOf(1, 2, 3), VersionComparator.parse("1.2.3"))
    }

    @Test
    fun `位数不足按0补齐`() {
        assertTrue(VersionComparator.isNewer("1.0", "1.0.1"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0"))
        assertEquals(0, VersionComparator.compare("1.0", "1.0.0"))
    }

    @Test
    fun `带后缀的标签能提取数字`() {
        // "v1.1.0-beta" 这种也能解析出 1.1.0
        assertTrue(VersionComparator.isNewer("1.0.0", "v1.1.0-beta"))
    }

    @Test
    fun `compare 返回值符号正确`() {
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0)
        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0)
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"))
    }
}
