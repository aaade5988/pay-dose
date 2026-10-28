package com.dosepay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dosepay.app.ui.PayDoseRoot
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.theme.PayDoseTheme

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
