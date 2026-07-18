package com.aicreatorlens.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicreatorlens.app.engine.CreatorEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugPanelScreen(
    initialParams: CreatorEngine,
    onParamsChanged: (CreatorEngine) -> Unit,
    onNavigateBack: () -> Unit
) {
    var params by remember { mutableStateOf(initialParams) }

    LaunchedEffect(params) {
        onParamsChanged(params)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creator Engine Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Group: Tone Mapping
            SectionHeader("Tone Mapping")
            ParamSlider(
                label = "Tone Mapping",
                value = params.toneMappingStrength,
                range = 0f..1f,
                onValueChange = { params = params.copy(toneMappingStrength = it) }
            )
            ParamSlider(
                label = "Exposure",
                value = params.exposure,
                range = -2f..2f,
                onValueChange = { params = params.copy(exposure = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Group: Color Science
            SectionHeader("Color Science")
            ParamSlider(
                label = "Contrast Curve",
                value = params.contrastCurve,
                range = -1f..1f,
                onValueChange = { params = params.copy(contrastCurve = it) }
            )
            ParamSlider(
                label = "Color Temperature",
                value = params.colorTemperature,
                range = 0f..1f,
                onValueChange = { params = params.copy(colorTemperature = it) }
            )
            ParamSlider(
                label = "Saturation",
                value = params.saturation,
                range = 0f..2f,
                onValueChange = { params = params.copy(saturation = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Group: Dynamic Range
            SectionHeader("Dynamic Range")
            ParamSlider(
                label = "Highlight Roll-off",
                value = params.highlightRolloff,
                range = 0f..1f,
                onValueChange = { params = params.copy(highlightRolloff = it) }
            )
            ParamSlider(
                label = "Shadow Recovery",
                value = params.shadowRecovery,
                range = 0f..1f,
                onValueChange = { params = params.copy(shadowRecovery = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Group: Face / Skin
            SectionHeader("Face / Skin")
            ParamSlider(
                label = "Skin Protection",
                value = params.skinProtection,
                range = 0f..1f,
                onValueChange = { params = params.copy(skinProtection = it) }
            )
            ParamSlider(
                label = "Skin Softness",
                value = params.skinSoftness,
                range = 0f..1f,
                onValueChange = { params = params.copy(skinSoftness = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Group: Background
            SectionHeader("Background")
            ParamSlider(
                label = "Background Contrast",
                value = params.backgroundContrast,
                range = -1f..1f,
                onValueChange = { params = params.copy(backgroundContrast = it) }
            )
            ParamSlider(
                label = "Background Saturation",
                value = params.backgroundSaturation,
                range = 0f..2f,
                onValueChange = { params = params.copy(backgroundSaturation = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Group: Cinematic Effects
            SectionHeader("Cinematic Effects")
            ParamSlider(
                label = "Film Grain",
                value = params.filmGrain,
                range = 0f..1f,
                onValueChange = { params = params.copy(filmGrain = it) }
            )
            ParamSlider(
                label = "Glow",
                value = params.glow,
                range = 0f..1f,
                onValueChange = { params = params.copy(glow = it) }
            )
            ParamSlider(
                label = "Sharpness",
                value = params.sharpness,
                range = 0f..1f,
                onValueChange = { params = params.copy(sharpness = it) }
            )
            ParamSlider(
                label = "Vignette",
                value = params.vignette,
                range = 0f..1f,
                onValueChange = { params = params.copy(vignette = it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Reset button
            OutlinedButton(
                onClick = {
                    params = initialParams
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Preset")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Color(0xFF90CAF9),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.width(140.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF90CAF9)
            )
        )
        Text(
            String.format("%.2f", value),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(44.dp)
        )
    }
}