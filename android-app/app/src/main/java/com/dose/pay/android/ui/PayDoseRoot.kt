package com.dose.pay.android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.dose.pay.android.data.AppContainer
import com.dose.pay.android.ui.bills.BillsScreen
import com.dose.pay.android.ui.bills.BillsViewModel
import com.dose.pay.android.ui.calc.CalcScreen
import com.dose.pay.android.ui.calc.CalcViewModel
import com.dose.pay.android.ui.cardedit.CardEditScreen
import com.dose.pay.android.ui.cardedit.CardEditViewModel
import com.dose.pay.android.ui.cards.CardsScreen
import com.dose.pay.android.ui.cards.CardsViewModel
import com.dose.pay.android.ui.dashboard.DashboardScreen
import com.dose.pay.android.ui.dashboard.DashboardViewModel
import com.dose.pay.android.ui.nav.Routes
import com.dose.pay.android.ui.nav.TopLevel
import com.dose.pay.android.ui.theme.PayColors

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
    }
}
