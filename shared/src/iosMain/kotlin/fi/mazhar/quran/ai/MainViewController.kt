package fi.mazhar.quran.ai

import androidx.compose.ui.window.ComposeUIViewController
import fi.mazhar.quran.ai.layout.UserDefaultsLayoutStorage
import fi.mazhar.quran.ai.ui.App

/** Entry point for the iOS host app: `MainViewControllerKt.MainViewController()`. */
fun MainViewController() = ComposeUIViewController { App(UserDefaultsLayoutStorage()) }
