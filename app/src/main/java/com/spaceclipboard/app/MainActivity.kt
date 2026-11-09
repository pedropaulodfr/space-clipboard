package com.spaceclipboard.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.spaceclipboard.app.ui.MainViewModel
import com.spaceclipboard.app.ui.MainViewModelFactory
import com.spaceclipboard.app.ui.SpaceApp
import com.spaceclipboard.app.ui.theme.SpaceClipboardTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as SpaceClipboardApplication
        MainViewModelFactory(
            container = app.container,
            hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SpaceClipboardTheme {
                SpaceApp(viewModel)
            }
        }
    }
}
