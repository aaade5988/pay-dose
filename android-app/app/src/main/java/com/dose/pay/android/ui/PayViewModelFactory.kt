package com.dose.pay.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dose.pay.android.data.AppContainer
import com.dose.pay.android.ui.calc.CalcViewModel
import com.dose.pay.android.ui.cardedit.CardEditViewModel
import com.dose.pay.android.ui.cards.CardsViewModel
import com.dose.pay.android.ui.dashboard.DashboardViewModel
import com.dose.pay.android.ui.flow.FlowViewModel
import com.dose.pay.android.ui.profile.ProfileViewModel

/**
 * 手写 ViewModel 工厂：从 AppContainer 取出 Repository 注入各个 ViewModel。
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
            modelClass.isAssignableFrom(FlowViewModel::class.java) -> FlowViewModel(repo, container.events)
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(container.settings, repo)
            else -> throw IllegalArgumentException("未知的 ViewModel：" + modelClass.name)
        }
        return vm as T
    }
}
