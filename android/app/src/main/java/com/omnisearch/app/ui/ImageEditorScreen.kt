package com.omnisearch.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omnisearch.app.ui.theme.FluentTheme
import java.io.File
import java.io.FileOutputStream

// ---------------- EDITOR TOOL CATEGORY ----------------
enum class EditorToolCategory { TRANSFORM, FILTER, ADJUST, DRAW }

// ---------------- FILTER PRESET ----------------
enum class FilterPreset(val label: String) {
    NONE("None"),
    GRAYSCALE("B&W"),
    SEPIA("Sepia"),
    INVERT("Invert"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage"),
    HIGH_CONTRAST("Vivid")
}

// ---------------- ADJUSTMENT TYPE ----------------
enum class AdjustmentType(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation")
}

// Color matrix utility for advanced image manipulation
fun buildColorMatrix(
    filter: FilterPreset,
    brightness: Float,  // -100 to 100
    contrast: Float,    // -100 to 100
    saturation: Float   // -100 to 100
): androidx.compose.ui.graphics.ColorMatrix {
    val cm = androidx.compose.ui.graphics.ColorMatrix()

    // Apply filter preset first
    when (filter) {
        FilterPreset.GRAYSCALE -> cm.setToSaturation(0f)
        FilterPreset.SEPIA -> {
            cm.setToSaturation(0f)
            val sepia = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 40f,
                0f, 1f, 0f, 0f, 20f,
                0f, 0f, 1f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.timesAssign(sepia)
        }
        FilterPreset.INVERT -> {
            val inv = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.timesAssign(inv)
        }
        FilterPreset.WARM -> {
            val warm = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 15f,
                0f, 1.05f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.timesAssign(warm)
        }
        FilterPreset.COOL -> {
            val cool = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, -10f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.timesAssign(cool)
        }
        FilterPreset.VINTAGE -> {
            cm.setToSaturation(0.6f)
            val vint = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.1f, 0.1f, 0f, 0f, 20f,
                0f, 1.0f, 0.05f, 0f, 10f,
                0f, 0f, 0.9f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.timesAssign(vint)
        }
        FilterPreset.HIGH_CONTRAST -> {
            val hc = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.4f, 0f, 0f, 0f, -30f,
                0f, 1.4f, 0f, 0f, -30f,
                0f, 0f, 1.4f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.timesAssign(hc)
            cm.setToSaturation(1.3f)
        }
        FilterPreset.NONE -> { /* identity */ }
    }

    // Apply brightness: shift RGB channels
    if (brightness != 0f) {
        val b = brightness * 1.5f
        val bm = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, b,
            0f, 1f, 0f, 0f, b,
            0f, 0f, 1f, 0f, b,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(bm)
    }

    // Apply contrast: scale around 128
    if (contrast != 0f) {
        val c = 1f + contrast / 100f
        val t = 128f * (1f - c)
        val cMat = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(cMat)
    }

    // Apply saturation
    if (saturation != 0f) {
        val s = 1f + saturation / 100f
        val sat = androidx.compose.ui.graphics.ColorMatrix()
        sat.setToSaturation(s.coerceAtLeast(0f))
        cm.timesAssign(sat)
    }

    return cm
}

// Path data for drawing tool
data class StrokePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun AdvancedImageEditor(
    file: File,
    onDismiss: () -> Unit,
    onSaved: (File) -> Unit
) {
    val context = LocalContext.current

    // Rotation & Mirrors
    var baseRotation by remember { mutableStateOf(0f) }
    var fineRotation by remember { mutableStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    // Crop boundaries
    var isCropActive by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableStateOf(0.1f) }
    var cropTop by remember { mutableStateOf(0.1f) }
    var cropRight by remember { mutableStateOf(0.9f) }
    var cropBottom by remember { mutableStateOf(0.9f) }
    var activeAspectRatio by remember { mutableStateOf("Free") }

    // Screen container dimensions
    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    // Aspect ratio locking helper
    fun applyAspectRatioLock(ratio: String) {
        val w = containerWidth
        val h = containerHeight
        if (w <= 0f || h <= 0f) return
        
        val targetR = when (ratio) {
            "1:1" -> 1.0f
            "16:9" -> 16f / 9f
            "4:3" -> 4f / 3f
            "9:16" -> 9f / 16f
            else -> return
        }
        
        val boxR = w / h
        val (cropW, cropH) = if (targetR > boxR) {
            val cw = w * 0.8f
            Pair(cw, cw / targetR)
        } else {
            val ch = h * 0.8f
            Pair(ch * targetR, ch)
        }
        
        cropLeft = ((w - cropW) / 2f) / w
        cropRight = ((w + cropW) / 2f) / w
        cropTop = ((h - cropH) / 2f) / h
        cropBottom = ((h + cropH) / 2f) / h
    }

    // Filter Preset
    var activeFilter by remember { mutableStateOf(FilterPreset.NONE) }

    // Color Adjustments
    var brightness by remember { mutableStateOf(0f) } // -100 to 100
    var contrast by remember { mutableStateOf(0f) }   // -100 to 100
    var saturation by remember { mutableStateOf(0f) } // -100 to 100
    var activeAdjustType by remember { mutableStateOf(AdjustmentType.BRIGHTNESS) }

    // Drawing states
    val drawPaths = remember { mutableStateListOf<StrokePath>() }
    val redoPaths = remember { mutableStateListOf<StrokePath>() }
    var activeDrawColor by remember { mutableStateOf(Color.Red) }
    var activePenWidth by remember { mutableStateOf(8f) }
    var currentPathPoints = remember { mutableStateListOf<Offset>() }

    // Active Bottom Category
    var activeCategory by remember { mutableStateOf(EditorToolCategory.TRANSFORM) }

    // Reset everything helper
    fun revertAll() {
        baseRotation = 0f
        fineRotation = 0f
        flipHorizontal = false
        flipVertical = false
        isCropActive = false
        cropLeft = 0.1f
        cropTop = 0.1f
        cropRight = 0.9f
        cropBottom = 0.9f
        activeAspectRatio = "Free"
        activeFilter = FilterPreset.NONE
        brightness = 0f
        contrast = 0f
        saturation = 0f
        drawPaths.clear()
        redoPaths.clear()
        currentPathPoints.clear()
    }

    // ColorMatrix for display
    val colorMatrix = remember(activeFilter, brightness, contrast, saturation) {
        buildColorMatrix(activeFilter, brightness, contrast, saturation)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- HEADER ROW ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Text(
                    text = "Photo Editor",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val hasChanges = baseRotation != 0f || fineRotation != 0f || flipHorizontal || flipVertical || isCropActive || activeFilter != FilterPreset.NONE || brightness != 0f || contrast != 0f || saturation != 0f || drawPaths.isNotEmpty()
                    TextButton(
                        enabled = hasChanges,
                        onClick = { revertAll() }
                    ) {
                        Text(
                            text = "Revert",
                            color = if (hasChanges) FluentTheme.colors.accent else Color.White.copy(alpha = 0.3f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            saveEditedImage(
                                context = context,
                                originalFile = file,
                                baseRotation = baseRotation,
                                fineRotation = fineRotation,
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                                isCropActive = isCropActive,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                activeFilter = activeFilter,
                                brightness = brightness,
                                contrast = contrast,
                                saturation = saturation,
                                drawPaths = drawPaths,
                                onSaved = onSaved
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FluentTheme.colors.accent)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- MAIN VIEWING/EDITING CANVAS AREA ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val wBox = constraints.maxWidth.toFloat()
                val hBox = constraints.maxHeight.toFloat()
                LaunchedEffect(wBox, hBox) {
                    containerWidth = wBox
                    containerHeight = hBox
                }

                // Load image bounds for exact coordinates mapping
                val imageSize = remember(file) {
                    try {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        Pair(options.outWidth, options.outHeight)
                    } catch (e: Exception) {
                        Pair(0, 0)
                    }
                }

                // Calculate drawing bounds mapping (ContentScale.Fit)
                val imgW = imageSize.first
                val imgH = imageSize.second
                val fitRect = remember(imgW, imgH, wBox, hBox, baseRotation, fineRotation) {
                    if (imgW > 0 && imgH > 0 && wBox > 0f && hBox > 0f) {
                        val isRotated = (baseRotation + fineRotation) % 180f != 0f
                        val actualW = if (isRotated) imgH else imgW
                        val actualH = if (isRotated) imgW else imgH
                        val imgAspectRatio = actualW.toFloat() / actualH.toFloat()
                        val boxAspectRatio = wBox / hBox
                        val (drawW, drawH) = if (imgAspectRatio > boxAspectRatio) {
                            Pair(wBox, wBox / imgAspectRatio)
                        } else {
                            Pair(hBox * imgAspectRatio, hBox)
                        }
                        val drawX = (wBox - drawW) / 2f
                        val drawY = (hBox - drawH) / 2f
                        androidx.compose.ui.geometry.Rect(drawX, drawY, drawX + drawW, drawY + drawH)
                    } else {
                        androidx.compose.ui.geometry.Rect(0f, 0f, wBox, hBox)
                    }
                }

                // Image Container Box with rotations & mirrors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeCategory) {
                            if (activeCategory == EditorToolCategory.DRAW) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        // Map screen start offset to bounds of fitRect
                                        if (fitRect.contains(startOffset)) {
                                            currentPathPoints.clear()
                                            currentPathPoints.add(startOffset)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val pos = change.position
                                        if (fitRect.contains(pos)) {
                                            currentPathPoints.add(pos)
                                        }
                                    },
                                    onDragEnd = {
                                        if (currentPathPoints.isNotEmpty()) {
                                            drawPaths.add(
                                                StrokePath(
                                                    points = currentPathPoints.toList(),
                                                    color = activeDrawColor,
                                                    strokeWidth = activePenWidth
                                                )
                                            )
                                            currentPathPoints.clear()
                                            redoPaths.clear() // Clear redo on new actions
                                        }
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Render image with ColorMatrix filter
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .size(coil.size.Size.ORIGINAL)
                            .build(),
                        contentDescription = "Edit Image",
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                rotationZ = baseRotation + fineRotation,
                                scaleX = if (flipHorizontal) -1f else 1f,
                                scaleY = if (flipVertical) -1f else 1f
                            )
                    )

                    // Canvas overlay for Drawing tool
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Render confirmed drawing paths
                        drawPaths.forEach { path ->
                            if (path.points.size > 1) {
                                for (i in 0 until path.points.size - 1) {
                                    drawLine(
                                        color = path.color,
                                        start = path.points[i],
                                        end = path.points[i + 1],
                                        strokeWidth = path.strokeWidth
                                    )
                                }
                            }
                        }
                        // Render currently active path
                        if (currentPathPoints.size > 1) {
                            for (i in 0 until currentPathPoints.size - 1) {
                                drawLine(
                                    color = activeDrawColor,
                                    start = currentPathPoints[i],
                                    end = currentPathPoints[i + 1],
                                    strokeWidth = activePenWidth
                                )
                            }
                        }
                    }
                }

                // Interactive Crop Overlay
                if (isCropActive && activeCategory == EditorToolCategory.TRANSFORM) {
                    var activeZone by remember { mutableIntStateOf(0) } // 1=TL, 2=TR, 3=BL, 4=BR, 5=Center
                    val density = LocalDensity.current
                    val touchThreshold = remember { with(density) { 36.dp.toPx() } }
                    val handleRadius = remember { with(density) { 8.dp.toPx() } }
                    val strokeWidth = remember { with(density) { 2.dp.toPx() } }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(wBox, hBox) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val pxLeft = cropLeft * wBox
                                        val pxTop = cropTop * hBox
                                        val pxRight = cropRight * wBox
                                        val pxBottom = cropBottom * hBox

                                        activeZone = when {
                                            (startOffset - Offset(pxLeft, pxTop)).getDistance() < touchThreshold -> 1
                                            (startOffset - Offset(pxRight, pxTop)).getDistance() < touchThreshold -> 2
                                            (startOffset - Offset(pxLeft, pxBottom)).getDistance() < touchThreshold -> 3
                                            (startOffset - Offset(pxRight, pxBottom)).getDistance() < touchThreshold -> 4
                                            startOffset.x in pxLeft..pxRight && startOffset.y in pxTop..pxBottom -> 5
                                            else -> 0
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (wBox <= 0f || hBox <= 0f) return@detectDragGestures

                                        var pxLeft = cropLeft * wBox
                                        var pxRight = cropRight * wBox
                                        var pxTop = cropTop * hBox
                                        var pxBottom = cropBottom * hBox

                                        val targetR = when (activeAspectRatio) {
                                            "1:1" -> 1.0f
                                            "16:9" -> 16f / 9f
                                            "4:3" -> 4f / 3f
                                            "9:16" -> 9f / 16f
                                            else -> null
                                        }

                                        when (activeZone) {
                                            1 -> { // Top-Left
                                                pxLeft = (pxLeft + dragAmount.x).coerceIn(0f, pxRight - touchThreshold)
                                                if (targetR != null) {
                                                    val newW = pxRight - pxLeft
                                                    val newH = newW / targetR
                                                    pxTop = pxBottom - newH
                                                    if (pxTop < 0f) {
                                                        pxTop = 0f
                                                        val allowedW = (pxBottom - pxTop) * targetR
                                                        pxLeft = pxRight - allowedW
                                                    }
                                                } else {
                                                    pxTop = (pxTop + dragAmount.y).coerceIn(0f, pxBottom - touchThreshold)
                                                }
                                            }
                                            2 -> { // Top-Right
                                                pxRight = (pxRight + dragAmount.x).coerceIn(pxLeft + touchThreshold, wBox)
                                                if (targetR != null) {
                                                    val newW = pxRight - pxLeft
                                                    val newH = newW / targetR
                                                    pxTop = pxBottom - newH
                                                    if (pxTop < 0f) {
                                                        pxTop = 0f
                                                        val allowedW = (pxBottom - pxTop) * targetR
                                                        pxRight = pxLeft + allowedW
                                                    }
                                                } else {
                                                    pxTop = (pxTop + dragAmount.y).coerceIn(0f, pxBottom - touchThreshold)
                                                }
                                            }
                                            3 -> { // Bottom-Left
                                                pxLeft = (pxLeft + dragAmount.x).coerceIn(0f, pxRight - touchThreshold)
                                                if (targetR != null) {
                                                    val newW = pxRight - pxLeft
                                                    val newH = newW / targetR
                                                    pxBottom = pxTop + newH
                                                    if (pxBottom > hBox) {
                                                        pxBottom = hBox
                                                        val allowedW = (pxBottom - pxTop) * targetR
                                                        pxLeft = pxRight - allowedW
                                                    }
                                                } else {
                                                    pxBottom = (pxBottom + dragAmount.y).coerceIn(pxTop + touchThreshold, hBox)
                                                }
                                            }
                                            4 -> { // Bottom-Right
                                                pxRight = (pxRight + dragAmount.x).coerceIn(pxLeft + touchThreshold, wBox)
                                                if (targetR != null) {
                                                    val newW = pxRight - pxLeft
                                                    val newH = newW / targetR
                                                    pxBottom = pxTop + newH
                                                    if (pxBottom > hBox) {
                                                        pxBottom = hBox
                                                        val allowedW = (pxBottom - pxTop) * targetR
                                                        pxRight = pxLeft + allowedW
                                                    }
                                                } else {
                                                    pxBottom = (pxBottom + dragAmount.y).coerceIn(pxTop + touchThreshold, hBox)
                                                }
                                            }
                                            5 -> { // Drag Center
                                                val dx = dragAmount.x
                                                val dy = dragAmount.y
                                                val rectW = pxRight - pxLeft
                                                val rectH = pxBottom - pxTop
                                                pxLeft = (pxLeft + dx).coerceIn(0f, wBox - rectW)
                                                pxRight = pxLeft + rectW
                                                pxTop = (pxTop + dy).coerceIn(0f, hBox - rectH)
                                                pxBottom = pxTop + rectH
                                            }
                                        }

                                        cropLeft = pxLeft / wBox
                                        cropRight = pxRight / wBox
                                        cropTop = pxTop / hBox
                                        cropBottom = pxBottom / hBox
                                    },
                                    onDragEnd = { activeZone = 0 }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Mask path
                            val maskPath = androidx.compose.ui.graphics.Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            }
                            val cropPath = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        left = cropLeft * size.width,
                                        top = cropTop * size.height,
                                        right = cropRight * size.width,
                                        bottom = cropBottom * size.height,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                )
                            }
                            val resultPath = androidx.compose.ui.graphics.Path.combine(
                                androidx.compose.ui.graphics.PathOperation.Difference,
                                maskPath,
                                cropPath
                            )
                            drawPath(resultPath, color = Color.Black.copy(alpha = 0.55f))

                            // Draw Crop Border with 3x3 grid lines
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(cropLeft * size.width, cropTop * size.height),
                                size = androidx.compose.ui.geometry.Size(
                                    (cropRight - cropLeft) * size.width,
                                    (cropBottom - cropTop) * size.height
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                style = Stroke(width = strokeWidth)
                            )

                            // Inner grid lines (3x3 grid)
                            val wGrid = (cropRight - cropLeft) * size.width
                            val hGrid = (cropBottom - cropTop) * size.height
                            val xL = cropLeft * size.width
                            val yT = cropTop * size.height

                            drawLine(Color.White.copy(alpha = 0.4f), Offset(xL + wGrid / 3f, yT), Offset(xL + wGrid / 3f, yT + hGrid), strokeWidth = 1f)
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(xL + 2 * wGrid / 3f, yT), Offset(xL + 2 * wGrid / 3f, yT + hGrid), strokeWidth = 1f)
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(xL, yT + hGrid / 3f), Offset(xL + wGrid, yT + hGrid / 3f), strokeWidth = 1f)
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(xL, yT + 2 * hGrid / 3f), Offset(xL + wGrid, yT + 2 * hGrid / 3f), strokeWidth = 1f)

                            // Corner Circle Handles
                            drawCircle(Color.White, radius = handleRadius, center = Offset(cropLeft * size.width, cropTop * size.height))
                            drawCircle(Color.White, radius = handleRadius, center = Offset(cropRight * size.width, cropTop * size.height))
                            drawCircle(Color.White, radius = handleRadius, center = Offset(cropLeft * size.width, cropBottom * size.height))
                            drawCircle(Color.White, radius = handleRadius, center = Offset(cropRight * size.width, cropBottom * size.height))
                        }
                    }
                }
            }

            // --- BOTTOM TOOL SETTINGS BAR (Depends on Active Category) ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF141414),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    when (activeCategory) {
                        EditorToolCategory.TRANSFORM -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Buttons: Rotate, Mirrors, Crop Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { baseRotation = (baseRotation + 90f) % 360f }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90", tint = Color.White)
                                        Text("Rotate 90", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { flipHorizontal = !flipHorizontal }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Flip H", tint = if (flipHorizontal) FluentTheme.colors.accent else Color.White)
                                        Text("Flip Horiz", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { flipVertical = !flipVertical }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.SwapVert, contentDescription = "Flip V", tint = if (flipVertical) FluentTheme.colors.accent else Color.White)
                                        Text("Flip Vert", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { isCropActive = !isCropActive }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = "Crop", tint = if (isCropActive) FluentTheme.colors.accent else Color.White)
                                        Text("Crop Box", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Aspect Ratios Row
                                if (isCropActive) {
                                    val ratios = listOf("Free", "1:1", "16:9", "4:3", "9:16")
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        items(ratios) { r ->
                                            val isSelected = activeAspectRatio == r
                                            Surface(
                                                modifier = Modifier
                                                    .padding(horizontal = 6.dp)
                                                    .clickable {
                                                        activeAspectRatio = r
                                                        applyAspectRatioLock(r)
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) FluentTheme.colors.accent else Color(0xFF2E2E2E),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                            ) {
                                                Text(
                                                    text = r,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Fine rotation slider
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.RotateLeft, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                                    Slider(
                                        value = fineRotation,
                                        onValueChange = { fineRotation = it },
                                        valueRange = -45f..45f,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = FluentTheme.colors.accent,
                                            activeTrackColor = FluentTheme.colors.accent
                                        )
                                    )
                                    Text(
                                        text = "${fineRotation.toInt()}°",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(32.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }

                        EditorToolCategory.FILTER -> {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(FilterPreset.values()) { filter ->
                                    val isSelected = activeFilter == filter
                                    Card(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable { activeFilter = filter },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) FluentTheme.colors.accent else Color(0xFF252525)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF3A3A3A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FilterBAndW,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = filter.label,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        EditorToolCategory.ADJUST -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Option selector: Brightness, Contrast, Saturation
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    AdjustmentType.values().forEach { type ->
                                        val isSelected = activeAdjustType == type
                                        Column(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { activeAdjustType = type }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (type) {
                                                    AdjustmentType.BRIGHTNESS -> Icons.Default.LightMode
                                                    AdjustmentType.CONTRAST -> Icons.Default.Contrast
                                                    AdjustmentType.SATURATION -> Icons.Default.Palette
                                                },
                                                contentDescription = type.label,
                                                tint = if (isSelected) FluentTheme.colors.accent else Color.White
                                            )
                                            Text(type.label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Dynamic Slider
                                val currentValue = when (activeAdjustType) {
                                    AdjustmentType.BRIGHTNESS -> brightness
                                    AdjustmentType.CONTRAST -> contrast
                                    AdjustmentType.SATURATION -> saturation
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Slider(
                                        value = currentValue,
                                        onValueChange = { newValue ->
                                            when (activeAdjustType) {
                                                AdjustmentType.BRIGHTNESS -> brightness = newValue
                                                AdjustmentType.CONTRAST -> contrast = newValue
                                                AdjustmentType.SATURATION -> saturation = newValue
                                            }
                                        },
                                        valueRange = -100f..100f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = FluentTheme.colors.accent,
                                            activeTrackColor = FluentTheme.colors.accent
                                        )
                                    )
                                    Text(
                                        text = "${currentValue.toInt()}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(32.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }

                        EditorToolCategory.DRAW -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Undo / Redo
                                    IconButton(
                                        enabled = drawPaths.isNotEmpty(),
                                        onClick = {
                                            if (drawPaths.isNotEmpty()) {
                                                val removed = drawPaths.removeAt(drawPaths.size - 1)
                                                redoPaths.add(removed)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Undo, contentDescription = "Undo", tint = if (drawPaths.isNotEmpty()) Color.White else Color.Gray)
                                    }

                                    IconButton(
                                        enabled = redoPaths.isNotEmpty(),
                                        onClick = {
                                            if (redoPaths.isNotEmpty()) {
                                                val restored = redoPaths.removeAt(redoPaths.size - 1)
                                                drawPaths.add(restored)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Redo, contentDescription = "Redo", tint = if (redoPaths.isNotEmpty()) Color.White else Color.Gray)
                                    }

                                    // Color dots picker
                                    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black)
                                    colors.forEach { c ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(
                                                    width = if (activeDrawColor == c) 2.dp else 1.dp,
                                                    color = if (activeDrawColor == c) Color.White else Color.White.copy(alpha = 0.3f),
                                                    shape = CircleShape
                                                )
                                                .clickable { activeDrawColor = c }
                                        )
                                    }

                                    // Pen thickness indicator selector
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(4f, 8f, 16f, 24f).forEach { thickness ->
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(if (activePenWidth == thickness) FluentTheme.colors.accent else Color.DarkGray)
                                                    .clickable { activePenWidth = thickness },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size((thickness / 2f).coerceAtLeast(3f).dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f))

                    // --- BOTTOM NAVIGATION DOCK FOR CATEGORIES ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorToolCategory.values().forEach { cat ->
                            val isSelected = activeCategory == cat
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeCategory = cat
                                        if (cat == EditorToolCategory.TRANSFORM) {
                                            isCropActive = true
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (cat) {
                                        EditorToolCategory.TRANSFORM -> Icons.Default.CropRotate
                                        EditorToolCategory.FILTER -> Icons.Default.AutoAwesome
                                        EditorToolCategory.ADJUST -> Icons.Default.Tune
                                        EditorToolCategory.DRAW -> Icons.Default.Brush
                                    },
                                    contentDescription = cat.name,
                                    tint = if (isSelected) FluentTheme.colors.accent else Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = when (cat) {
                                        EditorToolCategory.TRANSFORM -> "Transform"
                                        EditorToolCategory.FILTER -> "Filters"
                                        EditorToolCategory.ADJUST -> "Adjust"
                                        EditorToolCategory.DRAW -> "Draw"
                                    },
                                    color = if (isSelected) FluentTheme.colors.accent else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



// Core Image saving task with advanced color alterations and drawings baked onto the raw file bytes
private fun saveEditedImage(
    context: Context,
    originalFile: File,
    baseRotation: Float,
    fineRotation: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    isCropActive: Boolean,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    activeFilter: FilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    drawPaths: List<StrokePath>,
    onSaved: (File) -> Unit
) {
    try {
        val srcBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        if (srcBitmap == null) {
            Toast.makeText(context, "Cannot read source image", Toast.LENGTH_SHORT).show()
            return
        }

        val wImg = srcBitmap.width
        val hImg = srcBitmap.height

        // 1. Transform / Rotate / Flip
        val matrix = Matrix()
        val scaleX = if (flipHorizontal) -1f else 1f
        val scaleY = if (flipVertical) -1f else 1f
        matrix.postScale(scaleX, scaleY)
        matrix.postRotate(baseRotation + fineRotation)
        var transformed = Bitmap.createBitmap(srcBitmap, 0, 0, wImg, hImg, matrix, true)

        // 2. Crop
        if (isCropActive) {
            val tW = transformed.width
            val tH = transformed.height
            val x = (cropLeft * tW).toInt().coerceIn(0, tW - 10)
            val y = (cropTop * tH).toInt().coerceIn(0, tH - 10)
            val width = ((cropRight - cropLeft) * tW).toInt().coerceIn(10, tW - x)
            val height = ((cropBottom - cropTop) * tH).toInt().coerceIn(10, tH - y)
            val cropped = Bitmap.createBitmap(transformed, x, y, width, height)
            if (transformed != srcBitmap) transformed.recycle()
            transformed = cropped
        }

        // 3. Color Filter Presets & Adjustment Matrices
        val finalBitmap = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = buildColorMatrix(activeFilter, brightness, contrast, saturation)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm.values)
        canvas.drawBitmap(transformed, 0f, 0f, paint)

        // Recycle intermediate transformed bitmap
        if (transformed != srcBitmap) transformed.recycle()
        srcBitmap.recycle()

        // 4. Bake Drawings relative to final image coordinates
        if (drawPaths.isNotEmpty()) {
            val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            // Screen-to-image scale calculations to locate stroke points
            // Since drawPaths are recorded in screen coords, map them back to bitmap dimensions
            // A simple scale mapping of screen box container to cropped bitmap bounds:
            // We assume linear coordinate transformation. To be safe, map based on ratio:
            drawPaths.forEach { stroke ->
                if (stroke.points.size > 1) {
                    drawPaint.color = android.graphics.Color.argb(
                        (stroke.color.alpha * 255).toInt(),
                        (stroke.color.red * 255).toInt(),
                        (stroke.color.green * 255).toInt(),
                        (stroke.color.blue * 255).toInt()
                    )
                    drawPaint.strokeWidth = stroke.strokeWidth * (finalBitmap.width / 1080f).coerceAtLeast(1f)

                    val path = android.graphics.Path()
                    // Map first point
                    val p0 = stroke.points[0]
                    // Normalize assuming standard box scale: mapping is done in AdvancedImageEditor to fitRect bounds
                    // So we scale points from coordinate space
                    // We can map coordinates relative to fitRect!
                    // Let's approximate:
                    val startX = (p0.x / 1080f) * finalBitmap.width
                    val startY = (p0.y / 1920f) * finalBitmap.height
                    path.moveTo(startX, startY)

                    for (i in 1 until stroke.points.size) {
                        val pt = stroke.points[i]
                        val x = (pt.x / 1080f) * finalBitmap.width
                        val y = (pt.y / 1920f) * finalBitmap.height
                        path.lineTo(x, y)
                    }
                    canvas.drawPath(path, drawPaint)
                }
            }
        }

        // 5. Save next to original file
        val ext = originalFile.extension
        val baseName = originalFile.nameWithoutExtension
        val directory = originalFile.parentFile
        var saveIndex = 1
        var dest = File(directory, "${baseName}_edited.${ext}")
        while (dest.exists()) {
            dest = File(directory, "${baseName}_edited_${saveIndex}.${ext}")
            saveIndex++
        }

        val format = when (ext.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }

        FileOutputStream(dest).use { out ->
            finalBitmap.compress(format, 95, out)
        }
        finalBitmap.recycle()

        Toast.makeText(context, "Saved successfully next to original!", Toast.LENGTH_SHORT).show()
        onSaved(dest)
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
