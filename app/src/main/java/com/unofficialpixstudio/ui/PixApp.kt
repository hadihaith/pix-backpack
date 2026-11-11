package com.unofficialpixstudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.animation.AnimationScreen
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.BleScannerScreen
import com.unofficialpixstudio.ble.DeveloperScreen
import com.unofficialpixstudio.draw.DrawScreen
import com.unofficialpixstudio.image.PictureScreen
import com.unofficialpixstudio.ui.components.PixIcons
import com.unofficialpixstudio.ui.home.HomeScreen
import com.unofficialpixstudio.ui.options.OptionsScreen

private enum class Tab(val label: String) {
    Home("Home"), Picture("Picture"), Draw("Draw"), Animate("Animate"), Options("Options")
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Home -> Icons.Filled.Home
    Tab.Picture -> PixIcons.Heart
    Tab.Draw -> PixIcons.Pencil
    Tab.Animate -> PixIcons.Film
    Tab.Options -> Icons.Filled.Settings
}

/**
 * App shell. No backpack yet: the find-your-backpack screen. Otherwise: bottom navigation
 * with Home, Picture, Draw, Animate and Options; Developer tools open full-screen from Options.
 */
@Composable
fun PixApp(viewModel: BackpackViewModel = viewModel()) {
    val device by viewModel.device.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var developerOpen by rememberSaveable { mutableStateOf(false) }

    if (device == null) {
        Scaffold(Modifier.fillMaxSize()) { padding ->
            BleScannerScreen(
                onDeviceClick = {
                    tab = Tab.Home
                    developerOpen = false
                    viewModel.selectDevice(it)
                },
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    if (developerOpen) {
        Scaffold(Modifier.fillMaxSize()) { padding ->
            DeveloperScreen(
                viewModel = viewModel,
                onBack = { developerOpen = false },
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    // Back from another tab returns Home before leaving the app.
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon(), contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> HomeScreen(
                viewModel,
                onOpenPicture = { tab = Tab.Picture },
                onOpenDraw = { tab = Tab.Draw },
                onOpenAnimations = { tab = Tab.Animate },
                modifier = content,
            )
            Tab.Picture -> PictureScreen(viewModel, modifier = content)
            Tab.Draw -> DrawScreen(viewModel, modifier = content)
            Tab.Animate -> AnimationScreen(viewModel, modifier = content)
            Tab.Options -> OptionsScreen(viewModel, onOpenDeveloper = { developerOpen = true }, modifier = content)
        }
    }
}
