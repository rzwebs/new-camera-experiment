package com.aicreatorlens.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onOpenDebugPanel: () -> Unit,
    initialParams: CreatorEngine = Presets.CREATOR,
    onParamsChanged: (CreatorEngine) -> Unit = {}
) {
    val context = LocalContext.current

    var hasPermission by remember {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        DebugLog.log("APP", "Permission check: ${if (granted) "GRANTED" else "DENIED"}")
        mutableStateOf(granted)
    }

    var currentParams by remember { mutableStateOf(initialParams) }
    var selectedPresetIndex by remember { mutableIntStateOf(1) }
    var comparisonMode by remember { mutableStateOf(false) }

    val cameraManager = remember {
        DebugLog.log("APP", "Creating CameraManager")
        CameraManager(context)
    }
    var cameraStarted by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(true) }

    // SurfaceTexture callback from GL
    val onSurfaceTextureReady: (android.graphics.SurfaceTexture) -> Unit = remember(cameraStarted) {
        { surfaceTexture ->
            DebugLog.log("APP", ">>> SurfaceTexture callback FIRED")
            if (!hasPermission) {
                DebugLog.log("APP", "BLOCKED: no permission yet")
            } else if (cameraStarted) {
                DebugLog.log("APP", "BLOCKED: camera already started")
            } else {
                DebugLog.log("APP", ">>> Calling cameraManager.startCamera()")
                cameraManager.startCamera(surfaceTexture)
                cameraStarted = true
                DebugLog.log("APP", "<<< cameraManager.startCamera() returned")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager.stopCamera() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    DebugLog.log("UI", ">>> AndroidView factory called")
                    DebugLog.log("UI", ">>> Creating GLPreviewView...")
                    val view = GLPreviewView(ctx)
                    DebugLog.log("UI", ">>> GLPreviewView created, setting callbacks")
                    view.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    view.setEngineParams(currentParams)
                    view.onCameraSurfaceTextureReady = { st ->
                        DebugLog.log("UI", ">>> GLPreviewView.onCameraSurfaceTextureReady INVOKED")
                        onSurfaceTextureReady(st)
                    }
                    DebugLog.log("UI", "<<< AndroidView factory done")
                    view
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.setEngineParams(currentParams)
                    view.setComparisonMode(comparisonMode)
                }
            )
        } else {
            DebugLog.log("APP", "Showing permission request screen")
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission required", color = Color.White, fontSize = 18.sp)
                }
            }
        }

        // Permission launcher
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            DebugLog.log("PERM", ">>> Permission result: $granted")
            hasPermission = granted
        }

        if (!hasPermission) {
            LaunchedEffect(Unit) {
                DebugLog.log("PERM", ">>> Launching permission request...")
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // === DEBUG LOG OVERLAY (isolated composable to avoid parent recomposition) ===
        DebugLogOverlay(showDebug = showDebug, onToggle = { showDebug = !showDebug })

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
                        Text(name, color = textColor, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = comparisonMode,
                    onClick = { comparisonMode = !comparisonMode },
                    label = { Text("A/B Compare") },
                    leadingIcon = {
                        Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(18.dp))
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

/**
 * Isolated debug log overlay — its own recomposition scope
 * so log text updates don't trigger parent CameraScreen recomposition.
 */
@Composable
private fun BoxScope.DebugLogOverlay(
    showDebug: Boolean,
    onToggle: () -> Unit
) {
    // Log state lives HERE, not in parent
    var debugLogs by remember { mutableStateOf("loading...") }

    LaunchedEffect(Unit) {
        DebugLog.log("APP", "Log poller LaunchedEffect started")
        while (true) {
            kotlinx.coroutines.delay(500)
            val logs = DebugLog.getLogs()
            debugLogs = if (logs.isEmpty()) "(no logs yet)" else logs.joinToString("\n")
        }
    }

    // Toggle button — always shown at top-right area
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 52.dp, end = 56.dp)
            .background(
                if (showDebug) Color(0xFF44FF44).copy(alpha = 0.8f) else Color(0xFFFF4444).copy(alpha = 0.6f),
                RoundedCornerShape(4.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            if (showDebug) "LOG ON" else "LOG OFF",
            color = Color.Black,
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }

    // Log panel
    if (showDebug) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 52.dp, start = 8.dp, end = 80.dp)
                .fillMaxHeight(0.45f)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                val lines = debugLogs.split("\n")
                lines.forEach { line ->
                    val color = when {
                        line.contains("ERROR") || line.contains("FAILED") || line.contains("BLOCKED")
                                || line.contains("TEST PATTERN") -> Color(0xFFFF4444)
                        line.contains(">>>") -> Color(0xFF44AAFF)
                        line.contains("<<<") || line.contains("done") || line.contains("OK") ||
                                line.contains("OPENED") || line.contains("CONFIGURED") || line.contains("READY") ||
                                line.contains("RECEIVED") || line.contains("magenta") -> Color(0xFF44FF44)
                        else -> Color(0xFFCCCCCC)
                    }
                    Text(
                        text = line,
                        color = color,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        softWrap = true
                    )
                }
            }
        }
    }
}