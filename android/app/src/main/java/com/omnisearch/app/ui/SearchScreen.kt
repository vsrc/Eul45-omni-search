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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisearch.app.data.DriveInfo
import com.omnisearch.app.data.DuplicateFile
import com.omnisearch.app.data.DuplicateGroup
import com.omnisearch.app.data.SearchResult
import com.omnisearch.app.data.PinnedFileEntity
import com.omnisearch.app.ui.theme.FluentTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.selection.SelectionContainer
import android.widget.VideoView
import android.widget.MediaController
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    syncViewModel: SyncViewModel,
    onOpenExplorer: () -> Unit,
    triggerAutoFocus: Boolean = false,
    onTriggerAutoFocusHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val status by syncViewModel.status.collectAsState()
    val searchResults by syncViewModel.searchResults.collectAsState()
    val drives by syncViewModel.drives.collectAsState()
    val indexStatus by syncViewModel.indexStatus.collectAsState()
    val activeDriveLetter by syncViewModel.activeDriveLetter.collectAsState()
    val duplicateGroups by syncViewModel.duplicateGroups.collectAsState()
    val isSearching by syncViewModel.isSearching.collectAsState()
    val isScanningDuplicates by syncViewModel.isScanningDuplicates.collectAsState()
    val error by syncViewModel.error.collectAsState()
    val actionMessage by syncViewModel.actionMessage.collectAsState()
    val fileContent by syncViewModel.fileContent.collectAsState()
    val isLoadingFileContent by syncViewModel.isLoadingFileContent.collectAsState()
    val pinnedFiles by syncViewModel.pinnedFiles.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    var currentSearchLimit by remember { mutableIntStateOf(100) }
    var selectedExtensionFilter by remember { mutableStateOf<String?>("all") }
    var activeActionFile by remember { mutableStateOf<SearchResult?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmPath by remember { mutableStateOf<String?>(null) }
    var showFilePreview by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<SearchResult?>(null) }
    
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showWidgetsScreen by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(triggerAutoFocus) {
        if (triggerAutoFocus) {
            delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
            onTriggerAutoFocusHandled()
        }
    }

    val extensionFilters = listOf(
        Pair("all", "All Files"),
        Pair("pdf", "PDFs"),
        Pair("png,jpg,jpeg,webp", "Images"),
        Pair("mp4,mkv,webm,avi", "Videos"),
        Pair("txt,md,log", "Text"),
        Pair("rs,py,java,js,ts,kt,cpp,h", "Code"),
        Pair("zip,rar,7z,tar,gz", "Archives")
    )

    if (showWidgetsScreen) {
        com.omnisearch.app.ui.WidgetsScreen(onBack = { showWidgetsScreen = false })
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FluentTheme.colors.pageBg,
        floatingActionButton = {
            if (status != ConnectionStatus.CONNECTED) {
                val folderGradient = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE29604),
                        Color(0xFFE19605),
                        Color(0xFFE18E02)
                    )
                )
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp, end = 8.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(folderGradient)
                        .clickable { onOpenExplorer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Local Files Explorer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Title Bar
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OmniSearch",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluentTheme.colors.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (status == ConnectionStatus.CONNECTED && indexStatus != null) {
                        val statusLabel = when {
                            indexStatus!!.indexing -> "Indexing in progress (${indexStatus!!.indexedCount} files)"
                            indexStatus!!.ready -> "Index ready (${indexStatus!!.indexedCount} files)"
                            else -> "Desktop scanner ready"
                        }
                        Text(
                            text = statusLabel,
                            fontSize = 12.sp,
                            color = if (indexStatus!!.indexing) FluentTheme.colors.accent else FluentTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))

                // Header Action: Query server state and trigger duplicate scans
                if (status == ConnectionStatus.CONNECTED) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FluentTheme.colors.surfaceBg)
                                .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                .clickable {
                                    syncViewModel.queryIndexStatus()
                                    syncViewModel.queryDrives()
                                    Toast.makeText(context, "Refreshed PC index status", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh server details",
                                tint = FluentTheme.colors.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FluentTheme.colors.surfaceBg)
                                .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                .clickable { showDuplicatesDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CopyAll,
                                contentDescription = "Scan duplicate files",
                                tint = FluentTheme.colors.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FluentTheme.colors.surfaceBg)
                                .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                .clickable { onOpenExplorer() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Local Files Explorer",
                                tint = FluentTheme.colors.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            val sharedPrefs = context.getSharedPreferences("OmniSearchPrefs", Context.MODE_PRIVATE)
            var showBanner by remember { mutableStateOf(sharedPrefs.getBoolean("show_widgets_banner", true)) }

            if (showBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FluentTheme.dims.paddingLarge, vertical = 4.dp)
                        .clickable { 
                            sharedPrefs.edit().putBoolean("show_widgets_banner", false).apply()
                            showBanner = false
                            showWidgetsScreen = true 
                        },
                    colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.omnisearch.app.R.drawable.ic_widget_qr),
                            contentDescription = "Widgets",
                            tint = FluentTheme.colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Add widgets to your home screen",
                            color = FluentTheme.colors.textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = FluentTheme.colors.textMuted
                        )
                    }
                }
            }

            // Connection notification banner
            AnimatedVisibility(visible = error.isNotEmpty() || actionMessage.isNotEmpty()) {
                val isErr = error.isNotEmpty()
                val bannerBg = if (isErr) Color(0xFFFFECEB) else Color(0xFFE3FBE3)
                val bannerBorder = if (isErr) Color(0xFFFDC2BE) else Color(0xFFC1F3C1)
                val bannerText = if (isErr) FluentTheme.colors.dangerText else FluentTheme.colors.connectedText
                val bannerIcon = if (isErr) Icons.Default.Error else Icons.Default.CheckCircle

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FluentTheme.dims.paddingLarge, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = bannerIcon,
                        contentDescription = null,
                        tint = bannerText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isErr) error else actionMessage,
                        fontSize = 13.sp,
                        color = bannerText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { syncViewModel.clearNotifications() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = bannerText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (status != ConnectionStatus.CONNECTED) {
                // Connection requirement guide
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
                            imageVector = Icons.Default.SettingsCell,
                            contentDescription = "Server disconnected",
                            tint = FluentTheme.colors.textMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connect to PC",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluentTheme.colors.textColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To search your computer drives instantly, pair this phone with your OmniSearch desktop app. Go to the Settings tab.",
                            fontSize = 14.sp,
                            color = FluentTheme.colors.textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Search Input Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FluentTheme.dims.paddingLarge)
                        .clip(RoundedCornerShape(FluentTheme.dims.controlRadius))
                        .background(FluentTheme.colors.surfaceBg)
                        .border(1.dp, FluentTheme.colors.panelBorder, RoundedCornerShape(FluentTheme.dims.controlRadius))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FluentTheme.colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextFieldMinimal(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                hasSearched = it.isNotEmpty()
                                currentSearchLimit = 100
                                syncViewModel.executeSearch(
                                    query = it,
                                    extension = if (selectedExtensionFilter == "all") null else selectedExtensionFilter
                                )
                            },
                            placeholder = "Search files instantly...",
                            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear query",
                                tint = FluentTheme.colors.textMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        searchQuery = ""
                                        hasSearched = false
                                        currentSearchLimit = 100
                                        syncViewModel.executeSearch(query = "", extension = null)
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Extensions Pills Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = FluentTheme.dims.paddingLarge),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(extensionFilters) { filter ->
                        val isSelected = selectedExtensionFilter == filter.first
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) FluentTheme.colors.accent else FluentTheme.colors.surfaceBg)
                                .border(
                                    1.dp,
                                    if (isSelected) FluentTheme.colors.accent else FluentTheme.colors.panelBorder,
                                    CircleShape
                                )
                                .clickable {
                                    selectedExtensionFilter = filter.first
                                    currentSearchLimit = 100
                                    if (searchQuery.isNotEmpty()) {
                                        syncViewModel.executeSearch(
                                            query = searchQuery,
                                            extension = if (filter.first == "all") null else filter.first
                                        )
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter.second,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) FluentTheme.colors.onAccent else FluentTheme.colors.textColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Results or Drives Panel
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = FluentTheme.colors.accent)
                    }
                } else if (searchQuery.trim().isEmpty()) {
                    // Show Drives Configuration list when no search has been performed
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(FluentTheme.dims.paddingLarge),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "COMPUTER VOLUMES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FluentTheme.colors.textMuted,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (drives.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No drive mappings found. Check sync server.",
                                            color = FluentTheme.colors.textMuted,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            items(drives) { drive ->
                                DriveRowCard(
                                    drive = drive,
                                    onIndexClick = {
                                        syncViewModel.startIndexing(drive.letter)
                                        Toast.makeText(context, "Requested index of volume ${drive.letter}:", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "PRO-TIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FluentTheme.colors.textMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "High-speed Scanning",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = FluentTheme.colors.textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "OmniSearch reads your computer's NTFS master file tables directly. Indexing takes under 3 seconds for 1 million files!",
                                        fontSize = 12.sp,
                                        color = FluentTheme.colors.textMuted,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (searchQuery.trim().isNotEmpty() && searchResults.isEmpty()) {
                    // No results for current query + filter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.SearchOff,
                                contentDescription = "No results",
                                tint = FluentTheme.colors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No matching files found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluentTheme.colors.textColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try a different search query or change the extension filter.",
                                fontSize = 13.sp,
                                color = FluentTheme.colors.textMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Display files search matches
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (activeDriveLetter.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = FluentTheme.dims.paddingLarge, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Results for Drive $activeDriveLetter:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FluentTheme.colors.accent
                                )
                                val count = indexStatus?.indexedCount ?: 0L
                                if (count > 0) {
                                    val formattedCount = try {
                                        java.text.NumberFormat.getIntegerInstance().format(count)
                                    } catch (e: Exception) {
                                        count.toString()
                                    }
                                    Text(
                                        text = "$formattedCount files indexed",
                                        fontSize = 12.sp,
                                        color = FluentTheme.colors.textMuted
                                    )
                                }
                            }
                        }

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
                        items(searchResults, key = { it.path }) { result ->
                            SearchResultRow(
                                item = result,
                                pinnedFiles = pinnedFiles,
                                activeFileContent = fileContent,
                                onClick = {
                                    activeActionFile = result
                                    showActionSheet = true
                                    if (result.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif") && !result.isDirectory) {
                                        syncViewModel.requestFileContent(result.path)
                                    }
                                }
                            )
                        }

                        // Load More button
                        if (searchResults.size >= currentSearchLimit) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = {
                                            val newLimit = currentSearchLimit + 100
                                            currentSearchLimit = newLimit
                                            syncViewModel.executeSearch(
                                                query = searchQuery,
                                                extension = if (selectedExtensionFilter == "all") null else selectedExtensionFilter,
                                                limit = newLimit
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FluentTheme.colors.accent),
                                        shape = RoundedCornerShape(FluentTheme.dims.controlRadius)
                                    ) {
                                        Text("Load More Results", color = FluentTheme.colors.onAccent)
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

    // Modal action Sheet for Remote triggers
    if (showActionSheet && activeActionFile != null) {
        val file = activeActionFile!!
        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                activeActionFile = null
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
                // File name + details
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
                if (file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif") && fileContent != null && fileContent!!.path == file.path) {
                    val bitmap = remember(fileContent!!.data) {
                        try {
                            val decodedBytes = Base64.decode(fileContent!!.data, Base64.DEFAULT)
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

                // Actions List
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                ) {
                    Column {
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

                        Divider(color = FluentTheme.colors.panelBorder)

                        // Pin offline to Device Hub
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncViewModel.pinFile(file, context)
                                    showActionSheet = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = FluentTheme.colors.textColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Pin to local Phone Hub", color = FluentTheme.colors.textColor)
                        }

                        Divider(color = FluentTheme.colors.panelBorder)

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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        previewFile = file
                                        showFilePreview = true
                                        syncViewModel.requestFileContent(file.path)
                                        showActionSheet = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = FluentTheme.colors.accent)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Preview on Phone", color = FluentTheme.colors.textColor)
                            }

                            Divider(color = FluentTheme.colors.panelBorder)
                        }

                        // Download to Phone
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    syncViewModel.triggerRemoteTransfer(file.path)
                                    showActionSheet = false
                                    Toast.makeText(context, "Download transfer started...", Toast.LENGTH_SHORT).show()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = FluentTheme.colors.textColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Download to Phone", color = FluentTheme.colors.textColor)
                        }

                        Divider(color = FluentTheme.colors.panelBorder)

                        // Delete from Computer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDeleteConfirmPath = file.path
                                    showActionSheet = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = FluentTheme.colors.dangerText)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Delete from PC disk", color = FluentTheme.colors.dangerText, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // Modal Confirmation Dialog for destructive Delete triggers
    if (showDeleteConfirmPath != null) {
        val targetPath = showDeleteConfirmPath!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmPath = null },
            title = { Text("Delete computer file?") },
            text = { Text("Are you sure you want to permanently delete this file from your computer disk? This action moves it to the desktop Recycle Bin.") },
            confirmButton = {
                Button(
                    onClick = {
                        syncViewModel.deleteFileOnPC(targetPath)
                        showDeleteConfirmPath = null
                        activeActionFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentTheme.colors.dangerText)
                ) {
                    Text("Delete permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmPath = null }) {
                    Text("Cancel", color = FluentTheme.colors.textColor)
                }
            },
            containerColor = FluentTheme.colors.pageBg
        )
    }

    // Modern Dialog for Desktop Duplicate scanner
    if (showDuplicatesDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicatesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CopyAll, contentDescription = null, tint = FluentTheme.colors.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PC Duplicate Files Finder")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Scans all mapped NTFS drives on your computer to locate identical duplicate files above 50MB. Reclaims hard-drive storage.",
                        fontSize = 13.sp,
                        color = FluentTheme.colors.textMuted,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isScanningDuplicates) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = FluentTheme.colors.accent)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Analyzing PC sectors...", fontSize = 12.sp, color = FluentTheme.colors.textMuted)
                            }
                        }
                    } else if (duplicateGroups.isEmpty()) {
                        Button(
                            onClick = { syncViewModel.scanDuplicates() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = FluentTheme.colors.accent),
                            shape = RoundedCornerShape(FluentTheme.dims.controlRadius)
                        ) {
                            Text("Run Duplicate Scan now", color = FluentTheme.colors.onAccent)
                        }
                    } else {
                        Text(
                            text = "DUPLICATE GROUPS FOUND",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluentTheme.colors.textMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(duplicateGroups) { group ->
                                DuplicateGroupItem(
                                    group = group,
                                    onDeleteClick = { path ->
                                        syncViewModel.deleteFileOnPC(path, recycleBin = true)
                                        Toast.makeText(context, "Sent delete request to PC", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDuplicatesDialog = false }) {
                    Text("Close", color = FluentTheme.colors.textColor)
                }
            },
            containerColor = FluentTheme.colors.pageBg
        )
    }

    // Modal BottomSheet for Mobile File Preview & Download
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
                        IconButton(
                            onClick = {
                                if (fileContent != null) {
                                    syncViewModel.saveFileToDisk(
                                        context = context,
                                        fileName = file.name,
                                        contentType = fileContent!!.contentType,
                                        base64Data = fileContent!!.data
                                    )
                                } else {
                                    syncViewModel.triggerRemoteTransfer(file.path)
                                    showFilePreview = false
                                    previewFile = null
                                    syncViewModel.clearFileContent()
                                    Toast.makeText(context, "Download transfer started...", Toast.LENGTH_SHORT).show()
                                }
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
                                    androidx.compose.foundation.text.selection.SelectionContainer {
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
fun DriveRowCard(
    drive: DriveInfo,
    onIndexClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder),
        shape = RoundedCornerShape(FluentTheme.dims.surfaceRadius)
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(FluentTheme.colors.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = FluentTheme.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Local Disk (${drive.letter}:)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = FluentTheme.colors.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Filesystem: ${drive.filesystem} | DriveType: ${drive.driveType}",
                        fontSize = 12.sp,
                        color = FluentTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            if (drive.isNtfs) {
                Button(
                    onClick = onIndexClick,
                    colors = ButtonDefaults.buttonColors(containerColor = FluentTheme.colors.accent),
                    shape = RoundedCornerShape(FluentTheme.dims.controlRadius),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Index", fontSize = 11.sp, color = FluentTheme.colors.onAccent)
                }
            } else {
                Text(
                    "Non-NTFS",
                    fontSize = 11.sp,
                    color = FluentTheme.colors.textMuted,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SearchResultRow(
    item: SearchResult,
    pinnedFiles: List<PinnedFileEntity>,
    activeFileContent: SyncViewModel.FileContent?,
    onClick: () -> Unit
) {
    val isDir = item.isDirectory

    val iconColor = when {
        isDir -> Color(0xFFFFB900) // Folder yellow
        item.extension in listOf("png", "jpg", "jpeg", "webp", "gif") -> Color(0xFFE3008C) // Image magenta
        item.extension in listOf("mp4", "mkv", "webm", "avi") -> Color(0xFFB146C2) // Video purple
        item.extension == "pdf" -> Color(0xFFD83B01) // PDF orange-red
        item.extension in listOf("rs", "py", "java", "js", "ts", "kt", "cpp", "h", "html", "css") -> Color(0xFF107C41) // Code green
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

    val pinnedFile = remember(pinnedFiles, item.path) {
        pinnedFiles.firstOrNull { it.path == item.path }
    }
    val base64Data = remember(pinnedFile, activeFileContent, item.path) {
        pinnedFile?.fileData ?: if (activeFileContent != null && activeFileContent.path == item.path) activeFileContent.data else null
    }

    val thumbnailBitmap = remember(base64Data) {
        if (base64Data != null && item.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif")) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
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

@Composable
fun DuplicateGroupItem(
    group: DuplicateGroup,
    onDeleteClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, FluentTheme.colors.panelBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duplicates (${group.fileCount} copies)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = FluentTheme.colors.textColor
                )
                Text(
                    text = formatSize(group.size),
                    color = FluentTheme.colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.files.forEach { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = FluentTheme.colors.textColor
                            )
                            Text(
                                text = file.path,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = FluentTheme.colors.textMuted
                            )
                        }
                        IconButton(
                            onClick = { onDeleteClick(file.path) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete copy",
                                tint = FluentTheme.colors.dangerText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun BasicTextFieldMinimal(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = FluentTheme.colors.textMuted,
                fontSize = 14.sp
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(
                color = FluentTheme.colors.textColor,
                fontSize = 14.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
