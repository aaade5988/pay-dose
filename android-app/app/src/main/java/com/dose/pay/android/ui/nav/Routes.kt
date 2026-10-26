package com.dose.pay.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

/** 全局路由表 */
object Routes {
    const val CALC = "calc"
    const val CARDS = "cards"
    const val DASHBOARD = "dashboard"
    const val PROFILE = "profile"
    const val CARD_EDIT = "cardEdit"
    const val FLOW = "flow"

    /** 卡片编辑页（methodId 可空：空 = 新增） */
    const val CARD_EDIT_ROUTE = "cardEdit?methodId={methodId}"
}

/** 底部导航的 4 个顶层页面 */
enum class TopLevel(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    CALC(Routes.CALC, "计算", Icons.Rounded.Calculate),
    CARDS(Routes.CARDS, "卡片", Icons.Rounded.CreditCard),
    DASHBOARD(Routes.DASHBOARD, "看板", Icons.Rounded.Dashboard),
    PROFILE(Routes.PROFILE, "我的", Icons.Rounded.Person),
}
