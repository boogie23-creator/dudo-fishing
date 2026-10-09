package com.dudo.fishing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dudo.fishing.ui.DetailScreen
import com.dudo.fishing.ui.DudoTheme
import com.dudo.fishing.ui.AddLogScreen
import com.dudo.fishing.ui.AppShell
import com.dudo.fishing.ui.MainViewModel
import com.dudo.fishing.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 바다색 헤더가 상태바 뒤까지 이어지도록 (상태바 아이콘은 흰색)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            DudoTheme {
                val vm: MainViewModel = viewModel()
                // 아래 탭: home / map / log / more
                var tab by remember { mutableStateOf("home") }
                // 탭 위에 덮는 화면: settings / addlog / point:<id>
                var overlay by remember { mutableStateOf<String?>(null) }
                BackHandler(enabled = overlay != null || tab != "home") { if (overlay != null) overlay = null else tab = "home" }
                val ov = overlay
                when {
                    ov == null -> AppShell(vm, tab, onTab = { tab = it }, onOpen = { overlay = "point:$it" },
                        onAddLog = { overlay = "addlog" }, onSettings = { overlay = "settings" })
                    ov == "settings" -> SettingsScreen(vm.settings, onDone = { overlay = null; vm.refresh() })
                    ov == "addlog" -> AddLogScreen(vm, onBack = { overlay = null })
                    else -> DetailScreen(vm, pointId = ov.removePrefix("point:"), onBack = { overlay = null }, onOpen = { overlay = "point:$it" })
                }
            }
        }
    }
}
