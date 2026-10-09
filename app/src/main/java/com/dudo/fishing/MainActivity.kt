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
import com.dudo.fishing.ui.HomeScreen
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
                // 간단한 화면 전환: "home" / "settings" / 포인트 id (디자인 바꾸기 전 배치)
                var screen by remember { mutableStateOf("home") }
                BackHandler(enabled = screen != "home") { screen = "home" }
                when (screen) {
                    "home" -> HomeScreen(vm, onOpen = { screen = it }, onSettings = { screen = "settings" })
                    "settings" -> SettingsScreen(vm.settings, onDone = { screen = "home"; vm.refresh() })
                    else -> DetailScreen(vm, pointId = screen, onBack = { screen = "home" }, onOpen = { screen = it })
                }
            }
        }
    }
}
