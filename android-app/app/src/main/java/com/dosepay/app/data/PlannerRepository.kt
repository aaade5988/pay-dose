package com.dosepay.app.data

import android.content.Context
import com.dosepay.app.data.local.LocalPlannerService
import com.dosepay.app.data.local.PayDoseDatabase
import com.dosepay.app.data.local.SeedImporter

/** 业务错误：携带错误码与中文 detail（与后端契约保持一致，界面可直接 toast） */
class ApiError(val status: Int, override val message: String) : Exception(message)

/**
 * 数据仓储。
 *
 * 改造说明：原实现走 Retrofit 调 Spring Boot，现在直接读写本地 Room 数据库。
 * **对外方法签名一个都没变**，所以全部 ViewModel 与 UI 无需任何修改。
 *
 * 错误仍以 [ApiError] 抛出，消息就是中文 detail，界面提示与联网版一致。
 */
class PlannerRepository(
    private val service: LocalPlannerService,
) {

    suspend fun listMethods(): List<MethodDto> = service.listMethods()

    suspend fun createMethod(body: MethodInput): MethodDto = service.createMethod(body)

    suspend fun updateMethod(id: Long, body: MethodInput): MethodDto = service.updateMethod(id, body)

    suspend fun deleteMethod(id: Long) {
        service.deleteMethod(id)
    }

    /** 标记 / 取消「已还清」：只改标记位，不写流水、不动已用余额 */
    suspend fun setMethodSettled(id: Long, settled: Boolean): MethodDto =
        service.setSettled(id, settled)

    /** 账单分期：给某张卡的某一期账单安排分期，不写流水、不动已用余额 */
    suspend fun createInstallment(body: InstallmentInput): InstallmentDto =
        service.createInstallment(body)

    /** 取消账单分期 */
    suspend fun deleteInstallment(methodId: Long, cycleEnd: String) {
        service.deleteInstallment(methodId, cycleEnd)
    }

    /** 某张卡的全部分期计划 */
    suspend fun listInstallments(methodId: Long): List<InstallmentDto> =
        service.listInstallments(methodId)

    suspend fun recommend(request: RecommendRequest): List<RecItem> = service.recommend(request)

    /** 看板：按账单周期聚合（上一还款日 → 本还款日） */
    suspend fun cycleSummary(): CycleSummaryDto = service.cycleSummary()

    suspend fun createTransaction(body: TxnInput): TxnDto = service.createTransaction(body)

    suspend fun listTransactions(methodId: Long?, limit: Int, offset: Int): List<TxnDto> =
        service.listTransactions(methodId, limit, offset)

    /** 删除流水，返回删除后该卡已用金额 */
    suspend fun deleteTransaction(id: Long): String = service.deleteTransaction(id)
}

/**
 * 手写依赖容器（不引入 Hilt，减少配置风险）。
 * 所有 ViewModel 通过它拿到 Repository。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** 全局提示事件总线 */
    val events = UiEventBus()

    private val database = PayDoseDatabase.get(appContext)

    private val localService = LocalPlannerService(database)

    val repository: PlannerRepository = PlannerRepository(localService)

    /** 更新检查器（走 Gitee Releases API，独立于本地数据层） */
    val updateChecker = com.dosepay.app.data.update.UpdateChecker(appContext)

    /**
     * 首次启动时把内置的初始数据写入数据库（幂等：库里已有数据就跳过）。
     *
     * 这一步取代了「用户自己跑 Spring Boot」——数据随 App 一起来。
     */
    suspend fun seedIfNeeded() {
        SeedImporter.importIfEmpty(appContext, database)
    }
}
