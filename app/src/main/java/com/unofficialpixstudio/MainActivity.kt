package com.unofficialpixstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.unofficialpixstudio.ble.BleScannerScreen
import com.unofficialpixstudio.ui.theme.UnofficialPixStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnofficialPixStudioTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleScannerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
