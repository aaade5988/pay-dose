package com.dose.pay.android.ui.cardedit

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dose.pay.android.core.Methods
import com.dose.pay.android.ui.components.HintText
import com.dose.pay.android.ui.components.NumberPickerDialog
import com.dose.pay.android.ui.components.PayCard
import com.dose.pay.android.ui.components.PrimaryButton
import com.dose.pay.android.ui.components.SegControl
import com.dose.pay.android.ui.theme.PayColors

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
    var showInclusiveHelp by remember { mutableStateOf(false) }

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
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Text,
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (state.form.name.isEmpty()) {
                                Text(
                                    "例如：平安信用卡",
                                    fontSize = 13.sp,
                                    color = PayColors.Care,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            ErrorText(state.errors["name"])

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                Text("类型", fontSize = 14.sp, color = Color(0xFF43404C), modifier = Modifier.padding(bottom = 7.dp))
                SegControl(
                    items = Methods.METHOD_TYPE_LABELS,
                    selectedIndex = state.form.typeIndex,
                    onSelect = { vm.setType(it) },
                )
            }
            ErrorText(state.errors["method_type"])
            if (!state.isCredit) {
                HintText(
                    "当前类型为「" + state.methodType + "」，按即时扣款处理，不采集账单与还款规则",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            EditRow(label = "信用额度") {
                Text("¥", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.TextMuted)
                BasicTextField(
                    value = state.form.creditLimit,
                    onValueChange = { vm.setCreditLimit(it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Text,
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (state.form.creditLimit.isEmpty()) {
                                Text(
                                    "留空 = 不限额",
                                    fontSize = 13.sp,
                                    color = PayColors.Care,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            ErrorText(state.errors["credit_limit"])
        }

        /* 余额说明 */
        PayCard(contentPadding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("🚫", fontSize = 16.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "本页已移除「当前已用金额」字段",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Text,
                    )
                    HintText("余额只在「看板」经记一笔 / 直接更新维护 · 提交时自动回传原值，不会被清零")
                }
            }
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

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                    Text("还款规则", fontSize = 14.sp, color = Color(0xFF43404C), modifier = Modifier.padding(bottom = 7.dp))
                    SegControl(
                        items = listOf("固定还款日", "账单日后 N 天"),
                        selectedIndex = if (state.form.dueMode == DueMode.AFTER) 1 else 0,
                        onSelect = { vm.setDueMode(if (it == 1) DueMode.AFTER else DueMode.FIXED) },
                    )
                }

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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("账单日当天计入本期", fontSize = 14.sp, color = Color(0xFF43404C))
                        Text(
                            "ⓘ",
                            fontSize = 13.sp,
                            color = PayColors.Primary,
                            modifier = Modifier.clickable { showInclusiveHelp = true },
                        )
                    }
                    Switch(
                        checked = state.form.statementDayInclusive,
                        onCheckedChange = { vm.setStatementDayInclusive(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PayColors.Primary,
                        ),
                    )
                }
                HintText("开启后账单日当天消费计入本期（还款更早）；关闭则算下一期（占用更久）")
            }
        }

        /* ③ 操作 */
        PrimaryButton(
            text = if (state.saving) "保存中…" else "保存",
            enabled = !state.saving,
            modifier = Modifier.padding(top = 6.dp),
        ) { vm.save(onDone) }

        Text(
            "取消",
            fontSize = 13.sp,
            color = PayColors.TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDone() }
                .padding(vertical = 4.dp),
        )
        HintText("名称需唯一 · 类型非信用卡时规则组隐藏 · 校验失败行内报错")
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

    /* 帮助弹窗 */
    if (showInclusiveHelp) {
        AlertDialog(
            onDismissRequest = { showInclusiveHelp = false },
            title = { Text("账单日当天计入本期", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "开启后，账单日当天消费计入本期账单（还款更早）；关闭时，账单日当天消费算作下一期（资金占用更久）。",
                    fontSize = 14.sp,
                    color = PayColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInclusiveHelp = false }) {
                    Text("知道了", color = PayColors.Primary, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

/** 数字选择弹窗的目标 */
private enum class PickerTarget { STATEMENT_DAY, DUE_DAY, AFTER_DAYS }

/** 分组标题 */
@Composable
private fun GroupTitle(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
}

/** 表单行：左标签 + 右输入 */
@Composable
private fun EditRow(label: String, trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF43404C))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

/** 选择器行 */
@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF43404C))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
            Text("▾", fontSize = 12.sp, color = PayColors.Care)
        }
    }
}

/** 行内错误 */
@Composable
private fun ErrorText(message: String?) {
    if (message == null) return
    Text(
        message,
        fontSize = 11.sp,
        color = Color(0xFFD0342C),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
