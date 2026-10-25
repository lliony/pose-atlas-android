package com.lliony.poseatlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.ui.AtlasApp
import com.lliony.poseatlas.ui.AtlasViewModel
import com.lliony.poseatlas.ui.theme.AtlasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AtlasViewModel = hiltViewModel()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val health = getSharedPreferences("health", MODE_PRIVATE)
                if (health.getBoolean("previous_crash", false)) {
                    health.edit().remove("previous_crash").apply()
                    vm.message("上次应用异常退出，已重新加载本机资料")
                }
            }
            val prefs = vm.preferences.collectAsStateWithLifecycle().value
            AtlasTheme(prefs.theme) {
                val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                // 应用内配色可独立于系统主题，系统栏图标也要同步。
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                AtlasApp(vm)
            }
        }
    }
}
