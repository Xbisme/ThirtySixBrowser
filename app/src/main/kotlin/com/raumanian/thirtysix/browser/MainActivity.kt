package com.raumanian.thirtysix.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.raumanian.thirtysix.browser.presentation.navigation.AppNavGraph
import com.raumanian.thirtysix.browser.ui.theme.ThirdtySixBrowserTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThirdtySixBrowserTheme {
                AppNavGraph()
            }
        }
    }
}
