package com.omnisearch.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisearch.app.ui.theme.FluentTheme
import com.omnisearch.app.ui.theme.LightFluentColors
import com.omnisearch.app.ui.theme.DarkFluentColors
import com.omnisearch.app.ui.theme.LightOceanColors
import com.omnisearch.app.ui.theme.DarkOceanColors
import com.omnisearch.app.ui.theme.LightForestColors
import com.omnisearch.app.ui.theme.DarkForestColors
import com.omnisearch.app.ui.theme.LightSunsetColors
import com.omnisearch.app.ui.theme.DarkSunsetColors
import com.omnisearch.app.ui.theme.LightLavenderColors
import com.omnisearch.app.ui.theme.DarkLavenderColors
import com.omnisearch.app.ui.theme.LightRoseColors
import com.omnisearch.app.ui.theme.DarkRoseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    syncViewModel: SyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by syncViewModel.themeMode.collectAsState()
    val themeColor by syncViewModel.themeColor.collectAsState()

    Scaffold(
        containerColor = FluentTheme.colors.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Appearance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = FluentTheme.colors.textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = FluentTheme.colors.textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FluentTheme.colors.pageBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Color Scheme Mode (Premium Slider Style Selector)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                shape = RoundedCornerShape(FluentTheme.dims.surfaceRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APP THEME MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluentTheme.colors.textMuted,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ThemeModeSlider(
                        currentMode = themeMode,
                        onModeSelected = { syncViewModel.setThemeMode(it) }
                    )
                }
            }

            // Section 2: App Color Themes (6 Premium Palettes)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                shape = RoundedCornerShape(FluentTheme.dims.surfaceRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COLOR PALETTES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluentTheme.colors.textMuted,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val themes = listOf(
                        Triple("SLATE", "Slate (Default)", "Classic Windows Fluent styling"),
                        Triple("OCEAN", "Ocean (Midnight Blue)", "Deep ocean blue tones"),
                        Triple("FOREST", "Forest (Emerald Green)", "Soothing green accents"),
                        Triple("SUNSET", "Sunset (Warm Amber)", "Radiant orange and gold hues"),
                        Triple("LAVENDER", "Lavender (Royal Violet)", "Pleasing purple and violet tones"),
                        Triple("ROSE", "Rose (Vivid Crimson)", "Sleek red and pink accents")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        themes.forEach { themeItem ->
                            val isSelected = themeColor == themeItem.first
                            
                            // Determine preview colors
                            val previewLight = when (themeItem.first) {
                                "OCEAN" -> LightOceanColors
                                "FOREST" -> LightForestColors
                                "SUNSET" -> LightSunsetColors
                                "LAVENDER" -> LightLavenderColors
                                "ROSE" -> LightRoseColors
                                else -> LightFluentColors
                            }
                            val previewDark = when (themeItem.first) {
                                "OCEAN" -> DarkOceanColors
                                "FOREST" -> DarkForestColors
                                "SUNSET" -> DarkSunsetColors
                                "LAVENDER" -> DarkLavenderColors
                                "ROSE" -> DarkRoseColors
                                else -> DarkFluentColors
                            }

                            // A single high-end theme row with direct preview card clicking
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { syncViewModel.setThemeColor(themeItem.first) },
                                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.pageBg),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) FluentTheme.colors.accent else FluentTheme.colors.panelBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = themeItem.second,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FluentTheme.colors.textColor
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = FluentTheme.colors.accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = themeItem.third,
                                            fontSize = 12.sp,
                                            color = FluentTheme.colors.textMuted,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Preview visual blocks showing colors side-by-side (Light & Dark preview)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Light Mode Preview Card
                                            Box(
                                                modifier = Modifier
                                                    .width(70.dp)
                                                    .height(38.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(previewLight.pageBg)
                                                    .border(1.dp, previewLight.panelBorder, RoundedCornerShape(4.dp))
                                                    .padding(3.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    // Card mock
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(16.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(previewLight.surfaceBg)
                                                            .padding(horizontal = 3.dp, vertical = 2.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        // Accent bar mock
                                                        Box(
                                                            modifier = Modifier
                                                                .width(24.dp)
                                                                .height(4.dp)
                                                                .clip(RoundedCornerShape(1.dp))
                                                                .background(previewLight.accent)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Light",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = previewLight.textColor,
                                                        modifier = Modifier.padding(start = 2.dp)
                                                    )
                                                }
                                            }

                                            // Dark Mode Preview Card
                                            Box(
                                                modifier = Modifier
                                                    .width(70.dp)
                                                    .height(38.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(previewDark.pageBg)
                                                    .border(1.dp, previewDark.panelBorder, RoundedCornerShape(4.dp))
                                                    .padding(3.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    // Card mock
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(16.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(previewDark.surfaceBg)
                                                            .padding(horizontal = 3.dp, vertical = 2.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        // Accent bar mock
                                                        Box(
                                                            modifier = Modifier
                                                                .width(24.dp)
                                                                .height(4.dp)
                                                                .clip(RoundedCornerShape(1.dp))
                                                                .background(previewDark.accent)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Dark",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = previewDark.textColor,
                                                        modifier = Modifier.padding(start = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeModeSlider(
    currentMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf("DARK", "SYSTEM", "LIGHT")
    val selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(FluentTheme.colors.panelBg)
            .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
            .padding(4.dp)
    ) {
        // Sliding indicator background
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = maxWidth / 3
            val offset by animateDpAsState(
                targetValue = width * selectedIndex,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                ),
                label = "ThemeSliderOffset"
            )
            
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(width)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(FluentTheme.colors.surfaceBg)
                    .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
            )
        }
        
        // Interactive labels and icons
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val isSelected = currentMode == mode
                val icon = when (mode) {
                    "DARK" -> Icons.Default.DarkMode
                    "SYSTEM" -> Icons.Default.SettingsSuggest
                    else -> Icons.Default.LightMode
                }
                val label = when (mode) {
                    "DARK" -> "Dark"
                    "SYSTEM" -> "System"
                    else -> "Light"
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) FluentTheme.colors.accent else FluentTheme.colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) FluentTheme.colors.textColor else FluentTheme.colors.textMuted
                        )
                    }
                }
            }
        }
    }
}
