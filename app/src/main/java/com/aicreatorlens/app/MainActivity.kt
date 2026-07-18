package com.aicreatorlens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.engine.Presets
import com.aicreatorlens.app.ui.screens.CameraScreen
import com.aicreatorlens.app.ui.screens.DebugPanelScreen
import com.aicreatorlens.app.ui.theme.CreatorLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreatorLensTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Shared engine params across screens
    var currentParams by remember { mutableStateOf(Presets.CREATOR) }

    NavHost(
        navController = navController,
        startDestination = "camera"
    ) {
        composable("camera") {
            CameraScreen(
                initialParams = currentParams,
                onParamsChanged = { params ->
                    currentParams = params
                },
                onOpenDebugPanel = {
                    navController.navigate("debug_panel")
                }
            )
        }
        composable("debug_panel") {
            DebugPanelScreen(
                initialParams = currentParams,
                onParamsChanged = { params ->
                    currentParams = params
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}