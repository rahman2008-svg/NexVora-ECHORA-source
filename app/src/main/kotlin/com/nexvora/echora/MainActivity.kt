/*
 * file:    MainActivity.kt
 * desc:    NexVora ECHORA entry point. Hosts the ECHORA shell (Home/Studio/Library/Premium/Settings),
 *          which embeds Resound's editor, multitrack and library screens unchanged.
 */
package com.nexvora.echora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexvora.echora.ui.echora.EchoraRoot
import com.nexvora.echora.ui.theme.ResoundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ResoundApp).container
        setContent { ResoundTheme { EchoraRoot(container) } }
    }
}
