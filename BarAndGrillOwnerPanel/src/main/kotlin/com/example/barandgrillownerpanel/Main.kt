@file:Suppress("SpellCheckingInspection", "DEPRECATION")

package com.example.barandgrillownerpanel

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.barandgrillownerpanel.ui.theme.BarAndGrillPOSTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.example.barandgrillownerpanel.ui.main.MainApp
import com.example.barandgrillownerpanel.utils.Logger

fun main() = application {
    val errorState = androidx.compose.runtime.mutableStateOf<Throwable?>(null)

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        Logger.fatal("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
        errorState.value = throwable
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = AppFlavor.current.windowTitle,
        state = WindowState(placement = WindowPlacement.Maximized),
        icon = androidx.compose.ui.res.painterResource(AppFlavor.current.iconResource)
    ) {
        BarAndGrillPOSTheme {
            MainApp()
        }
    }
}
