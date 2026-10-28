package com.dosepay.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dosepay.app.data.AppContainer
import com.dosepay.app.ui.bills.BillsScreen
import com.dosepay.app.ui.bills.BillsViewModel
import com.dosepay.app.ui.calc.CalcScreen
import com.dosepay.app.ui.calc.CalcViewModel
import com.dosepay.app.ui.cardedit.CardEditScreen
import com.dosepay.app.ui.cardedit.CardEditViewModel
import com.dosepay.app.ui.cards.CardsScreen
import com.dosepay.app.ui.cards.CardsViewModel
import com.dosepay.app.ui.dashboard.DashboardScreen
import com.dosepay.app.ui.dashboard.DashboardViewModel
import com.dosepay.app.ui.nav.Routes
import com.dosepay.app.ui.nav.TopLevel
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.update.UpdateDialog
import com.dosepay.app.ui.update.UpdateViewModel
import androidx.compose.runtime.collectAsState

/** 底部导航未选中色 */
private val NavUnselected = Color(0xFF9A96A3)

/**
 * 应用根组件：底部 4 Tab 导航 + NavHost。
 */
@Composable
fun PayDoseRoot(container: AppContainer) {
    val factory = remember(container) { PayViewModelFactory(container) }
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    // 全局提示（各 ViewModel 通过事件总线发出）
    LaunchedEffect(Unit) {
        container.events.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 启动时静默检查更新：发现新版本才弹窗，失败或已是最新都不打扰用户
    val updateVm: UpdateViewModel = viewModel(factory = factory)
    val updateState by updateVm.state.collectAsState()
    LaunchedEffect(Unit) { updateVm.checkSilently() }

    Scaffold(
        containerColor = PayColors.Bg,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = PayColors.Surface, tonalElevation = 0.dp) {
                    TopLevel.entries.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PayColors.Primary,
                                selectedTextColor = PayColors.Primary,
                                unselectedIconColor = NavUnselected,
                                unselectedTextColor = NavUnselected,
                                indicatorColor = PayColors.Secondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALC,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.CALC) {
                val vm: CalcViewModel = viewModel(factory = factory)
                CalcScreen(
                    vm = vm,
                    onGoCards = { navController.navigate(Routes.CARDS) { launchSingleTop = true } },
                    onGoDashboard = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(Routes.CARDS) {
                val vm: CardsViewModel = viewModel(factory = factory)
                CardsScreen(
                    vm = vm,
                    onOpenEditor = { id ->
                        navController.navigate(Routes.CARD_EDIT_ROUTE.replace("{methodId}", (id ?: -1L).toString()))
                    },
                    onGoDashboard = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(factory = factory)
                DashboardScreen(
                    vm = vm,
                    onGoCards = { navController.navigate(Routes.CARDS) { launchSingleTop = true } },
                )
            }

            composable(Routes.BILLS) {
                val vm: BillsViewModel = viewModel(factory = factory)
                BillsScreen(vm = vm)
            }

            composable(
                route = Routes.CARD_EDIT_ROUTE,
                arguments = listOf(
                    navArgument("methodId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
            ) { entry ->
                val vm: CardEditViewModel = viewModel(factory = factory)
                val methodId = entry.arguments?.getLong("methodId") ?: -1L
                CardEditScreen(vm = vm, methodId = methodId, onDone = { navController.popBackStack() })
            }
        }

        // 新版本提示弹窗（覆盖在所有页面之上）
        UpdateDialog(
            state = updateState,
            onDownload = { updateVm.openDownload() },
            onDismiss = { updateVm.dismiss() },
        )
    }
}
