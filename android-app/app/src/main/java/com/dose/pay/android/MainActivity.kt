package com.dose.pay.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dose.pay.android.ui.PayDoseRoot
import com.dose.pay.android.ui.theme.PayColors
import com.dose.pay.android.ui.theme.PayDoseTheme

/**
 * 单 Activity + Compose 的入口。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PayDoseApp).container
        setContent {
            PayDoseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PayColors.Bg) {
                    PayDoseRoot(container = container)
                }
            }
        }
    }
}
