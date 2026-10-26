package com.dose.pay.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dose.pay.android.ui.theme.PayColors
import com.dose.pay.android.ui.theme.PayDoseTheme

/**
 * 单 Activity + Compose 的入口。导航图在 M3 起替换这里的占位内容。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PayDoseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PayColors.Bg) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("pay-dose", color = PayColors.Text)
                    }
                }
            }
        }
    }
}
