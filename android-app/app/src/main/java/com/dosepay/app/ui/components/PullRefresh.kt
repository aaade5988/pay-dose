package com.dosepay.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** 指示器最短展示时长（毫秒）：太短的话用户根本看不见 */
private const val MIN_SPIN_MS = 400L

/** 兜底上限：业务异常没复位时，指示器最多转到这个时长 */
private const val MAX_SPIN_MS = 5000L

/**
 * 下拉刷新容器（卡片页 / 看板页 / 账单页共用）。
 *
 * 为什么不直接用 Material3 的 PullToRefreshBox 默认写法：
 *
 * 1. 纯本地 Room 读数据很快，业务侧 `refreshing` 往往在**同一帧内** true → false。
 *    指示器刚被 fling 停在「刷新中」的位置，就收到 false，于是卡在屏幕上不消失
 *    （老版本 material3 1.3.0 还有个更硬的问题：PullToRefreshState.isRefreshing
 *    是 private set，外部改 false 根本管不到它，必须杀进程才能复位）。
 * 2. 这里由组件自己持有「转不转」这个状态，业务侧只负责给一个事实：
 *    刷新完成了没有。
 *
 * 行为：下拉松手 → 立刻开始转 → 业务完成后再等最短时长才收，
 * 保证指示器既不会一闪而过，也不会永远卡住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    var spinning by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }

    // 业务侧说「刷新结束了」：等最短展示时长再收起
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && spinning) {
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_SPIN_MS) delay(MIN_SPIN_MS - elapsed)
            spinning = false
        }
    }

    // 兜底：业务侧异常没把 refreshing 复位时，强制收起，避免永久卡死
    LaunchedEffect(spinning) {
        if (spinning) {
            delay(MAX_SPIN_MS)
            spinning = false
        }
    }

    PullToRefreshBox(
        isRefreshing = spinning,
        onRefresh = {
            spinning = true
            startedAt = System.currentTimeMillis()
            onRefresh()
        },
        modifier = modifier,
        state = state,
    ) {
        content()
    }
}
