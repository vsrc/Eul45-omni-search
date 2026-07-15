package com.omnisearch.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.omnisearch.app.widget.WidgetThemeUtil
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisearch.app.R
import com.omnisearch.app.ui.theme.FluentTheme
import com.omnisearch.app.widget.ScanConnectWidgetProvider
import com.omnisearch.app.widget.LocalExplorerWidgetProvider
import com.omnisearch.app.widget.MusicWidgetProvider

data class WidgetInfo(
    val name: String,
    val sizeDescription: String,
    val preview: @Composable () -> Unit,
    val providerClass: Class<*>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }
    val context = LocalContext.current
    
    val prefs = remember { context.getSharedPreferences("omnisearch_widget_theme", Context.MODE_PRIVATE) }
    var alpha by remember { mutableFloatStateOf(prefs.getInt("widget_alpha", 255) / 255f) }
    var tintColor by remember { mutableIntStateOf(prefs.getInt("widget_tint", android.graphics.Color.TRANSPARENT)) }

    // Solid tint options
    val solidTintOptions = listOf(
        Pair("Default", android.graphics.Color.TRANSPARENT),
        Pair("Pure Dark", android.graphics.Color.BLACK),
        Pair("Pure Light", android.graphics.Color.WHITE),
        Pair("Blue Tint", android.graphics.Color.parseColor("#1976D2")),
        Pair("Dark Teal", android.graphics.Color.parseColor("#3E4444")),
        Pair("Green Tint", android.graphics.Color.parseColor("#388E3C"))
    )

    // Gradient tint options (special positive IDs → gradient drawables)
    data class GradientOption(
        val name: String,
        val id: Int,
        val startColor: Long,
        val endColor: Long
    )
    val gradientTintOptions = listOf(
        GradientOption("Crimson", WidgetThemeUtil.GRADIENT_CRIMSON, 0xFF2C1010, 0xFF4A1A1A),
        GradientOption("Sunset", WidgetThemeUtil.GRADIENT_SUNSET, 0xFF3E1800, 0xFFB84400),
        GradientOption("Ocean", WidgetThemeUtil.GRADIENT_OCEAN, 0xFF0A1929, 0xFF0D47A1),
        GradientOption("Berry", WidgetThemeUtil.GRADIENT_BERRY, 0xFF2C0A1E, 0xFF6A0038),
        GradientOption("Forest", WidgetThemeUtil.GRADIENT_FOREST, 0xFF0A1A0F, 0xFF1B5E20),
        GradientOption("Twilight", WidgetThemeUtil.GRADIENT_TWILIGHT, 0xFF10082B, 0xFF311B92)
    )

    fun applyAndSaveAppearance() {
        val intAlpha = (alpha * 255).toInt()
        WidgetThemeUtil.saveWidgetAppearance(context, intAlpha, tintColor)
        WidgetThemeUtil.updateAllWidgets(context)
    }
    
    fun Modifier.previewBackground(radius: androidx.compose.ui.unit.Dp, defaultColor: Color? = null): Modifier {
        val isGradient = WidgetThemeUtil.isGradientTint(tintColor)
        return if (isGradient) {
            val gradientOption = gradientTintOptions.find { it.id == tintColor }
            if (gradientOption != null) {
                this.background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(Color(gradientOption.startColor).copy(alpha = alpha), Color(gradientOption.endColor).copy(alpha = alpha))
                    ),
                    shape = RoundedCornerShape(radius)
                )
            } else this
        } else {
            val solidColor = if (tintColor == android.graphics.Color.TRANSPARENT) {
                defaultColor?.copy(alpha = alpha) ?: Color(0xE61E1E1E).copy(alpha = alpha)
            } else {
                Color(tintColor).copy(alpha = alpha)
            }
            this.background(
                color = solidColor,
                shape = RoundedCornerShape(radius)
            )
        }
    }
    
    val widgets = listOf(
        WidgetInfo(
            name = "OmniSearch",
            sizeDescription = "Size: 4x1 (Resizable)",
            preview = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .previewBackground(28.dp, defaultColor = Color(0xFF252525))
                        .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(28.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // OmniSearch app icon
                        Image(
                            painter = painterResource(id = R.drawable.ic_widget_search),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                if (tintColor == android.graphics.Color.parseColor("#1976D2")) Color.White 
                                else Color(0xFF1976D2)
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Search hint text
                        Text(
                            text = "Search files...",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Toggle icon (phone/desktop)
                        Icon(
                            painter = painterResource(id = R.drawable.ic_widget_computer),
                            contentDescription = null,
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            providerClass = com.omnisearch.app.widget.SearchWidgetProvider::class.java
        ),
        WidgetInfo(
            name = "Scan & Connect",
            sizeDescription = "Size: 1x1 (Resizable)",
            preview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .previewBackground(12.dp, defaultColor = Color(0xFF2C3E50)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_widget_qr),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Scan", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            providerClass = ScanConnectWidgetProvider::class.java
        ),
        WidgetInfo(
            name = "Local Explorer",
            sizeDescription = "Size: 4x1 (Resizable)",
            preview = {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .previewBackground(12.dp, defaultColor = Color(0xFF1E1E1E))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icons = listOf(
                        Pair(R.drawable.ic_widget_folder, Color(0xFF03A9F4)),
                        Pair(R.drawable.ic_widget_download, Color(0xFF00BCD4)),
                        Pair(R.drawable.ic_widget_image, Color(0xFFFF5722))
                    )
                    icons.forEach { (icon, color) ->
                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            providerClass = LocalExplorerWidgetProvider::class.java
        ),
        WidgetInfo(
            name = "Music Player",
            sizeDescription = "Size: 3x2 (Resizable)",
            preview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .previewBackground(12.dp, defaultColor = Color(0xFF2C1010))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_widget_music),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).background(Color(0x33000000), RoundedCornerShape(4.dp)).padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Icon(painterResource(id = R.drawable.ic_notification_favorite_border), null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Icon(painterResource(id = android.R.drawable.ic_media_previous), null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Icon(painterResource(id = android.R.drawable.ic_media_play), null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Icon(painterResource(id = android.R.drawable.ic_media_next), null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            providerClass = MusicWidgetProvider::class.java
        ),
        WidgetInfo(
            name = "Music Player (Compact)",
            sizeDescription = "Size: 3x1 (Resizable)",
            preview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .previewBackground(12.dp, defaultColor = Color(0xFF2C1010))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_widget_music),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp).background(Color(0x33000000), RoundedCornerShape(4.dp)).padding(4.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(painterResource(id = android.R.drawable.ic_media_previous), null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(painterResource(id = android.R.drawable.ic_media_play), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(painterResource(id = android.R.drawable.ic_media_next), null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            },
            providerClass = com.omnisearch.app.widget.MusicWidgetSmallProvider::class.java
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FluentTheme.colors.pageBg)
    ) {
        TopAppBar(
            title = { Text("Widgets", color = FluentTheme.colors.textColor, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = FluentTheme.colors.textColor
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = FluentTheme.colors.pageBg
            )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Select a widget to add to your home screen",
                    color = FluentTheme.colors.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(widgets) { index, widget ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(
                                when {
                                    widget.name.contains("OmniSearch") -> 200.dp
                                    widget.name.contains("Explorer") || widget.name.contains("Music") -> 120.dp
                                    else -> 80.dp
                                }
                            )
                            .height(80.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        widget.preview()
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = widget.name,
                            color = FluentTheme.colors.textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = widget.sizeDescription,
                            color = FluentTheme.colors.textMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val appWidgetManager = AppWidgetManager.getInstance(context)
                                    val myProvider = ComponentName(context, widget.providerClass)
                                    
                                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                        appWidgetManager.requestPinAppWidget(myProvider, null, null)
                                    } else {
                                        Toast.makeText(context, "Your launcher does not support pinning widgets", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Pinning widgets requires Android 8.0+", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = FluentTheme.colors.surfaceBg,
                                contentColor = FluentTheme.colors.accent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                
                HorizontalDivider(
                    color = FluentTheme.colors.surfaceBg,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Advanced Appearance",
                        color = FluentTheme.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transparency", color = FluentTheme.colors.textColor, fontSize = 14.sp)
                        Text("${(alpha * 100).toInt()}%", color = FluentTheme.colors.textMuted, fontSize = 13.sp)
                    }
                    Slider(
                        value = alpha,
                        onValueChange = { 
                            alpha = it 
                            applyAndSaveAppearance()
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = FluentTheme.colors.accent,
                            activeTrackColor = FluentTheme.colors.accent,
                            inactiveTrackColor = FluentTheme.colors.surfaceBg
                        ),
                        modifier = Modifier.padding(vertical = 4.dp).height(32.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tint Color", color = FluentTheme.colors.textColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Solid tint options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        solidTintOptions.forEach { option ->
                            val isSelected = tintColor == option.second
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (option.second == android.graphics.Color.TRANSPARENT) Color.Gray.copy(alpha = 0.3f) 
                                        else Color(option.second)
                                    )
                                    .clickable {
                                        tintColor = option.second
                                        applyAndSaveAppearance()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (option.second == android.graphics.Color.WHITE || option.second == android.graphics.Color.TRANSPARENT) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Gradient Tint", color = FluentTheme.colors.textColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Gradient tint options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        gradientTintOptions.forEach { gradient ->
                            val isSelected = tintColor == gradient.id
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(Color(gradient.startColor), Color(gradient.endColor))
                                        )
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        tintColor = gradient.id
                                        applyAndSaveAppearance()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
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
