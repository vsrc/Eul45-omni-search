package com.omnisearch.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisearch.app.data.PinnedFileEntity
import com.omnisearch.app.ui.theme.FluentTheme
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.selection.SelectionContainer
import android.widget.VideoView
import android.widget.MediaController
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PinnedScreen(
    syncViewModel: SyncViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by syncViewModel.status.collectAsState()
    val pinnedFiles by syncViewModel.pinnedFiles.collectAsState()
    val fileContent by syncViewModel.fileContent.collectAsState()
    val isLoadingFileContent by syncViewModel.isLoadingFileContent.collectAsState()

    var activePinnedFile by remember { mutableStateOf<PinnedFileEntity?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showFilePreview by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<PinnedFileEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FluentTheme.colors.pageBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = FluentTheme.dims.paddingLarge,
                        vertical = FluentTheme.dims.paddingMedium
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Hub",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluentTheme.colors.textColor
                    )
                    Text(
                        text = "${pinnedFiles.size} offline pinned files",
                        fontSize = 12.sp,
                        color = FluentTheme.colors.textMuted
                    )
                }
            }

            if (pinnedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "No bookmarks",
                            tint = FluentTheme.colors.textMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Pinned Files",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluentTheme.colors.textColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pin search results from your PC to save them in this local Offline Hub. Access their paths even when disconnected!",
                            fontSize = 14.sp,
                            color = FluentTheme.colors.textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = FluentTheme.dims.paddingLarge,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pinnedFiles, key = { it.path }) { file ->
                        PinnedFileRow(
                            item = file,
                            onClick = {
                                activePinnedFile = file
                                showActionSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal actions sheets for Pinned hubs
    if (showActionSheet && activePinnedFile != null) {
        val file = activePinnedFile!!
        val isConnected = status == ConnectionStatus.CONNECTED

        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                activePinnedFile = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FluentTheme.colors.pageBg,
            dragHandle = { BottomSheetDefaults.DragHandle(color = FluentTheme.colors.textMuted) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FluentTheme.dims.paddingLarge)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = file.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FluentTheme.colors.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = file.path,
                    fontSize = 12.sp,
                    color = FluentTheme.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                val formattedSize = remember(file.size) { formatSize(file.size) }
                Text(
                    text = "Size: $formattedSize | Extension: .${file.extension.uppercase()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FluentTheme.colors.accent
                )

                // Compact image preview inside bottom sheet
                val activeFileContent = fileContent
                val hasContent = file.fileData != null || (activeFileContent != null && activeFileContent.path == file.path)
                val base64Data = file.fileData ?: activeFileContent?.data
                if (file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif") && hasContent && base64Data != null) {
                    val bitmap = remember(base64Data) {
                        try {
                            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(FluentTheme.dims.surfaceRadius))
                                .background(FluentTheme.colors.surfaceBg)
                                .border(1.dp, FluentTheme.colors.panelBorder, RoundedCornerShape(FluentTheme.dims.surfaceRadius)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Compact image preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                ) {
                    Column {
                        // Unpin
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncViewModel.unpinFile(file.path, context)
                                    showActionSheet = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = FluentTheme.colors.accent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Unpin from Phone", color = FluentTheme.colors.textColor)
                        }

                        // Preview on Phone
                        val isPreviewable = file.extension.lowercase() in listOf(
                            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg",
                            "pdf",
                            "mp4", "mkv", "webm", "avi", "3gp", "mov",
                            "txt", "md", "log", "rs", "py", "java", "js", "ts", "kt", "cpp", "h",
                            "html", "css", "json", "xml", "yaml", "yml", "ini", "toml", "csv",
                            "c", "go", "rb", "sh", "bat", "ps1", "cfg", "conf"
                        )
                        if (isPreviewable && !file.isDirectory) {
                            Divider(color = FluentTheme.colors.panelBorder)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.fileData != null) {
                                            previewFile = file
                                            showFilePreview = true
                                            syncViewModel.loadFileContentLocally(
                                                path = file.path,
                                                contentType = getMimeTypeForExtension(file.extension),
                                                data = file.fileData
                                            )
                                            showActionSheet = false
                                        } else if (isConnected) {
                                            previewFile = file
                                            showFilePreview = true
                                            syncViewModel.requestFileContent(file.path)
                                            showActionSheet = false
                                        } else {
                                            Toast.makeText(context, "This file is not cached offline and PC is disconnected.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = FluentTheme.colors.accent)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Preview on Phone", color = FluentTheme.colors.textColor)
                            }
                        }

                        if (isConnected) {
                            Divider(color = FluentTheme.colors.panelBorder)

                            // Open on PC
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        syncViewModel.openFileOnPC(file.path)
                                        showActionSheet = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, tint = FluentTheme.colors.accent)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Open file on Computer", color = FluentTheme.colors.textColor)
                            }
                            
                            Divider(color = FluentTheme.colors.panelBorder)

                            // Reveal in Explorer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        syncViewModel.revealInPCExplorer(file.path)
                                        showActionSheet = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = FluentTheme.colors.textColor)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Reveal in Explorer", color = FluentTheme.colors.textColor)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal BottomSheet for Mobile Offline Hub File Preview & Download
    if (showFilePreview && previewFile != null) {
        val file = previewFile!!
        ModalBottomSheet(
            onDismissRequest = {
                showFilePreview = false
                previewFile = null
                syncViewModel.clearFileContent()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FluentTheme.colors.pageBg,
            dragHandle = { BottomSheetDefaults.DragHandle(color = FluentTheme.colors.textMuted) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = FluentTheme.dims.paddingLarge)
            ) {
                // Preview Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluentTheme.colors.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Size: ${remember(file.size) { formatSize(file.size) }}",
                            fontSize = 12.sp,
                            color = FluentTheme.colors.textMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Download/Save button
                        if (fileContent != null) {
                            IconButton(
                                onClick = {
                                    syncViewModel.saveFileToDisk(
                                        context = context,
                                        fileName = file.name,
                                        contentType = fileContent!!.contentType,
                                        base64Data = fileContent!!.data
                                    )
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(FluentTheme.colors.accent)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save file",
                                    tint = FluentTheme.colors.onAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                showFilePreview = false
                                previewFile = null
                                syncViewModel.clearFileContent()
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(FluentTheme.colors.surfaceBg)
                                .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = FluentTheme.colors.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(FluentTheme.dims.surfaceRadius))
                        .background(FluentTheme.colors.surfaceBg)
                        .border(1.dp, FluentTheme.colors.panelBorder, RoundedCornerShape(FluentTheme.dims.surfaceRadius))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingFileContent) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = FluentTheme.colors.accent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Streaming file from desktop...",
                                fontSize = 13.sp,
                                color = FluentTheme.colors.textMuted
                            )
                        }
                    } else if (fileContent != null) {
                        val content = fileContent!!
                        val mime = content.contentType
                        when {
                            mime.startsWith("image/") -> {
                                val dataUrl = "data:${mime};base64,${content.data}"
                                ImageZoomView(
                                    imageUrl = dataUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            mime.startsWith("video/") -> {
                                val tempFile = remember(content.data) {
                                    try {
                                        val decodedBytes = Base64.decode(content.data, Base64.DEFAULT)
                                        val tFile = File(context.cacheDir, "preview_temp_" + System.currentTimeMillis() + "." + file.extension)
                                        tFile.writeBytes(decodedBytes)
                                        tFile
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (tempFile != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            val root = android.widget.FrameLayout(ctx).apply {
                                                setBackgroundColor(android.graphics.Color.BLACK)
                                                layoutParams = android.view.ViewGroup.LayoutParams(
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            }
                                            
                                            val videoView = android.widget.VideoView(ctx)
                                            
                                            val controlsContainer = android.widget.LinearLayout(ctx).apply {
                                                orientation = android.widget.LinearLayout.HORIZONTAL
                                                gravity = android.view.Gravity.CENTER_VERTICAL
                                                val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                                                    setColor(android.graphics.Color.parseColor("#E61A1B20")) // 90% solid fluent deep grey
                                                    cornerRadius = 24f * ctx.resources.displayMetrics.density
                                                }
                                                background = bgDrawable
                                                val paddingHorizontal = (20 * ctx.resources.displayMetrics.density).toInt()
                                                val paddingVertical = (12 * ctx.resources.displayMetrics.density).toInt()
                                                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                                            }
                                            
                                            val playPauseButton = android.widget.ImageView(ctx).apply {
                                                setImageResource(android.R.drawable.ic_media_pause)
                                                setColorFilter(android.graphics.Color.WHITE)
                                                val size = (32 * ctx.resources.displayMetrics.density).toInt()
                                                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                                                    marginEnd = (12 * ctx.resources.displayMetrics.density).toInt()
                                                }
                                            }
                                            
                                            val seekBar = android.widget.SeekBar(ctx).apply {
                                                layoutParams = android.widget.LinearLayout.LayoutParams(
                                                    0,
                                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                                    1f
                                                )
                                            }
                                            
                                            controlsContainer.addView(playPauseButton)
                                            controlsContainer.addView(seekBar)
                                            
                                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                                            val hideRunnable = Runnable {
                                                controlsContainer.visibility = android.view.View.GONE
                                            }
                                            
                                            val resetAutoHide = {
                                                handler.removeCallbacks(hideRunnable)
                                                handler.postDelayed(hideRunnable, 4000)
                                            }
                                            
                                            playPauseButton.setOnClickListener {
                                                resetAutoHide()
                                                if (videoView.isPlaying) {
                                                    videoView.pause()
                                                    playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                                                } else {
                                                    videoView.start()
                                                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                                                }
                                            }
                                            
                                            seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                                                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                                                    if (fromUser) {
                                                        videoView.seekTo(progress)
                                                        resetAutoHide()
                                                    }
                                                }
                                                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {
                                                    handler.removeCallbacks(hideRunnable)
                                                }
                                                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                                                    resetAutoHide()
                                                }
                                            })
                                            
                                            videoView.apply {
                                                setVideoPath(tempFile.absolutePath)
                                                setOnPreparedListener { mp ->
                                                    mp.isLooping = true
                                                    start()
                                                    seekBar.max = duration
                                                    resetAutoHide()
                                                }
                                                setOnTouchListener { _, event ->
                                                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                                                        if (controlsContainer.visibility == android.view.View.VISIBLE) {
                                                            controlsContainer.visibility = android.view.View.GONE
                                                        } else {
                                                            controlsContainer.visibility = android.view.View.VISIBLE
                                                            resetAutoHide()
                                                        }
                                                    }
                                                    true
                                                }
                                            }
                                            
                                            val updateProgressRunnable = object : Runnable {
                                                override fun run() {
                                                    try {
                                                        if (videoView.isPlaying) {
                                                            seekBar.progress = videoView.currentPosition
                                                        }
                                                    } catch (e: Exception) {}
                                                    handler.postDelayed(this, 250)
                                                }
                                            }
                                            handler.post(updateProgressRunnable)
                                            
                                            root.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                                                override fun onViewAttachedToWindow(v: android.view.View) {}
                                                override fun onViewDetachedFromWindow(v: android.view.View) {
                                                    handler.removeCallbacksAndMessages(null)
                                                }
                                            })
                                            
                                            root.addView(videoView, android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.view.Gravity.CENTER
                                            ))
                                            
                                            val controlParams = android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                                android.view.Gravity.BOTTOM
                                            ).apply {
                                                val margin = (24 * ctx.resources.displayMetrics.density).toInt()
                                                setMargins(margin, margin, margin, margin)
                                            }
                                            root.addView(controlsContainer, controlParams)
                                            
                                            root
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("Failed to load video preview", color = FluentTheme.colors.dangerText)
                                }
                            }
                            mime == "application/pdf" -> {
                                PdfZoomView(
                                    base64Data = content.data,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> {
                                val textString = remember(content.data) {
                                    try {
                                        String(Base64.decode(content.data, Base64.DEFAULT))
                                    } catch (e: Exception) {
                                        "Error decoding text file"
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = textString,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = FluentTheme.colors.textColor,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                            else -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = FluentTheme.colors.textMuted,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Binary File (.${file.extension.uppercase()})",
                                        fontWeight = FontWeight.Bold,
                                        color = FluentTheme.colors.textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Click download above to save this file locally.",
                                        fontSize = 12.sp,
                                        color = FluentTheme.colors.textMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Could not load file data.",
                            color = FluentTheme.colors.dangerText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PinnedFileRow(
    item: PinnedFileEntity,
    onClick: () -> Unit
) {
    val isDir = item.isDirectory

    val iconColor = when {
        isDir -> Color(0xFFFFB900)
        item.extension in listOf("png", "jpg", "jpeg", "webp", "gif") -> Color(0xFFE3008C)
        item.extension in listOf("mp4", "mkv", "webm", "avi") -> Color(0xFFB146C2)
        item.extension == "pdf" -> Color(0xFFD83B01)
        item.extension in listOf("rs", "py", "java", "js", "ts", "kt", "cpp", "h") -> Color(0xFF107C41)
        else -> FluentTheme.colors.textMuted
    }

    val iconVector = when {
        isDir -> Icons.Default.Folder
        item.extension in listOf("png", "jpg", "jpeg", "webp", "gif") -> Icons.Default.Image
        item.extension in listOf("mp4", "mkv", "webm", "avi") -> Icons.Default.VideoLibrary
        item.extension == "pdf" -> Icons.Default.PictureAsPdf
        item.extension in listOf("rs", "py", "java", "js", "ts", "kt", "cpp", "h") -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }

    val thumbnailBitmap = remember(item.fileData) {
        if (item.fileData != null && item.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif")) {
            try {
                val bytes = Base64.decode(item.fileData, Base64.DEFAULT)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                var inSampleSize = 1
                val reqHeight = 80
                val reqWidth = 80
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                        inSampleSize *= 2
                    }
                }
                options.inSampleSize = inSampleSize
                options.inJustDecodeBounds = false
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FluentTheme.dims.surfaceRadius))
            .background(FluentTheme.colors.surfaceBg)
            .border(1.dp, FluentTheme.colors.panelBorder, RoundedCornerShape(FluentTheme.dims.surfaceRadius))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = FluentTheme.colors.textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.path,
                fontSize = 11.sp,
                color = FluentTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val formattedSize = remember(item.size) { formatSize(item.size) }
        Text(
            text = if (isDir) "" else formattedSize,
            fontSize = 12.sp,
            color = FluentTheme.colors.textMuted
        )
    }
}

fun getMimeTypeForExtension(ext: String): String {
    return when (ext.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "pdf" -> "application/pdf"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "txt", "log", "cfg", "conf", "ini" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html" -> "text/html"
        "css" -> "text/css"
        else -> "application/octet-stream"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
