package com.dose.pay.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dose.pay.android.data.AppContainer
import com.dose.pay.android.ui.bills.BillsViewModel
import com.dose.pay.android.ui.calc.CalcViewModel
import com.dose.pay.android.ui.cardedit.CardEditViewModel
import com.dose.pay.android.ui.cards.CardsViewModel
import com.dose.pay.android.ui.dashboard.DashboardViewModel

/**
 * 手写 ViewModel 工厂：从 AppContainer 取出 Repository 注入各个 ViewModel。
 *
 * 注：原「消费流水」页（FlowViewModel）已下线，流水明细统一由 Tab4「账单」承载。
 */
class PayViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = container.repository
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(CalcViewModel::class.java) -> CalcViewModel(repo, container.events)
            modelClass.isAssignableFrom(CardsViewModel::class.java) -> CardsViewModel(repo, container.events)
            modelClass.isAssignableFrom(CardEditViewModel::class.java) -> CardEditViewModel(repo, container.events)
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repo, container.events)
            modelClass.isAssignableFrom(BillsViewModel::class.java) -> BillsViewModel(repo, container.events)
            else -> throw IllegalArgumentException("未知的 ViewModel：" + modelClass.name)
        }
        return vm as T
    }
}
