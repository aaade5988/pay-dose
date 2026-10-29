package com.dosepay.app.ui.cardedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosepay.app.ui.components.HintText
import com.dosepay.app.ui.components.NumberPickerDialog
import com.dosepay.app.ui.components.PayCard
import com.dosepay.app.ui.components.PrimaryButton
import com.dosepay.app.ui.theme.PayColors

/**
 * 支付方式新增 / 编辑页。
 */
@Composable
fun CardEditScreen(
    vm: CardEditViewModel,
    methodId: Long,
    onDone: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    LaunchedEffect(methodId) { vm.load(methodId) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        /* 顶部标题栏 */
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回：用图标 + 圆形浅底 + 34dp 触控区，替代纯文本「‹」
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PayColors.Primary.copy(alpha = 0.10f))
                    .clickable { onDone() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = PayColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (state.isEdit) "编辑支付方式" else "新增支付方式",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PayColors.Text,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                Text("正在加载…", fontSize = 13.sp, color = PayColors.Hint)
            }
            return@Column
        }

        /* 表单级错误 */
        if (state.formError.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFFFF6F1))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠️", fontSize = 12.sp)
                Text(
                    state.formError,
                    fontSize = 12.sp,
                    color = PayColors.AccentText,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        /* ① 基础信息 */
        PayCard(contentPadding = 12.dp) {
            GroupTitle("基础信息")

            EditRow(label = "名称") {
                BasicTextField(
                    value = state.form.name,
                    onValueChange = { vm.setName(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Text,
                        textAlign = TextAlign.End,
                    ),
                    decorationBox = { inner ->
                        // inner 自身是 fillMaxWidth 的，placeholder 必须用同一套对齐才能跟输入文字严格同一条右边界
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            if (state.form.name.isEmpty()) {
                                Text(
                                    "未命名",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PayColors.Care,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            ErrorText(state.errors["name"])

            ReadOnlyRow(label = "类型", value = state.methodType)
            ErrorText(state.errors["method_type"])
            if (!state.isCredit) {
                HintText(
                    "当前类型为「" + state.methodType + "」，按即时扣款处理，不采集账单与还款规则",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            EditRow(label = "信用额度") {
                BasicTextField(
                    value = state.form.creditLimit,
                    onValueChange = { vm.setCreditLimit(it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Text,
                        textAlign = TextAlign.End,
                    ),
                    decorationBox = { inner ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // weight 区把右侧留给单位，inner 填满该区后数字右对齐，正好贴着「元」
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 3.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                if (state.form.creditLimit.isEmpty()) {
                                    Text(
                                        "0",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PayColors.Care,
                                    )
                                }
                                inner()
                            }
                            Text(
                                " 元",
                                fontSize = 12.sp,
                                color = PayColors.TextMuted,
                            )
                        }
                    },
                )
            }
            ErrorText(state.errors["credit_limit"])
        }

        /* ② 账单与还款规则（仅信用卡） */
        if (state.isCredit) {
            PayCard(contentPadding = 12.dp) {
                GroupTitle("账单与还款规则")

                PickerRow(
                    label = "账单日",
                    value = "每月 " + state.form.statementDay + " 日",
                    onClick = { picker = PickerTarget.STATEMENT_DAY },
                )
                ErrorText(state.errors["statement_day"])

                ReadOnlyRow(
                    label = "还款规则",
                    value = if (state.form.dueMode == DueMode.AFTER) "账单日后 N 天" else "固定还款日",
                )

                if (state.form.dueMode == DueMode.FIXED) {
                    PickerRow(
                        label = "还款日",
                        value = "每月 " + state.form.dueDay + " 日",
                        onClick = { picker = PickerTarget.DUE_DAY },
                    )
                    ErrorText(state.errors["due_day"])
                } else {
                    PickerRow(
                        label = "账单日后",
                        value = state.form.dueDaysAfterStatement.toString() + " 天",
                        onClick = { picker = PickerTarget.AFTER_DAYS },
                    )
                    ErrorText(state.errors["due_days_after_statement"])
                }
            }
        }

        /* ③ 操作 */
        PrimaryButton(
            text = if (state.saving) "保存中…" else "保存",
            enabled = !state.saving,
            modifier = Modifier.padding(top = 6.dp),
        ) { vm.save(onDone) }
    }

    /* 数字选择弹窗 */
    when (picker) {
        PickerTarget.STATEMENT_DAY -> NumberPickerDialog(
            title = "账单日",
            range = 1..31,
            selected = state.form.statementDay,
            label = { "每月 " + it + " 日" },
            onDismiss = { picker = null },
            onPicked = {
                vm.setStatementDay(it)
                picker = null
            },
        )

        PickerTarget.DUE_DAY -> NumberPickerDialog(
            title = "还款日",
            range = 1..31,
            selected = state.form.dueDay,
            label = { "每月 " + it + " 日" },
            onDismiss = { picker = null },
            onPicked = {
                vm.setDueDay(it)
                picker = null
            },
        )

        PickerTarget.AFTER_DAYS -> NumberPickerDialog(
            title = "账单日后还款天数",
            range = 1..60,
            selected = state.form.dueDaysAfterStatement,
            label = { "账单日后 " + it + " 天" },
            onDismiss = { picker = null },
            onPicked = {
                vm.setDueDaysAfterStatement(it)
                picker = null
            },
        )

        null -> Unit
    }
}

/** 数字选择弹窗的目标 */
private enum class PickerTarget { STATEMENT_DAY, DUE_DAY, AFTER_DAYS }
