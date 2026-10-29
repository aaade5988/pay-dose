package com.dosepay.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 指示器最短展示时长（毫秒）：太短的话用户根本看不见 */
private const val MIN_SPIN_MS = 400L

/** 兜底上限：业务侧卡住时，指示器最多转到这个时长 */
private const val MAX_SPIN_MS = 5000L

/**
 * 下拉刷新容器（卡片页 / 看板页 / 账单页共用）。
 *
 * [onRefresh] 是 **suspend** 的：组件直接等它跑完，再保证指示器至少转了
 * [MIN_SPIN_MS] 才收起。调用方必须把真正取数的那一步写在 lambda 里
 * （`onRefresh = { vm.load() }`，`load` 为 suspend），不要用 `launch` 包一层。
 *
 * 为什么不按「业务侧传 isRefreshing 进来」来收指示器（这是老写法，会转满 5 秒）：
 *
 * 纯本地 Room 查询经常在**一帧之内**完成，业务侧 `refreshing` 在同一帧里
 * true → false。StateFlow 是 conflated 的，Compose 重组时只读到最后那个
 * false，`LaunchedEffect(isRefreshing)` 的 key 根本没变过，于是永远收不到
 * 「刷新结束」这个边沿，指示器只能等兜底超时才停——数据越少、查得越快，
 * 越容易卡满 5 秒。改成直接 await 异步任务，就不存在信号丢失。
 *
 * 同时这也是 material3 1.3.0 `PullToRefreshState.isRefreshing` 为 private set
 * 那个坑的绕法：转不转由本组件自己持有，业务侧不碰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayPullToRefresh(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    val refresh = onRefresh

    var spinning by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }

    PullToRefreshBox(
        isRefreshing = spinning,
        onRefresh = {
            if (!spinning) {
                spinning = true
                startedAt = System.currentTimeMillis()
                scope.launch {
                    try {
                        withTimeoutOrNull(MAX_SPIN_MS) { refresh() }
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (elapsed < MIN_SPIN_MS) delay(MIN_SPIN_MS - elapsed)
                    } finally {
                        spinning = false
                    }
                }
            }
        },
        // 必须撑满可用高度：默认 wrap 的话，内容不满一屏时下拉手势
        // 只有落在内容那一小块区域才生效，看起来就像「下拉刷新没了」
        modifier = Modifier.fillMaxSize().then(modifier),
        state = state,
    ) {
        content()
    }
}
