package com.hikmah.quran

import androidx.compose.ui.window.ComposeUIViewController
import com.hikmah.quran.layout.UserDefaultsLayoutStorage
import com.hikmah.quran.ui.App

/** Entry point for the iOS host app: `MainViewControllerKt.MainViewController()`. */
fun MainViewController() = ComposeUIViewController { App(UserDefaultsLayoutStorage()) }
