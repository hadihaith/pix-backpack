package com.unofficialpixstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.unofficialpixstudio.ble.BackpackScreen
import com.unofficialpixstudio.ble.BleDevice
import com.unofficialpixstudio.ble.BleScannerScreen
import com.unofficialpixstudio.ui.theme.UnofficialPixStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnofficialPixStudioTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Tapping a scan result opens it; leaving the backpack screen disconnects.
                    var selectedDevice by remember { mutableStateOf<BleDevice?>(null) }
                    val device = selectedDevice
                    if (device == null) {
                        BleScannerScreen(
                            onDeviceClick = { selectedDevice = it },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        BackpackScreen(
                            device = device,
                            onBack = { selectedDevice = null },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
