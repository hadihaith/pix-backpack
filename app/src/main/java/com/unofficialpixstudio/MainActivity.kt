package com.unofficialpixstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unofficialpixstudio.ui.PixApp
import com.unofficialpixstudio.ui.theme.UnofficialPixStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnofficialPixStudioTheme {
                PixApp()
            }
        }
    }
}
