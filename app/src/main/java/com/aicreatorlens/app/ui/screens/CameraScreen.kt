package com.aicreatorlens.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.FrameLayout
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
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
    DebugLog.log("APP", "=== CameraScreen composed ===")

    val context = LocalContext.current

    var hasPermission by remember {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        DebugLog.log("APP", "Camera permission: ${if (granted) "GRANTED" else "DENIED"}")
        mutableStateOf(granted)
    }

    var currentParams by remember { mutableStateOf(initialParams) }
    var selectedPresetIndex by remember { mutableIntStateOf(1) }
    var comparisonMode by remember { mutableStateOf(false) }

    val cameraManager = remember {
        DebugLog.log("APP", "CameraManager created")
        CameraManager(context)
    }
    var cameraStarted by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(true) }

    // Hold reference to GLPreviewView for passing camera size
    var glPreviewView by remember { mutableStateOf<com.aicreatorlens.app.gl.GLPreviewView?>(null) }

    // SurfaceTexture callback from GL
    val onSurfaceTextureReady: (android.graphics.SurfaceTexture) -> Unit = remember(cameraStarted) {
        { surfaceTexture ->
            DebugLog.log("APP", ">>> SurfaceTexture callback FIRED!")
            if (!hasPermission) {
                DebugLog.log("APP", "BLOCKED: no camera permission")
            } else if (cameraStarted) {
                DebugLog.log("APP", "BLOCKED: camera already started")
            } else {
                DebugLog.log("APP", ">>> Starting camera with SurfaceTexture...")
                // Wire preview size callback before starting camera
                cameraManager.onPreviewSizeSelected = { w, h ->
                    DebugLog.log("APP", "Camera preview size: ${w}x${h}, passing to GL")
                    glPreviewView?.setCameraPreviewSize(w, h)
                }
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
                    DebugLog.log("UI", ">>> AndroidView factory START")
                    val view = GLPreviewView(ctx)
                    DebugLog.log("UI", "GLPreviewView created")
                    view.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    view.setEngineParams(currentParams)
                    view.onCameraSurfaceTextureReady = { st ->
                        DebugLog.log("UI", ">>> GLPreviewView callback -> onSurfaceTextureReady")
                        onSurfaceTextureReady(st)
                    }
                    glPreviewView = view
                    DebugLog.log("UI", "<<< AndroidView factory DONE")
                    view
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.setEngineParams(currentParams)
                    view.setComparisonMode(comparisonMode)
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission required", color = Color.White, fontSize = 18.sp)
            }
        }

        // Permission launcher
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            DebugLog.log("PERM", "Permission result: $granted")
            hasPermission = granted
        }

        if (!hasPermission) {
            LaunchedEffect(Unit) {
                DebugLog.log("PERM", "Requesting camera permission...")
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // === DEBUG LOG OVERLAY (isolated composable) ===
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
 * Isolated debug overlay with COPY button.
 * Updates to log text only recompose THIS composable, not parent.
 */
@Composable
private fun BoxScope.DebugLogOverlay(
    showDebug: Boolean,
    onToggle: () -> Unit
) {
    var debugLogs by remember { mutableStateOf("waiting for logs...") }
    val context = LocalContext.current
    var copiedToast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            val logs = DebugLog.getLogs()
            debugLogs = if (logs.isEmpty()) "(no logs yet)" else logs.joinToString("\n")
        }
    }

    // Show "copied!" toast
    LaunchedEffect(copiedToast) {
        if (copiedToast) {
            Toast.makeText(context, "LOGS COPIED! Paste anywhere.", Toast.LENGTH_SHORT).show()
            copiedToast = false
        }
    }

    // Button row: LOG ON/OFF + COPY
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 52.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // COPY button
        Box(
            modifier = Modifier
                .background(Color(0xFFFFFF00).copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                .clickable {
                    val allLogs = DebugLog.getLogs().joinToString("\n")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("app_logs", allLogs))
                    copiedToast = true
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy logs",
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("COPY", color = Color.Black, fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }

        // LOG ON/OFF toggle
        Box(
            modifier = Modifier
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
    }

    // Log panel
    if (showDebug) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 80.dp, start = 8.dp, end = 110.dp)
                .fillMaxHeight(0.40f)
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                .padding(6.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                val lines = debugLogs.split("\n")
                lines.forEach { line ->
                    val color = when {
                        line.contains("ERROR") || line.contains("FAILED") || line.contains("BLOCKED")
                                || line.contains("EXCEPTION") || line.contains("WARNING") -> Color(0xFFFF4444)
                        line.contains(">>>") || line.contains("HEARTBEAT") -> Color(0xFF44AAFF)
                        line.contains("<<<") || line.contains("OK") || line.contains("done")
                                || line.contains("COMPLETE") || line.contains("RECEIVED")
                                || line.contains("STREAMING") || line.contains("FIRED") -> Color(0xFF44FF44)
                        else -> Color(0xFFBBBBBB)
                    }
                    Text(
                        text = line,
                        color = color,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        softWrap = true
                    )
                }
            }
        }
    }
}