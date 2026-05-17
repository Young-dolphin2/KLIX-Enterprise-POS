package com.example.barandgrillownerpanel

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.example.barandgrillownerpanel.ui.main.MainApp
import com.example.barandgrillownerpanel.ui.theme.BarAndGrillPOSTheme

@Preview
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KLIX Enterprise POS - TEST MODE",
        state = WindowState(placement = WindowPlacement.Maximized)
    ) {
        BarAndGrillPOSTheme {
            MainApp()
        }
    }
}
