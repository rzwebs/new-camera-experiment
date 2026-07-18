package com.aicreatorlens.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aicreatorlens.app.camera.CameraManager
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.engine.Presets
import com.aicreatorlens.app.gl.GLPreviewView

/**
 * In-memory debug log overlay — shows camera + GL pipeline status on screen.
 */
object DebugOverlay {
    private val logs = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var visible = true

    fun log(tag: String, msg: String) {
        val line = "[$tag] $msg"
        logs.add(line)
        while (logs.size > 25) logs.poll()
    }

    fun getLogs(): List<String> = logs.toList()

    fun toggle() { visible = !visible }
    fun isVisible(): Boolean = visible
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onOpenDebugPanel: () -> Unit,
    initialParams: CreatorEngine = Presets.CREATOR,
    onParamsChanged: (CreatorEngine) -> Unit = {}
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var currentParams by remember { mutableStateOf(initialParams) }
    var selectedPresetIndex by remember { mutableIntStateOf(1) } // Creator = index 1
    var comparisonMode by remember { mutableStateOf(false) }

    // Raw Camera2 manager — no ProcessCameraProvider
    val cameraManager = remember { CameraManager(context) }
    var cameraStarted by remember { mutableStateOf(false) }

    // Debug overlay state
    val debugLogs = remember { mutableStateOf(listOf<String>()) }
    var showDebug by remember { mutableStateOf(true) }

    // Collect logs periodically from all sources
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val allLogs = mutableListOf<String>()
            allLogs.addAll(cameraManager.getLogMessages())
            // GLRenderer logs are collected from the view
            debugLogs.value = allLogs
        }
    }

    // Callback when GL creates camera SurfaceTexture
    val onSurfaceTextureReady: (android.graphics.SurfaceTexture) -> Unit = remember {
        { surfaceTexture ->
            if (hasPermission && !cameraStarted) {
                DebugOverlay.log("CAM", "SurfaceTexture received, starting Camera2...")
                cameraManager.startCamera(surfaceTexture)
                cameraStarted = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopCamera()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    DebugOverlay.log("UI", "Creating GLPreviewView")
                    GLPreviewView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setEngineParams(currentParams)
                        onCameraSurfaceTextureReady = { st ->
                            DebugOverlay.log("UI", "GLPreviewView → SurfaceTexture callback!")
                            onSurfaceTextureReady(st)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.setEngineParams(currentParams)
                    view.setComparisonMode(comparisonMode)
                    // Collect GL + EGL logs from renderer
                    val rendererLogs = view.getRenderer()?.getLogMessages() ?: emptyList()
                    if (rendererLogs.isNotEmpty()) {
                        val allLogs = mutableListOf<String>()
                        allLogs.addAll(rendererLogs)
                        allLogs.addAll(cameraManager.getLogMessages())
                        debugLogs.value = allLogs
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission required", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { }) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Permission launcher
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
            DebugOverlay.log("UI", "Permission result: $granted")
        }

        if (!hasPermission) {
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Debug overlay — tap to toggle
        if (showDebug && debugLogs.value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 56.dp, start = 8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(6.dp)
                    .clickable { showDebug = false }
            ) {
                Column {
                    debugLogs.value.forEach { line ->
                        val color = if (line.contains("ERROR") || line.contains("FAILED")) {
                            Color(0xFFFF4444)
                        } else if (line.contains("SUCCESS") || line.contains("OPENED") ||
                                   line.contains("CONFIGURED") || line.contains("READY") ||
                                   line.contains("rendered") || line.contains("delivered")) {
                            Color(0xFF44FF44)
                        } else {
                            Color(0xFFCCCCCC)
                        }
                        Text(
                            text = line,
                            color = color,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        } else if (!showDebug && debugLogs.value.isNotEmpty()) {
            // Small "LOG" button to re-enable
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 56.dp, start = 8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable { showDebug = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("LOG", color = Color.White.copy(0.6f), fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "AI Creator Lens",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.background(
                    Color.Black.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ).padding(horizontal = 12.dp, vertical = 6.dp)
            )

            IconButton(
                onClick = onOpenDebugPanel,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Debug Panel",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Preset selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Presets.ALL.forEachIndexed { index, (name, _) ->
                    val isSelected = index == selectedPresetIndex
                    val bgColor by animateColorAsState(
                        if (isSelected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f),
                        label = "preset_bg_$index"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                        label = "preset_text_$index"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = bgColor,
                        modifier = Modifier.clickable {
                            selectedPresetIndex = index
                            val newParams = Presets.ALL[index].second
                            currentParams = newParams
                            onParamsChanged(newParams)
                        }
                    ) {
                        Text(
                            name,
                            color = textColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Comparison toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = comparisonMode,
                    onClick = { comparisonMode = !comparisonMode },
                    label = { Text("A/B Compare") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White.copy(alpha = 0.9f),
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
    }
}