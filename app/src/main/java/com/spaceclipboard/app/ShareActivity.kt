package com.spaceclipboard.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.spaceclipboard.app.ui.share.ShareScreen
import com.spaceclipboard.app.ui.share.ShareViewModel
import com.spaceclipboard.app.ui.share.ShareViewModelFactory
import com.spaceclipboard.app.ui.theme.SpaceClipboardTheme
import com.spaceclipboard.app.util.sharedText

class ShareActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModels {
        val app = application as SpaceClipboardApplication
        ShareViewModelFactory(
            container = app.container,
            text = intent.sharedText(),
            hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SpaceClipboardTheme {
                ShareScreen(
                    viewModel = viewModel,
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}
