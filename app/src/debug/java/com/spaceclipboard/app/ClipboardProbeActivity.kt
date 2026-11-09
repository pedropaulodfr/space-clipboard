package com.spaceclipboard.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/**
 * Activity mínima usada apenas por testes instrumentados de clipboard.
 * Não é exportada, não tem launcher e não existe no build de release.
 */
class ClipboardProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "Space Clipboard clipboard probe"
                gravity = Gravity.CENTER
                contentDescription = "clipboard-probe"
            },
        )
    }
}
