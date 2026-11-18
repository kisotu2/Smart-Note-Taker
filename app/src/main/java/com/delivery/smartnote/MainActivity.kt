package com.delivery.smartnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.delivery.smartnote.ui.SmartNoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val purple = Color(0xFF5B37E8)
            MaterialTheme(colorScheme = lightColorScheme(primary = purple, secondary = Color(0xFF7549ED), background = Color(0xFFEEF0FF), surface = Color.White, onSurface = Color(0xFF15163B))) {
                SmartNoteApp()
            }
        }
    }
}
