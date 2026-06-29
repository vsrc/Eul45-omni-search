package com.omnisearch.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.omnisearch.app.data.LocalTrashManager
import com.omnisearch.app.ui.theme.FluentTheme
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray

// Local storage categories
enum class ExplorerView {
        HOME,
        DIRECTORY,
        TRASH,
        FAVORITES,
        CATEGORY
}

data class StorageInfo(
        val name: String,
        val path: String,
        val totalBytes: Long,
        val usedBytes: Long,
        val isSdCard: Boolean
)

private const val IMMERSIVE_MEDIA_FLAGS =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

private data class MediaWindowSnapshot(
        val systemUiVisibility: Int,
        val statusBarColor: Int,
        val navigationBarColor: Int,
        val windowFlags: Int,
        val layoutInDisplayCutoutMode: Int,
        val keepScreenOn: Boolean
)

private fun Window.snapshotMediaWindow(): MediaWindowSnapshot =
        MediaWindowSnapshot(
                systemUiVisibility = decorView.systemUiVisibility,
                statusBarColor = statusBarColor,
                navigationBarColor = navigationBarColor,
                windowFlags = attributes.flags,
                layoutInDisplayCutoutMode =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                attributes.layoutInDisplayCutoutMode
                        } else {
                                0
                        },
                keepScreenOn =
                        (attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        )

private fun Window.enterImmersiveMediaMode(keepScreenOn: Boolean) {
        setDimAmount(1f)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        if (keepScreenOn) addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        statusBarColor = android.graphics.Color.TRANSPARENT
        navigationBarColor = android.graphics.Color.TRANSPARENT
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        decorView.setBackgroundColor(android.graphics.Color.BLACK)
        decorView.systemUiVisibility = IMMERSIVE_MEDIA_FLAGS
        attributes =
                attributes.apply {
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                        gravity = Gravity.TOP or Gravity.START
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                layoutInDisplayCutoutMode =
                                        WindowManager.LayoutParams
                                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
        }
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        WindowCompat.setDecorFitsSystemWindows(this, false)
        WindowCompat.getInsetsController(this, decorView).apply {
                systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
        }
}

private fun Window.restoreAfterImmersiveMediaMode(snapshot: MediaWindowSnapshot) {
        decorView.systemUiVisibility = snapshot.systemUiVisibility
        statusBarColor = snapshot.statusBarColor
        navigationBarColor = snapshot.navigationBarColor
        attributes =
                attributes.apply {
                        flags = snapshot.windowFlags
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                layoutInDisplayCutoutMode = snapshot.layoutInDisplayCutoutMode
                        }
                }
        WindowCompat.setDecorFitsSystemWindows(this, false)
        WindowCompat.getInsetsController(this, decorView).show(WindowInsetsCompat.Type.systemBars())
}

@Composable
private fun ImmersiveMediaDialogWindow(activity: Activity?, keepScreenOn: Boolean = false) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(activity, dialogWindow, keepScreenOn) {
                val activityWindow = activity?.window
                val activitySnapshot = activityWindow?.snapshotMediaWindow()
                activityWindow?.enterImmersiveMediaMode(keepScreenOn)
                dialogWindow?.enterImmersiveMediaMode(keepScreenOn)

                onDispose {
                        dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        activitySnapshot?.let { activityWindow?.restoreAfterImmersiveMediaMode(it) }
                }
        }
        SideEffect {
                activity?.window?.enterImmersiveMediaMode(keepScreenOn)
                dialogWindow?.enterImmersiveMediaMode(keepScreenOn)
        }
}

private fun clampMediaOffset(offset: Offset, zoom: Float, width: Float, height: Float): Offset {
        if (zoom <= 1f || width <= 0f || height <= 0f) return Offset.Zero
        val maxX = ((zoom - 1f) * width) / 2f
        val maxY = ((zoom - 1f) * height) / 2f
        return Offset(x = offset.x.coerceIn(-maxX, maxX), y = offset.y.coerceIn(-maxY, maxY))
}

enum class FileSortType {
        NAME,
        DATE,
        SIZE,
        TYPE
}

private data class ImageAlbum(
        val name: String,
        val directory: File,
        val coverFile: File,
        val count: Int,
        val lastModified: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalExplorerScreen(
        onBackToPC: () -> Unit,
        onThemesClick: () -> Unit,
        syncViewModel: SyncViewModel,
        modifier: Modifier = Modifier
) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val syncStatus by syncViewModel.status.collectAsState()
        val isConnectedToDesktop = syncStatus == ConnectionStatus.CONNECTED

        // Navigation and state
        var currentView by remember { mutableStateOf(ExplorerView.HOME) }
        var currentDirectory by remember {
                mutableStateOf(File(Environment.getExternalStorageDirectory().absolutePath))
        }
        var currentCategoryName by remember { mutableStateOf("") }
        var currentCategoryExtensions by remember { mutableStateOf<List<String>>(emptyList()) }

        // Explorer State variables (Saved to/Loaded from Preferences DataStore)
        val prefsManager = remember { com.omnisearch.app.data.PreferencesManager(context) }
        val showHiddenFilesState = prefsManager.showHiddenFilesFlow.collectAsState(initial = false)
        val sortTypeState = prefsManager.sortTypeFlow.collectAsState(initial = "NAME")
        val sortAscendingState = prefsManager.sortAscendingFlow.collectAsState(initial = true)
        val useVideoGridState = prefsManager.useVideoGridFlow.collectAsState(initial = false)
        val useImageAlbumsState = prefsManager.useImageAlbumsFlow.collectAsState(initial = true)
        val imageSortTypeState = prefsManager.imageSortTypeFlow.collectAsState(initial = "DATE")
        val imageSortAscendingState =
                prefsManager.imageSortAscendingFlow.collectAsState(initial = false)

        val showHiddenFiles = showHiddenFilesState.value
        val sortType =
                remember(sortTypeState.value) {
                        try {
                                FileSortType.valueOf(sortTypeState.value)
                        } catch (e: Exception) {
                                FileSortType.NAME
                        }
                }
        val sortAscending = sortAscendingState.value
        val useVideoGrid = useVideoGridState.value
        val useImageAlbums = useImageAlbumsState.value
        val imageSortType =
                remember(imageSortTypeState.value) {
                        try {
                                FileSortType.valueOf(imageSortTypeState.value)
                        } catch (e: Exception) {
                                FileSortType.DATE
                        }
                }
        val imageSortAscending = imageSortAscendingState.value

        var searchQuery by remember { mutableStateOf("") }
        var selectedImageAlbum by remember { mutableStateOf<ImageAlbum?>(null) }

        // Multi-select actions
        val selectedFiles = remember { mutableStateListOf<File>() }
        var isMultiSelectMode by remember { mutableStateOf(false) }
        val copiedFiles = remember { mutableStateListOf<File>() }
        var isMoveOperation by remember { mutableStateOf(false) }

        // Dialog state variables
        var showRenameDialog by remember { mutableStateOf<File?>(null) }
        var showCreateFolderDialog by remember { mutableStateOf(false) }
        var showApkDetailsDialog by remember { mutableStateOf<File?>(null) }
        var showCompressDialog by remember { mutableStateOf<List<File>?>(null) }
        var showDetailsDialog by remember { mutableStateOf<File?>(null) }
        var filesToDelete by remember { mutableStateOf<List<File>?>(null) }

        // Previews/Players state
        var previewPdfFile by remember { mutableStateOf<File?>(null) }
        var previewVideoFile by remember { mutableStateOf<File?>(null) }
        var previewImageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
        var previewImageIndex by remember { mutableIntStateOf(0) }
        var showMusicPlayerExpanded by remember { mutableStateOf(false) }

        // Dynamic file lists
        var directoryFiles by remember { mutableStateOf<List<File>>(emptyList()) }
        var recentFilesList by remember { mutableStateOf<List<File>>(emptyList()) }
        var favoritesList by remember { mutableStateOf<List<File>>(emptyList()) }
        var trashItems by remember {
                mutableStateOf<List<LocalTrashManager.TrashItem>>(emptyList())
        }
        var categoryFilesList by remember { mutableStateOf<List<File>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }

        var storagesList by remember { mutableStateOf<List<StorageInfo>>(emptyList()) }
        var showStorageAnalysisDialog by remember { mutableStateOf(false) }

        // Check permission inline helper function
        val isPermissionGranted = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                } else {
                        context.checkSelfPermission(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED &&
                                context.checkSelfPermission(
                                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED
                }
        }

        // Permission state - check synchronously to avoid initial frame flicker
        var hasPermission by remember { mutableStateOf(isPermissionGranted()) }

        // Dynamically check and update permissions on app resume
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                                hasPermission = isPermissionGranted()
                        }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(hasPermission) {
                if (hasPermission) {
                        withContext(Dispatchers.IO) {
                                val list = mutableListOf<StorageInfo>()
                                // 1. Internal Storage
                                val primaryDir = Environment.getExternalStorageDirectory()
                                try {
                                        val stats = StatFs(primaryDir.path)
                                        val total = stats.blockCountLong * stats.blockSizeLong
                                        val free = stats.availableBlocksLong * stats.blockSizeLong
                                        val used = total - free
                                        list.add(
                                                StorageInfo(
                                                        "Internal storage",
                                                        primaryDir.absolutePath,
                                                        total,
                                                        used,
                                                        false
                                                )
                                        )
                                } catch (_: Exception) {}

                                // 2. SD Card(s)
                                val dirs = ContextCompat.getExternalFilesDirs(context, null)
                                for (i in 1 until dirs.size) {
                                        val dir = dirs[i] ?: continue
                                        if (Environment.isExternalStorageRemovable(dir) ||
                                                        dir.absolutePath.contains("emulated").not()
                                        ) {
                                                val path = dir.absolutePath
                                                val storageRoot =
                                                        if (path.contains("/Android/data")) {
                                                                path.substringBefore(
                                                                        "/Android/data"
                                                                )
                                                        } else {
                                                                path
                                                        }
                                                try {
                                                        val stats = StatFs(storageRoot)
                                                        val total =
                                                                stats.blockCountLong *
                                                                        stats.blockSizeLong
                                                        val free =
                                                                stats.availableBlocksLong *
                                                                        stats.blockSizeLong
                                                        val used = total - free
                                                        if (total > 0) {
                                                                list.add(
                                                                        StorageInfo(
                                                                                "SD card",
                                                                                storageRoot,
                                                                                total,
                                                                                used,
                                                                                true
                                                                        )
                                                                )
                                                        }
                                                } catch (_: Exception) {}
                                        }
                                }
                                withContext(Dispatchers.Main) { storagesList = list }
                        }
                }
        }

        // Refresh dynamic lists
        fun refreshDirectory() {
                if (!hasPermission) return
                isLoading = true
                val rawFiles = currentDirectory.listFiles()
                directoryFiles =
                        if (rawFiles != null) {
                                rawFiles.toList()
                        } else {
                                emptyList()
                        }
                isLoading = false
        }

        fun loadFavorites() {
                isLoading = true
                val prefs =
                        context.getSharedPreferences(
                                "omnisearch_local_explorer",
                                Context.MODE_PRIVATE
                        )
                val favoritesJson = prefs.getString("favorites_paths", "[]") ?: "[]"
                try {
                        val arr = JSONArray(favoritesJson)
                        val list = mutableListOf<File>()
                        for (i in 0 until arr.length()) {
                                val f = File(arr.getString(i))
                                if (f.exists()) list.add(f)
                        }
                        favoritesList = list
                } catch (_: Exception) {}
                isLoading = false
        }

        fun toggleFavorite(file: File) {
                val prefs =
                        context.getSharedPreferences(
                                "omnisearch_local_explorer",
                                Context.MODE_PRIVATE
                        )
                val favoritesJson = prefs.getString("favorites_paths", "[]") ?: "[]"
                try {
                        val arr = JSONArray(favoritesJson)
                        val currentList = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                                currentList.add(arr.getString(i))
                        }
                        if (currentList.contains(file.absolutePath)) {
                                currentList.remove(file.absolutePath)
                                Toast.makeText(
                                                context,
                                                "Removed from Favorites",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                        } else {
                                currentList.add(file.absolutePath)
                                Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT)
                                        .show()
                        }
                        prefs.edit()
                                .putString("favorites_paths", JSONArray(currentList).toString())
                                .apply()
                        loadFavorites()
                } catch (_: Exception) {}
        }

        fun isFavorited(file: File): Boolean {
                return favoritesList.any { it.absolutePath == file.absolutePath }
        }

        fun loadRecentFiles() {
                if (!hasPermission) return
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                        val list = mutableListOf<File>()
                        try {
                                // 1. Query MediaStore
                                val uri = MediaStore.Files.getContentUri("external")
                                val projection =
                                        arrayOf(
                                                MediaStore.Files.FileColumns.DATA,
                                                MediaStore.Files.FileColumns.DATE_MODIFIED
                                        )
                                val sortOrder =
                                        "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 50"
                                val cursor =
                                        context.contentResolver.query(
                                                uri,
                                                projection,
                                                null,
                                                null,
                                                sortOrder
                                        )
                                cursor?.use { c ->
                                        val dataIdx =
                                                c.getColumnIndexOrThrow(
                                                        MediaStore.Files.FileColumns.DATA
                                                )
                                        while (c.moveToNext()) {
                                                val path = c.getString(dataIdx)
                                                val file = File(path)
                                                if (file.exists() &&
                                                                file.isFile &&
                                                                !file.name.startsWith(".")
                                                ) {
                                                        list.add(file)
                                                }
                                        }
                                }
                        } catch (e: Exception) {
                                Log.e("LocalExplorer", "Error querying MediaStore", e)
                        }

                        // 2. Direct filesystem scan of major directories (2 levels deep) for
                        // real-time accuracy
                        val scanDirs =
                                listOf(
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS
                                        ),
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOCUMENTS
                                        ),
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_PICTURES
                                        ),
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DCIM
                                        ),
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_MUSIC
                                        ),
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_MOVIES
                                        )
                                )

                        val fsFiles = mutableListOf<File>()
                        for (dir in scanDirs) {
                                if (dir.exists() && dir.isDirectory) {
                                        // Level 1 files
                                        dir.listFiles()?.forEach { f ->
                                                if (f.isFile) {
                                                        fsFiles.add(f)
                                                } else if (f.isDirectory && !f.name.startsWith(".")
                                                ) {
                                                        // Level 2 files (e.g. DCIM/Camera,
                                                        // DCIM/Screenshots,
                                                        // Pictures/Screenshots)
                                                        f.listFiles()?.forEach { subF ->
                                                                if (subF.isFile) {
                                                                        fsFiles.add(subF)
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        // Combine both sources and sort by modification date
                        val combined =
                                (list + fsFiles)
                                        .distinctBy { it.absolutePath }
                                        .filter {
                                                it.exists() &&
                                                        it.isFile &&
                                                        !it.name.startsWith(".") &&
                                                        it.length() > 0
                                        }
                                        .sortedByDescending { it.lastModified() }
                                        .take(15)

                        withContext(Dispatchers.Main) {
                                recentFilesList = combined
                                isLoading = false
                        }
                }
        }

        fun loadCategoryFiles() {
                if (!hasPermission) return
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                        val list = mutableListOf<File>()
                        val root = Environment.getExternalStorageDirectory()

                        // Fast scan using MediaStore or recursive search for specific extensions
                        try {
                                val uri = MediaStore.Files.getContentUri("external")
                                val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                                val selection = StringBuilder()
                                val args = mutableListOf<String>()

                                currentCategoryExtensions.forEachIndexed { index, ext ->
                                        selection
                                                .append(MediaStore.Files.FileColumns.DATA)
                                                .append(" LIKE ?")
                                        if (index < currentCategoryExtensions.size - 1) {
                                                selection.append(" OR ")
                                        }
                                        args.add("%.$ext")
                                }

                                val cursor =
                                        context.contentResolver.query(
                                                uri,
                                                projection,
                                                if (selection.isNotEmpty()) selection.toString()
                                                else null,
                                                if (args.isNotEmpty()) args.toTypedArray()
                                                else null,
                                                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                                        )

                                cursor?.use { c ->
                                        val dataIdx =
                                                c.getColumnIndexOrThrow(
                                                        MediaStore.Files.FileColumns.DATA
                                                )
                                        while (c.moveToNext()) {
                                                val path = c.getString(dataIdx)
                                                val file = File(path)
                                                if (file.exists() && file.isFile) {
                                                        list.add(file)
                                                }
                                        }
                                }
                        } catch (e: Exception) {
                                // Fallback manual walk of major directories (Download, Pictures,
                                // Documents, Music,
                                // Movies)
                                val scanFolders =
                                        listOf(
                                                Environment.getExternalStoragePublicDirectory(
                                                        Environment.DIRECTORY_DOWNLOADS
                                                ),
                                                Environment.getExternalStoragePublicDirectory(
                                                        Environment.DIRECTORY_DOCUMENTS
                                                ),
                                                Environment.getExternalStoragePublicDirectory(
                                                        Environment.DIRECTORY_PICTURES
                                                ),
                                                Environment.getExternalStoragePublicDirectory(
                                                        Environment.DIRECTORY_MUSIC
                                                ),
                                                Environment.getExternalStoragePublicDirectory(
                                                        Environment.DIRECTORY_MOVIES
                                                )
                                        )

                                fun walk(file: File) {
                                        if (file.isDirectory) {
                                                file.listFiles()?.forEach { walk(it) }
                                        } else {
                                                val ext = file.extension.lowercase()
                                                if (currentCategoryExtensions.contains(ext)) {
                                                        list.add(file)
                                                }
                                        }
                                }
                                scanFolders.forEach { walk(it) }
                        }

                        withContext(Dispatchers.Main) {
                                categoryFilesList = list.sortedByDescending { it.lastModified() }
                                isLoading = false
                        }
                }
        }

        fun loadTrash() {
                isLoading = true
                trashItems = LocalTrashManager.getTrashItems(context)
                isLoading = false
        }

        // Trigger loads on screen changes
        LaunchedEffect(currentView, currentDirectory, hasPermission) {
                if (hasPermission) {
                        when (currentView) {
                                ExplorerView.HOME -> {
                                        loadRecentFiles()
                                        loadFavorites()
                                }
                                ExplorerView.DIRECTORY -> {
                                        refreshDirectory()
                                }
                                ExplorerView.TRASH -> {
                                        loadTrash()
                                }
                                ExplorerView.FAVORITES -> {
                                        loadFavorites()
                                }
                                ExplorerView.CATEGORY -> {
                                        loadCategoryFiles()
                                }
                        }
                }
        }

        LaunchedEffect(currentView, currentCategoryName, useImageAlbums) {
                if (currentView != ExplorerView.CATEGORY ||
                                currentCategoryName != "Images" ||
                                !useImageAlbums
                ) {
                        selectedImageAlbum = null
                }
        }

        val handleBackNavigation = {
                if (isMultiSelectMode) {
                        isMultiSelectMode = false
                        selectedFiles.clear()
                } else if (selectedImageAlbum != null) {
                        selectedImageAlbum = null
                } else {
                        searchQuery = "" // Reset search query when navigating back
                        when (currentView) {
                                ExplorerView.HOME -> {
                                        onBackToPC()
                                }
                                ExplorerView.DIRECTORY -> {
                                        val isAtAnyStorageRoot =
                                                storagesList.any {
                                                        it.path == currentDirectory.absolutePath
                                                }
                                        if (isAtAnyStorageRoot ||
                                                        currentDirectory.parentFile == null
                                        ) {
                                                currentView = ExplorerView.HOME
                                        } else {
                                                val parent = currentDirectory.parentFile
                                                if (parent.absolutePath == "/storage" ||
                                                                parent.absolutePath == "/" ||
                                                                parent.absolutePath ==
                                                                        "/storage/emulated"
                                                ) {
                                                        currentView = ExplorerView.HOME
                                                } else {
                                                        directoryFiles =
                                                                emptyList() // Clear list to prevent
                                                        // flash of old content
                                                        currentDirectory = parent
                                                }
                                        }
                                }
                                else -> {
                                        currentView = ExplorerView.HOME
                                }
                        }
                }
        }

        // Handles back pressed gestures beautifully
        BackHandler { handleBackNavigation() }

        // File Operations:
        fun renameFile(file: File, newName: String) {
                val dest = File(file.parentFile, newName)
                if (dest.exists()) {
                        Toast.makeText(
                                        context,
                                        "A file with this name already exists",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                        return
                }
                if (file.renameTo(dest)) {
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                        refreshDirectory()
                        loadRecentFiles()
                        loadCategoryFiles()
                        loadFavorites()
                } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                }
        }

        fun createFolder(name: String) {
                val folder = File(currentDirectory, name)
                if (folder.exists()) {
                        Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
                        return
                }
                if (folder.mkdirs()) {
                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                        refreshDirectory()
                        loadRecentFiles()
                        loadCategoryFiles()
                        loadFavorites()
                } else {
                        Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT)
                                .show()
                }
        }

        fun deleteFiles(files: List<File>) {
                filesToDelete = files
        }

        fun performDelete(files: List<File>) {
                var count = 0
                files.forEach { file ->
                        if (LocalTrashManager.moveToTrash(context, file)) {
                                count++
                        }
                }
                Toast.makeText(context, "Moved $count items to Recycle Bin", Toast.LENGTH_SHORT)
                        .show()
                selectedFiles.clear()
                isMultiSelectMode = false
                refreshDirectory()
                loadRecentFiles()
                loadCategoryFiles()
                loadFavorites()
        }

        fun copyOrMoveFiles(files: List<File>, isMove: Boolean) {
                val count = files.size
                copiedFiles.clear()
                copiedFiles.addAll(files)
                isMoveOperation = isMove
                selectedFiles.clear()
                isMultiSelectMode = false
                val actionText = if (isMove) "cut" else "copied"
                Toast.makeText(context, "$count items $actionText to clipboard", Toast.LENGTH_SHORT)
                        .show()
        }

        fun pasteFiles() {
                if (copiedFiles.isEmpty()) return
                coroutineScope.launch(Dispatchers.IO) {
                        var count = 0
                        copiedFiles.forEach { srcFile ->
                                val destFile = File(currentDirectory, srcFile.name)
                                try {
                                        val success =
                                                if (isMoveOperation) {
                                                        if (srcFile.renameTo(destFile)) {
                                                                true
                                                        } else {
                                                                if (srcFile.isDirectory) {
                                                                        srcFile.copyRecursively(
                                                                                destFile,
                                                                                overwrite = true
                                                                        ) &&
                                                                                srcFile.deleteRecursively()
                                                                } else {
                                                                        srcFile.copyTo(
                                                                                        destFile,
                                                                                        overwrite =
                                                                                                true
                                                                                )
                                                                                .exists() &&
                                                                                srcFile.delete()
                                                                }
                                                        }
                                                } else {
                                                        if (srcFile.isDirectory) {
                                                                srcFile.copyRecursively(
                                                                        destFile,
                                                                        overwrite = true
                                                                )
                                                        } else {
                                                                srcFile.copyTo(
                                                                                destFile,
                                                                                overwrite = true
                                                                        )
                                                                        .exists()
                                                        }
                                                }
                                        if (success) count++
                                } catch (e: Exception) {
                                        Log.e("Explorer", "Failed to paste file", e)
                                }
                        }
                        withContext(Dispatchers.Main) {
                                Toast.makeText(
                                                context,
                                                "Pasted $count items successfully",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                                if (isMoveOperation) copiedFiles.clear()
                                refreshDirectory()
                                loadRecentFiles()
                                loadCategoryFiles()
                                loadFavorites()
                        }
                }
        }

        fun compressFiles(files: List<File>, zipName: String) {
                coroutineScope.launch(Dispatchers.IO) {
                        val targetZip =
                                File(
                                        currentDirectory,
                                        if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
                                )
                        try {
                                ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZip)))
                                        .use { zos ->
                                                files.forEach { file ->
                                                        fun zip(f: File, entryPath: String) {
                                                                if (f.isDirectory) {
                                                                        val children = f.listFiles()
                                                                        if (children.isNullOrEmpty()
                                                                        ) {
                                                                                zos.putNextEntry(
                                                                                        ZipEntry(
                                                                                                "$entryPath/"
                                                                                        )
                                                                                )
                                                                                zos.closeEntry()
                                                                        } else {
                                                                                children.forEach {
                                                                                        child ->
                                                                                        zip(
                                                                                                child,
                                                                                                "$entryPath/${child.name}"
                                                                                        )
                                                                                }
                                                                        }
                                                                } else {
                                                                        zos.putNextEntry(
                                                                                ZipEntry(entryPath)
                                                                        )
                                                                        f.inputStream().use { input
                                                                                ->
                                                                                input.copyTo(zos)
                                                                        }
                                                                        zos.closeEntry()
                                                                }
                                                        }
                                                        zip(file, file.name)
                                                }
                                        }
                                withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        "Archive compressed successfully",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        refreshDirectory()
                                        loadRecentFiles()
                                        loadCategoryFiles()
                                        loadFavorites()
                                }
                        } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        "Compression failed: ${e.localizedMessage}",
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                }
                        }
                }
        }

        fun extractZip(file: File) {
                coroutineScope.launch(Dispatchers.IO) {
                        val outputFolder = File(currentDirectory, file.nameWithoutExtension)
                        if (!outputFolder.exists()) outputFolder.mkdirs()
                        try {
                                ZipFile(file).use { zip ->
                                        val entries = zip.entries()
                                        while (entries.hasMoreElements()) {
                                                val entry = entries.nextElement()
                                                val destFile = File(outputFolder, entry.name)
                                                if (entry.isDirectory) {
                                                        destFile.mkdirs()
                                                } else {
                                                        destFile.parentFile?.mkdirs()
                                                        zip.getInputStream(entry).use { input ->
                                                                destFile.outputStream().use { output
                                                                        ->
                                                                        input.copyTo(output)
                                                                }
                                                        }
                                                }
                                        }
                                }
                                withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        "Archive extracted successfully",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        refreshDirectory()
                                        loadRecentFiles()
                                        loadCategoryFiles()
                                        loadFavorites()
                                }
                        } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        "Extraction failed: ${e.localizedMessage}",
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                }
                        }
                }
        }

        fun shareFiles(files: List<File>) {
                try {
                        val uris = ArrayList<Uri>()
                        files.forEach { file ->
                                val uri =
                                        FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                        )
                                uris.add(uri)
                        }
                        if (uris.isEmpty()) return
                        val intent =
                                Intent().apply {
                                        action =
                                                if (uris.size == 1) Intent.ACTION_SEND
                                                else Intent.ACTION_SEND_MULTIPLE
                                        if (uris.size == 1) {
                                                putExtra(Intent.EXTRA_STREAM, uris.first())
                                                type = externalMimeTypeForFile(files.first())
                                        } else {
                                                putParcelableArrayListExtra(
                                                        Intent.EXTRA_STREAM,
                                                        uris
                                                )
                                                type = "*/*"
                                        }
                                        clipData =
                                                ClipData.newUri(
                                                        context.contentResolver,
                                                        files.first().name,
                                                        uris.first()
                                                ).apply {
                                                        uris.drop(1).forEach { uri ->
                                                                addItem(ClipData.Item(uri))
                                                        }
                                                }
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                        context.startActivity(Intent.createChooser(intent, "Share files via"))
                } catch (e: Exception) {
                        Toast.makeText(
                                        context,
                                        "Share failed: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                }
        }

        // Permission Requester launcher
        fun launchPermissionIntent() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                                val intent =
                                        Intent(
                                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                )
                                                .apply {
                                                        data =
                                                                Uri.parse(
                                                                        "package:${context.packageName}"
                                                                )
                                                }
                                context.startActivity(intent)
                                Toast.makeText(
                                                context,
                                                "Please allow All Files access and reopen",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                        } catch (e: Exception) {
                                val intent =
                                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                        }
                } else {
                        // Under Android 11, request regular permissions
                        Toast.makeText(
                                        context,
                                        "Please grant storage permissions in App Info Settings",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                }
        }

        // Helper for sorting list
        fun sortFileList(
                list: List<File>,
                activeSortType: FileSortType = sortType,
                activeSortAscending: Boolean = sortAscending
        ): List<File> {
                val filtered =
                        list.filter {
                                (showHiddenFiles || !it.name.startsWith(".")) &&
                                        (searchQuery.isEmpty() ||
                                                it.name.contains(searchQuery, ignoreCase = true))
                        }

                val comparator =
                        when (activeSortType) {
                                FileSortType.NAME -> compareBy<File> { it.name.lowercase() }
                                FileSortType.DATE -> compareBy { it.lastModified() }
                                FileSortType.SIZE ->
                                        compareBy { if (it.isDirectory) 0L else it.length() }
                                FileSortType.TYPE ->
                                        compareBy {
                                                if (it.isDirectory) "" else it.extension.lowercase()
                                        }
                        }

                val sorted = filtered.sortedWith(comparator)

                // Group folders before files
                val folders = sorted.filter { it.isDirectory }
                val files = sorted.filter { !it.isDirectory }

                return if (activeSortAscending) {
                        folders + files
                } else {
                        (folders.reversed()) + (files.reversed())
                }
        }

        // Core layout
        Box(modifier = modifier.fillMaxSize().background(FluentTheme.colors.pageBg)) {
                if (!hasPermission) {
                        // Premium permission request guide
                        Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Permission needed",
                                        tint = FluentTheme.colors.accent,
                                        modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                        text = "Storage Permission Required",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FluentTheme.colors.textColor,
                                        textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                        text =
                                                "To access your mobile folders, copy, move, compress, extract, and display local files in our gorgeous Fluent Explorer, OmniSearch requires All Files Access permission.",
                                        fontSize = 14.sp,
                                        color = FluentTheme.colors.textMuted,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(28.dp))
                                Button(
                                        onClick = { launchPermissionIntent() },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = FluentTheme.colors.accent
                                                ),
                                        shape = RoundedCornerShape(FluentTheme.dims.controlRadius),
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                        Text(
                                                "Grant Permission",
                                                color = FluentTheme.colors.onAccent,
                                                fontWeight = FontWeight.SemiBold
                                        )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                TextButton(onClick = onBackToPC) {
                                        Text("Go Back", color = FluentTheme.colors.accent)
                                }
                        }
                } else {
                        // Main views
                        Column(modifier = Modifier.fillMaxSize()) {
                                // Header (Common across pages)
                                val isImagesCategory =
                                        currentView == ExplorerView.CATEGORY &&
                                                currentCategoryName == "Images"
                                ExplorerHeader(
                                        currentView = currentView,
                                        currentDirectory = currentDirectory,
                                        currentCategoryName =
                                                selectedImageAlbum?.name ?: currentCategoryName,
                                        searchQuery = searchQuery,
                                        onSearchQueryChange = { searchQuery = it },
                                        onBack = { handleBackNavigation() },
                                        showHiddenFiles = showHiddenFiles,
                                        onToggleHiddenFiles = { value ->
                                                coroutineScope.launch {
                                                        prefsManager.saveShowHiddenFiles(value)
                                                }
                                        },
                                        sortType = if (isImagesCategory) imageSortType else sortType,
                                        onSortTypeChange = { value ->
                                                coroutineScope.launch {
                                                        if (isImagesCategory) {
                                                                prefsManager.saveImageSortType(
                                                                        value.name
                                                                )
                                                        } else {
                                                                prefsManager.saveSortType(
                                                                        value.name
                                                                )
                                                        }
                                                }
                                        },
                                        sortAscending =
                                                if (isImagesCategory) imageSortAscending
                                                else sortAscending,
                                        onToggleSortOrder = {
                                                coroutineScope.launch {
                                                        if (isImagesCategory) {
                                                                prefsManager
                                                                        .saveImageSortAscending(
                                                                                !imageSortAscending
                                                                        )
                                                        } else {
                                                                prefsManager.saveSortAscending(
                                                                        !sortAscending
                                                                )
                                                        }
                                                }
                                        },
                                        onCreateFolder = { showCreateFolderDialog = true },
                                        onBackToPC = onBackToPC,
                                        onThemesClick = onThemesClick,
                                        isVideoCategory =
                                                currentCategoryName == "Videos" &&
                                                        currentView == ExplorerView.CATEGORY,
                                        useVideoGrid = useVideoGrid,
                                        onToggleVideoGrid = {
                                                coroutineScope.launch {
                                                        prefsManager.saveUseVideoGrid(!useVideoGrid)
                                                }
                                        },
                                        isImageCategory = isImagesCategory,
                                        useImageAlbums = useImageAlbums,
                                        onToggleImageAlbums = {
                                                selectedImageAlbum = null
                                                coroutineScope.launch {
                                                        prefsManager.saveUseImageAlbums(
                                                                !useImageAlbums
                                                        )
                                                }
                                        },
                                        isConnectedToDesktop = isConnectedToDesktop,
                                        onSendToDesktop = {
                                                val filesToSend =
                                                        selectedFiles.filter { it.isFile }.toList()
                                                if (filesToSend.isNotEmpty()) {
                                                        syncViewModel.sendFilesToDesktop(
                                                                context,
                                                                filesToSend
                                                        )
                                                        selectedFiles.clear()
                                                        isMultiSelectMode = false
                                                } else {
                                                        android.widget.Toast.makeText(
                                                                        context,
                                                                        "Select files first (folders cannot be sent)",
                                                                        android.widget.Toast
                                                                                .LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        },
                                        hasSelectedFiles = selectedFiles.isNotEmpty()
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                        when (currentView) {
                                                ExplorerView.HOME -> {
                                                        HomeDashboard(
                                                                onNavigateToDir = {
                                                                        searchQuery = ""
                                                                        directoryFiles = emptyList()
                                                                        currentDirectory = it
                                                                        currentView =
                                                                                ExplorerView
                                                                                        .DIRECTORY
                                                                },
                                                                onNavigateToCategory = { name, exts
                                                                        ->
                                                                        searchQuery = ""
                                                                        selectedImageAlbum = null
                                                                        categoryFilesList =
                                                                                emptyList()
                                                                        currentCategoryName = name
                                                                        currentCategoryExtensions =
                                                                                exts
                                                                        currentView =
                                                                                ExplorerView
                                                                                        .CATEGORY
                                                                },
                                                                onNavigateToTrash = {
                                                                        searchQuery = ""
                                                                        trashItems = emptyList()
                                                                        currentView =
                                                                                ExplorerView.TRASH
                                                                },
                                                                onNavigateToFavorites = {
                                                                        searchQuery = ""
                                                                        favoritesList = emptyList()
                                                                        currentView =
                                                                                ExplorerView
                                                                                        .FAVORITES
                                                                },
                                                                recentFiles = recentFilesList,
                                                                onOpenFile = { file ->
                                                                        openLocalFile(
                                                                                file = file,
                                                                                context = context,
                                                                                onPdfPreview = {
                                                                                        previewPdfFile =
                                                                                                it
                                                                                },
                                                                                onVideoPlay = {
                                                                                        previewVideoFile =
                                                                                                it
                                                                                },
                                                                                onImagePreview = {
                                                                                        previewImageFiles =
                                                                                                listOf(
                                                                                                        file
                                                                                                )
                                                                                        previewImageIndex =
                                                                                                0
                                                                                },
                                                                                onApkView = {
                                                                                        showApkDetailsDialog =
                                                                                                it
                                                                                }
                                                                        )
                                                                },
                                                                storages = storagesList,
                                                                onAnalyseStorageClick = {
                                                                        showStorageAnalysisDialog =
                                                                                true
                                                                }
                                                        )
                                                }
                                                ExplorerView.DIRECTORY -> {
                                                        val sorted =
                                                                remember(
                                                                        directoryFiles,
                                                                        searchQuery,
                                                                        showHiddenFiles,
                                                                        sortType,
                                                                        sortAscending
                                                                ) { sortFileList(directoryFiles) }
                                                        Column(modifier = Modifier.fillMaxSize()) {
                                                                PathBreadcrumbs(
                                                                        currentDirectory =
                                                                                currentDirectory,
                                                                        onNavigateToDir = {
                                                                                directoryFiles =
                                                                                        emptyList()
                                                                                currentDirectory =
                                                                                        it
                                                                        },
                                                                        onNavigateHome = {
                                                                                currentView =
                                                                                        ExplorerView
                                                                                                .HOME
                                                                        }
                                                                )

                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        16.dp,
                                                                                                vertical =
                                                                                                        6.dp
                                                                                        ),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                modifier =
                                                                                        Modifier
                                                                                                .clickable {
                                                                                                        coroutineScope
                                                                                                                .launch {
                                                                                                                        prefsManager
                                                                                                                                .saveShowHiddenFiles(
                                                                                                                                        !showHiddenFiles
                                                                                                                                )
                                                                                                                }
                                                                                                }
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                if (showHiddenFiles
                                                                                                )
                                                                                                        "All (with hidden)"
                                                                                                else
                                                                                                        "All",
                                                                                        color =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.7f
                                                                                                        ),
                                                                                        fontSize =
                                                                                                13.sp,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Medium
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        2.dp
                                                                                                )
                                                                                )
                                                                                Icon(
                                                                                        imageVector =
                                                                                                Icons.Default
                                                                                                        .ArrowDropDown,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.7f
                                                                                                        ),
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        16.dp
                                                                                                )
                                                                                )
                                                                        }

                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                modifier =
                                                                                        Modifier
                                                                                                .clickable {
                                                                                                        coroutineScope
                                                                                                                .launch {
                                                                                                                        prefsManager
                                                                                                                                .saveSortAscending(
                                                                                                                                        !sortAscending
                                                                                                                                )
                                                                                                                }
                                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        imageVector =
                                                                                                if (sortAscending
                                                                                                )
                                                                                                        Icons.Default
                                                                                                                .ArrowUpward
                                                                                                else
                                                                                                        Icons.Default
                                                                                                                .ArrowDownward,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.7f
                                                                                                        ),
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        14.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        4.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                when (sortType
                                                                                                ) {
                                                                                                        FileSortType
                                                                                                                .NAME ->
                                                                                                                "Name"
                                                                                                        FileSortType
                                                                                                                .DATE ->
                                                                                                                "Date"
                                                                                                        FileSortType
                                                                                                                .SIZE ->
                                                                                                                "Size"
                                                                                                        FileSortType
                                                                                                                .TYPE ->
                                                                                                                "Type"
                                                                                                },
                                                                                        color =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.7f
                                                                                                        ),
                                                                                        fontSize =
                                                                                                13.sp,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Medium
                                                                                )
                                                                        }
                                                                }

                                                                Box(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                ) {
                                                                        DirectoryViewer(
                                                                                files = sorted,
                                                                                selectedFiles =
                                                                                        selectedFiles,
                                                                                isMultiSelectMode =
                                                                                        isMultiSelectMode,
                                                                                onToggleSelectMode = {
                                                                                        isMultiSelectMode =
                                                                                                it
                                                                                        if (!it)
                                                                                                selectedFiles
                                                                                                        .clear()
                                                                                },
                                                                                isLoading =
                                                                                        isLoading,
                                                                                onFileClick = { file
                                                                                        ->
                                                                                        if (isMultiSelectMode
                                                                                        ) {
                                                                                                if (selectedFiles
                                                                                                                .contains(
                                                                                                                        file
                                                                                                                )
                                                                                                ) {
                                                                                                        selectedFiles
                                                                                                                .remove(
                                                                                                                        file
                                                                                                                )
                                                                                                        if (selectedFiles
                                                                                                                        .isEmpty()
                                                                                                        )
                                                                                                                isMultiSelectMode =
                                                                                                                        false
                                                                                                } else {
                                                                                                        selectedFiles
                                                                                                                .add(
                                                                                                                        file
                                                                                                                )
                                                                                                }
                                                                                        } else {
                                                                                                if (file.isDirectory
                                                                                                ) {
                                                                                                        searchQuery =
                                                                                                                ""
                                                                                                        directoryFiles =
                                                                                                                emptyList()
                                                                                                        currentDirectory =
                                                                                                                file
                                                                                                } else {
                                                                                                        val imgs =
                                                                                                                sorted
                                                                                                                        .filter {
                                                                                                                                f
                                                                                                                                ->
                                                                                                                                !f.isDirectory &&
                                                                                                                                        f.extension
                                                                                                                                                .lowercase() in
                                                                                                                                                listOf(
                                                                                                                                                        "jpg",
                                                                                                                                                        "jpeg",
                                                                                                                                                        "png",
                                                                                                                                                        "webp",
                                                                                                                                                        "gif"
                                                                                                                                                )
                                                                                                                        }
                                                                                                        val startIdx =
                                                                                                                imgs.indexOf(
                                                                                                                        file
                                                                                                                )
                                                                                                        openLocalFile(
                                                                                                                file =
                                                                                                                        file,
                                                                                                                context =
                                                                                                                        context,
                                                                                                                onPdfPreview = {
                                                                                                                        previewPdfFile =
                                                                                                                                it
                                                                                                                },
                                                                                                                onVideoPlay = {
                                                                                                                        previewVideoFile =
                                                                                                                                it
                                                                                                                },
                                                                                                                onImagePreview = {
                                                                                                                        if (startIdx !=
                                                                                                                                        -1
                                                                                                                        ) {
                                                                                                                                previewImageFiles =
                                                                                                                                        imgs
                                                                                                                                previewImageIndex =
                                                                                                                                        startIdx
                                                                                                                        } else {
                                                                                                                                previewImageFiles =
                                                                                                                                        listOf(
                                                                                                                                                file
                                                                                                                                        )
                                                                                                                                previewImageIndex =
                                                                                                                                        0
                                                                                                                        }
                                                                                                                },
                                                                                                                onApkView = {
                                                                                                                        showApkDetailsDialog =
                                                                                                                                it
                                                                                                                }
                                                                                                        )
                                                                                                }
                                                                                        }
                                                                                },
                                                                                onFileLongClick = {
                                                                                        file ->
                                                                                        if (!isMultiSelectMode
                                                                                        ) {
                                                                                                isMultiSelectMode =
                                                                                                        true
                                                                                                selectedFiles
                                                                                                        .add(
                                                                                                                file
                                                                                                        )
                                                                                        }
                                                                                },
                                                                                isFavorited = {
                                                                                        isFavorited(
                                                                                                it
                                                                                        )
                                                                                },
                                                                                onToggleFavorite = {
                                                                                        toggleFavorite(
                                                                                                it
                                                                                        )
                                                                                },
                                                                                onRename = {
                                                                                        showRenameDialog =
                                                                                                it
                                                                                },
                                                                                onDelete = {
                                                                                        deleteFiles(
                                                                                                listOf(
                                                                                                        it
                                                                                                )
                                                                                        )
                                                                                },
                                                                                onCompress = {
                                                                                        showCompressDialog =
                                                                                                listOf(
                                                                                                        it
                                                                                                )
                                                                                },
                                                                                onExtract = {
                                                                                        extractZip(
                                                                                                it
                                                                                        )
                                                                                },
                                                                                onShare = {
                                                                                        shareFiles(
                                                                                                listOf(
                                                                                                        it
                                                                                                )
                                                                                        )
                                                                                },
                                                                                onDetails = {
                                                                                        showDetailsDialog =
                                                                                                it
                                                                                },
                                                                                onSendToDesktop = {
                                                                                        file ->
                                                                                        if (isConnectedToDesktop &&
                                                                                                        file.isFile
                                                                                        ) {
                                                                                                syncViewModel
                                                                                                        .sendFilesToDesktop(
                                                                                                                context,
                                                                                                                listOf(
                                                                                                                        file
                                                                                                                )
                                                                                                        )
                                                                                        }
                                                                                },
                                                                                isConnectedToDesktop =
                                                                                        isConnectedToDesktop
                                                                        )
                                                                }
                                                        }
                                                }
                                                ExplorerView.TRASH -> {
                                                        TrashViewer(
                                                                trashItems = trashItems,
                                                                onRestore = { item ->
                                                                        if (LocalTrashManager
                                                                                        .restoreItem(
                                                                                                context,
                                                                                                item
                                                                                        )
                                                                        ) {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "Restored: ${item.name}",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                                loadTrash()
                                                                        } else {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "Restore failed",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                        }
                                                                },
                                                                onDeletePermanently = { item ->
                                                                        if (LocalTrashManager
                                                                                        .deletePermanently(
                                                                                                context,
                                                                                                item
                                                                                        )
                                                                        ) {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "Deleted permanently",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                                loadTrash()
                                                                        }
                                                                },
                                                                onEmptyTrash = {
                                                                        LocalTrashManager
                                                                                .emptyTrash(context)
                                                                        Toast.makeText(
                                                                                        context,
                                                                                        "Recycle Bin cleared",
                                                                                        Toast.LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                        loadTrash()
                                                                },
                                                                onItemClick = { item ->
                                                                        val trashFile =
                                                                                File(
                                                                                        File(
                                                                                                context.filesDir,
                                                                                                "omnisearch_trash"
                                                                                        ),
                                                                                        item.id
                                                                                )
                                                                        if (trashFile.exists()) {
                                                                                val ext =
                                                                                        item.name
                                                                                                .substringAfterLast(
                                                                                                        '.'
                                                                                                )
                                                                                                .lowercase()
                                                                                when (ext) {
                                                                                        "png",
                                                                                        "jpg",
                                                                                        "jpeg",
                                                                                        "webp",
                                                                                        "gif",
                                                                                        "bmp" -> {
                                                                                                previewImageFiles =
                                                                                                        listOf(
                                                                                                                trashFile
                                                                                                        )
                                                                                                previewImageIndex =
                                                                                                        0
                                                                                        }
                                                                                        "mp4",
                                                                                        "mkv",
                                                                                        "webm",
                                                                                        "avi",
                                                                                        "3gp",
                                                                                        "mov" -> {
                                                                                                previewVideoFile =
                                                                                                        trashFile
                                                                                        }
                                                                                        else -> {
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "Preview not available for this file type",
                                                                                                                Toast.LENGTH_SHORT
                                                                                                        )
                                                                                                        .show()
                                                                                        }
                                                                                }
                                                                        }
                                                                }
                                                        )
                                                }
                                                ExplorerView.FAVORITES -> {
                                                        val sorted =
                                                                remember(
                                                                        favoritesList,
                                                                        searchQuery,
                                                                        sortType,
                                                                        sortAscending
                                                                ) { sortFileList(favoritesList) }
                                                        DirectoryViewer(
                                                                files = sorted,
                                                                selectedFiles = selectedFiles,
                                                                isMultiSelectMode =
                                                                        isMultiSelectMode,
                                                                onToggleSelectMode = {
                                                                        isMultiSelectMode = it
                                                                        if (!it)
                                                                                selectedFiles
                                                                                        .clear()
                                                                },
                                                                isLoading = isLoading,
                                                                onFileClick = { file ->
                                                                        if (file.isDirectory) {
                                                                                searchQuery = ""
                                                                                directoryFiles =
                                                                                        emptyList()
                                                                                currentDirectory =
                                                                                        file
                                                                                currentView =
                                                                                        ExplorerView
                                                                                                .DIRECTORY
                                                                        } else {
                                                                                openLocalFile(
                                                                                        file = file,
                                                                                        context =
                                                                                                context,
                                                                                        onPdfPreview = {
                                                                                                previewPdfFile =
                                                                                                        it
                                                                                        },
                                                                                        onVideoPlay = {
                                                                                                previewVideoFile =
                                                                                                        it
                                                                                        },
                                                                                        onImagePreview = {
                                                                                                previewImageFiles =
                                                                                                        listOf(
                                                                                                                file
                                                                                                        )
                                                                                                previewImageIndex =
                                                                                                        0
                                                                                        },
                                                                                        onApkView = {
                                                                                                showApkDetailsDialog =
                                                                                                        it
                                                                                        }
                                                                                )
                                                                        }
                                                                },
                                                                onFileLongClick = {},
                                                                isFavorited = { true },
                                                                onToggleFavorite = {
                                                                        toggleFavorite(it)
                                                                },
                                                                onRename = {
                                                                        showRenameDialog = it
                                                                },
                                                                onDelete = {
                                                                        deleteFiles(listOf(it))
                                                                },
                                                                onCompress = {
                                                                        showCompressDialog =
                                                                                listOf(it)
                                                                },
                                                                onExtract = { extractZip(it) },
                                                                onShare = {
                                                                        shareFiles(listOf(it))
                                                                },
                                                                onDetails = {
                                                                        showDetailsDialog = it
                                                                },
                                                                onSendToDesktop = { file ->
                                                                        if (isConnectedToDesktop &&
                                                                                        file.isFile
                                                                        ) {
                                                                                syncViewModel
                                                                                        .sendFilesToDesktop(
                                                                                                context,
                                                                                                listOf(
                                                                                                        file
                                                                                                )
                                                                                        )
                                                                        }
                                                                },
                                                                isConnectedToDesktop =
                                                                        isConnectedToDesktop
                                                        )
                                                }
                                                ExplorerView.CATEGORY -> {
                                                        val isImagesCategory =
                                                                currentCategoryName == "Images"
                                                        val activeCategorySortType =
                                                                if (isImagesCategory)
                                                                        imageSortType
                                                                else sortType
                                                        val activeCategorySortAscending =
                                                                if (isImagesCategory)
                                                                        imageSortAscending
                                                                else sortAscending
                                                        val sorted =
                                                                remember(
                                                                        categoryFilesList,
                                                                        searchQuery,
                                                                        activeCategorySortType,
                                                                        activeCategorySortAscending
                                                                ) {
                                                                        sortFileList(
                                                                                categoryFilesList,
                                                                                activeCategorySortType,
                                                                                activeCategorySortAscending
                                                                        )
                                                                }
                                                        if (currentCategoryName == "Images") {
                                                                val imageAlbums =
                                                                        remember(
                                                                                categoryFilesList,
                                                                                showHiddenFiles,
                                                                                searchQuery,
                                                                                imageSortType,
                                                                                imageSortAscending
                                                                        ) {
                                                                                val albums =
                                                                                        buildImageAlbums(
                                                                                                categoryFilesList,
                                                                                                showHiddenFiles
                                                                                        )
                                                                                                .filter {
                                                                                                        searchQuery
                                                                                                                .isBlank() ||
                                                                                                                it.name
                                                                                                                        .contains(
                                                                                                                                searchQuery,
                                                                                                                                ignoreCase =
                                                                                                                                        true
                                                                                                                        )
                                                                                                }
                                                                                sortImageAlbums(
                                                                                        albums,
                                                                                        imageSortType,
                                                                                        imageSortAscending
                                                                                )
                                                                        }
                                                                if (useImageAlbums &&
                                                                                selectedImageAlbum ==
                                                                                        null
                                                                ) {
                                                                        val albumsGridState =
                                                                                rememberLazyGridState()
                                                                        ImageAlbumsGrid(
                                                                                albums =
                                                                                        imageAlbums,
                                                                                gridState =
                                                                                        albumsGridState,
                                                                                isLoading =
                                                                                        isLoading,
                                                                                onAlbumClick = {
                                                                                        selectedImageAlbum =
                                                                                                it
                                                                                        searchQuery =
                                                                                                ""
                                                                                },
                                                                                modifier =
                                                                                        Modifier
                                                                                                .fillMaxSize()
                                                                        )
                                                                } else {
                                                                        val imageFiles =
                                                                                selectedImageAlbum
                                                                                        ?.let {
                                                                                                album
                                                                                                ->
                                                                                                sorted.filter {
                                                                                                        file
                                                                                                        ->
                                                                                                        file.parentFile
                                                                                                                ?.absolutePath ==
                                                                                                                album.directory
                                                                                                                        .absolutePath
                                                                                                }
                                                                                        }
                                                                                        ?: sorted
                                                                if (imageFiles.isEmpty()) {
                                                                        if (!isLoading) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.fillMaxSize(),
                                                                                        contentAlignment =
                                                                                                Alignment
                                                                                                        .Center
                                                                                ) {
                                                                                        Column(
                                                                                                horizontalAlignment =
                                                                                                        Alignment
                                                                                                                .CenterHorizontally
                                                                                        ) {
                                                                                                Icon(
                                                                                                        imageVector =
                                                                                                                Icons.Outlined
                                                                                                                        .Image,
                                                                                                        contentDescription =
                                                                                                                null,
                                                                                                        tint =
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .textMuted,
                                                                                                        modifier =
                                                                                                                Modifier.size(
                                                                                                                        64.dp
                                                                                                                )
                                                                                                )
                                                                                                Spacer(
                                                                                                        modifier =
                                                                                                                Modifier.height(
                                                                                                                        12.dp
                                                                                                                )
                                                                                                )
                                                                                                Text(
                                                                                                        "No images found",
                                                                                                        color =
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .textColor,
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .SemiBold
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                } else {
                                                                        // Image Gallery Grid
                                                                        val imagesGridState =
                                                                                rememberLazyGridState()
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.fillMaxSize()
                                                                        ) {
                                                                                LazyVerticalGrid(
                                                                                        state =
                                                                                                imagesGridState,
                                                                                        columns =
                                                                                                GridCells
                                                                                                        .Adaptive(
                                                                                                                110.dp
                                                                                                        ),
                                                                                        contentPadding =
                                                                                                PaddingValues(
                                                                                                        10.dp
                                                                                                ),
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .spacedBy(
                                                                                                                8.dp
                                                                                                        ),
                                                                                        verticalArrangement =
                                                                                                Arrangement
                                                                                                        .spacedBy(
                                                                                                                8.dp
                                                                                                        ),
                                                                                        modifier =
                                                                                                Modifier.fillMaxSize()
                                                                                ) {
                                                                                        itemsIndexed(
                                                                                                imageFiles,
                                                                                                key = {
                                                                                                        _,
                                                                                                        f
                                                                                                        ->
                                                                                                        f.absolutePath
                                                                                                }
                                                                                        ) {
                                                                                                index,
                                                                                                file
                                                                                                ->
                                                                                                val isSelected =
                                                                                                        selectedFiles
                                                                                                                .contains(
                                                                                                                        file
                                                                                                                )
                                                                                                Box(
                                                                                                        modifier =
                                                                                                                Modifier.aspectRatio(
                                                                                                                                1f
                                                                                                                        )
                                                                                                                        .clip(
                                                                                                                                RoundedCornerShape(
                                                                                                                                        FluentTheme
                                                                                                                                                .dims
                                                                                                                                                .surfaceRadius
                                                                                                                                )
                                                                                                                        )
                                                                                                                        .border(
                                                                                                                                width =
                                                                                                                                        if (isSelected
                                                                                                                                        )
                                                                                                                                                3.dp
                                                                                                                                        else
                                                                                                                                                1.dp,
                                                                                                                                color =
                                                                                                                                        if (isSelected
                                                                                                                                        )
                                                                                                                                                FluentTheme
                                                                                                                                                        .colors
                                                                                                                                                        .accent
                                                                                                                                        else
                                                                                                                                                FluentTheme
                                                                                                                                                        .colors
                                                                                                                                                        .panelBorder,
                                                                                                                                shape =
                                                                                                                                        RoundedCornerShape(
                                                                                                                                                FluentTheme
                                                                                                                                                        .dims
                                                                                                                                                        .surfaceRadius
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        .combinedClickable(
                                                                                                                                onClick = {
                                                                                                                                        if (isMultiSelectMode
                                                                                                                                        ) {
                                                                                                                                                if (selectedFiles
                                                                                                                                                                .contains(
                                                                                                                                                                        file
                                                                                                                                                                )
                                                                                                                                                ) {
                                                                                                                                                        selectedFiles
                                                                                                                                                                .remove(
                                                                                                                                                                        file
                                                                                                                                                                )
                                                                                                                                                        if (selectedFiles
                                                                                                                                                                        .isEmpty()
                                                                                                                                                        )
                                                                                                                                                                isMultiSelectMode =
                                                                                                                                                                        false
                                                                                                                                                } else {
                                                                                                                                                        selectedFiles
                                                                                                                                                                .add(
                                                                                                                                                                        file
                                                                                                                                                                )
                                                                                                                                                }
                                                                                                                                                } else {
                                                                                                                                                        previewImageFiles =
                                                                                                                                                        imageFiles
                                                                                                                                                previewImageIndex =
                                                                                                                                                        index
                                                                                                                                        }
                                                                                                                                },
                                                                                                                                onLongClick = {
                                                                                                                                        if (!isMultiSelectMode
                                                                                                                                        ) {
                                                                                                                                                isMultiSelectMode =
                                                                                                                                                        true
                                                                                                                                                selectedFiles
                                                                                                                                                        .add(
                                                                                                                                                                file
                                                                                                                                                        )
                                                                                                                                        }
                                                                                                                                }
                                                                                                                        )
                                                                                                ) {
                                                                                                        AsyncImage(
                                                                                                                model =
                                                                                                                        file,
                                                                                                                contentDescription =
                                                                                                                        file.name,
                                                                                                                contentScale =
                                                                                                                        ContentScale
                                                                                                                                .Crop,
                                                                                                                modifier =
                                                                                                                        Modifier.fillMaxSize()
                                                                                                        )
                                                                                                        if (isMultiSelectMode
                                                                                                        ) {
                                                                                                                Checkbox(
                                                                                                                        checked =
                                                                                                                                isSelected,
                                                                                                                        onCheckedChange = {
                                                                                                                                if (selectedFiles
                                                                                                                                                .contains(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                ) {
                                                                                                                                        selectedFiles
                                                                                                                                                .remove(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                        if (selectedFiles
                                                                                                                                                        .isEmpty()
                                                                                                                                        )
                                                                                                                                                isMultiSelectMode =
                                                                                                                                                        false
                                                                                                                                } else {
                                                                                                                                        selectedFiles
                                                                                                                                                .add(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                }
                                                                                                                        },
                                                                                                                        colors =
                                                                                                                                CheckboxDefaults
                                                                                                                                        .colors(
                                                                                                                                                checkedColor =
                                                                                                                                                        FluentTheme
                                                                                                                                                                .colors
                                                                                                                                                                .accent
                                                                                                                                        ),
                                                                                                                        modifier =
                                                                                                                                Modifier.align(
                                                                                                                                                Alignment
                                                                                                                                                        .TopEnd
                                                                                                                                        )
                                                                                                                                        .padding(
                                                                                                                                                4.dp
                                                                                                                                        )
                                                                                                                )
                                                                                                        }
                                                                                                }
                                                                                        }
                                                                                }
                                                                                FastScrollbarGrid(
                                                                                        gridState =
                                                                                                imagesGridState,
                                                                                        modifier =
                                                                                                Modifier.align(
                                                                                                        Alignment
                                                                                                                .CenterEnd
                                                                                                )
                                                                                )
                                                                        }
                                                                }
                                                                }
                                                        } else if (currentCategoryName ==
                                                                        "Videos" && useVideoGrid
                                                        ) {
                                                                // Video Thumbnail Grid View
                                                                if (sorted.isEmpty()) {
                                                                        if (!isLoading) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.fillMaxSize(),
                                                                                        contentAlignment =
                                                                                                Alignment
                                                                                                        .Center
                                                                                ) {}
                                                                        }
                                                                } else {
                                                                        val videosGridState =
                                                                                rememberLazyGridState()
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.fillMaxSize()
                                                                        ) {
                                                                                LazyVerticalGrid(
                                                                                        state =
                                                                                                videosGridState,
                                                                                        columns =
                                                                                                GridCells
                                                                                                        .Adaptive(
                                                                                                                140.dp
                                                                                                        ),
                                                                                        contentPadding =
                                                                                                PaddingValues(
                                                                                                        10.dp
                                                                                                ),
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .spacedBy(
                                                                                                                8.dp
                                                                                                        ),
                                                                                        verticalArrangement =
                                                                                                Arrangement
                                                                                                        .spacedBy(
                                                                                                                8.dp
                                                                                                        ),
                                                                                        modifier =
                                                                                                Modifier.fillMaxSize()
                                                                                ) {
                                                                                        itemsIndexed(
                                                                                                sorted,
                                                                                                key = {
                                                                                                        _,
                                                                                                        f
                                                                                                        ->
                                                                                                        f.absolutePath
                                                                                                }
                                                                                        ) { _, file
                                                                                                ->
                                                                                                val isSelected =
                                                                                                        selectedFiles
                                                                                                                .contains(
                                                                                                                        file
                                                                                                                )
                                                                                                Box(
                                                                                                        modifier =
                                                                                                                Modifier.aspectRatio(
                                                                                                                                16f /
                                                                                                                                        9f
                                                                                                                        )
                                                                                                                        .clip(
                                                                                                                                RoundedCornerShape(
                                                                                                                                        FluentTheme
                                                                                                                                                .dims
                                                                                                                                                .surfaceRadius
                                                                                                                                )
                                                                                                                        )
                                                                                                                        .border(
                                                                                                                                width =
                                                                                                                                        if (isSelected
                                                                                                                                        )
                                                                                                                                                3.dp
                                                                                                                                        else
                                                                                                                                                1.dp,
                                                                                                                                color =
                                                                                                                                        if (isSelected
                                                                                                                                        )
                                                                                                                                                FluentTheme
                                                                                                                                                        .colors
                                                                                                                                                        .accent
                                                                                                                                        else
                                                                                                                                                FluentTheme
                                                                                                                                                        .colors
                                                                                                                                                        .panelBorder,
                                                                                                                                shape =
                                                                                                                                        RoundedCornerShape(
                                                                                                                                                FluentTheme
                                                                                                                                                        .dims
                                                                                                                                                        .surfaceRadius
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        .combinedClickable(
                                                                                                                                onClick = {
                                                                                                                                        if (isMultiSelectMode
                                                                                                                                        ) {
                                                                                                                                                if (selectedFiles
                                                                                                                                                                .contains(
                                                                                                                                                                        file
                                                                                                                                                                )
                                                                                                                                                ) {
                                                                                                                                                        selectedFiles
                                                                                                                                                                .remove(
                                                                                                                                                                        file
                                                                                                                                                                )
                                                                                                                                                        if (selectedFiles
                                                                                                                                                                        .isEmpty()
                                                                                                                                                        )
                                                                                                                                                                isMultiSelectMode =
                                                                                                                                                                        false
                                                                                                                                                } else {
                                                                                                                                                        selectedFiles
                                                                                                                                                                .add(
                                                                                                                                                                        file
                                                                                                                                                                )
                                                                                                                                                }
                                                                                                                                        } else {
                                                                                                                                                previewVideoFile =
                                                                                                                                                        file
                                                                                                                                        }
                                                                                                                                },
                                                                                                                                onLongClick = {
                                                                                                                                        if (!isMultiSelectMode
                                                                                                                                        ) {
                                                                                                                                                isMultiSelectMode =
                                                                                                                                                        true
                                                                                                                                                selectedFiles
                                                                                                                                                        .add(
                                                                                                                                                                file
                                                                                                                                                        )
                                                                                                                                        }
                                                                                                                                }
                                                                                                                        )
                                                                                                ) {
                                                                                                        // Load video thumbnail asynchronously with
                                                                                                        // caching and serialized concurrency
                                                                                                        var vidThumb by
                                                                                                                remember(
                                                                                                                        file
                                                                                                                ) {
                                                                                                                        mutableStateOf<
                                                                                                                                android.graphics.Bitmap?>(
                                                                                                                                VideoThumbnailCache
                                                                                                                                        .get(
                                                                                                                                                file.absolutePath
                                                                                                                                        )
                                                                                                                        )
                                                                                                                }
                                                                                                        var thumbLoaded by
                                                                                                                remember(
                                                                                                                        file
                                                                                                                ) {
                                                                                                                        mutableStateOf(
                                                                                                                                vidThumb !=
                                                                                                                                        null
                                                                                                                        )
                                                                                                                }
                                                                                                        LaunchedEffect(
                                                                                                                file
                                                                                                        ) {
                                                                                                                if (vidThumb ==
                                                                                                                                null
                                                                                                                ) {
                                                                                                                        kotlinx.coroutines
                                                                                                                                .withContext(
                                                                                                                                        kotlinx.coroutines
                                                                                                                                                .Dispatchers
                                                                                                                                                .IO
                                                                                                                                ) {
                                                                                                                                        vidThumb =
                                                                                                                                                VideoThumbnailCache
                                                                                                                                                        .getOrCreate(
                                                                                                                                                                file =
                                                                                                                                                                        file,
                                                                                                                                                                size =
                                                                                                                                                                        android.util
                                                                                                                                                                                .Size(
                                                                                                                                                                                        320,
                                                                                                                                                                                        240
                                                                                                                                                                                ),
                                                                                                                                                                isMicro =
                                                                                                                                                                        false
                                                                                                                                                        )
                                                                                                                                }
                                                                                                                        thumbLoaded =
                                                                                                                                true
                                                                                                                }
                                                                                                        }
                                                                                                        if (vidThumb !=
                                                                                                                        null
                                                                                                        ) {
                                                                                                                Image(
                                                                                                                        bitmap =
                                                                                                                                vidThumb!!
                                                                                                                                        .asImageBitmap(),
                                                                                                                        contentDescription =
                                                                                                                                file.name,
                                                                                                                        contentScale =
                                                                                                                                ContentScale
                                                                                                                                        .Crop,
                                                                                                                        modifier =
                                                                                                                                Modifier.fillMaxSize()
                                                                                                                )
                                                                                                        } else if (thumbLoaded
                                                                                                        ) {
                                                                                                                // ThumbnailUtils failed, fall back to Coil
                                                                                                                AsyncImage(
                                                                                                                        model =
                                                                                                                                file,
                                                                                                                        contentDescription =
                                                                                                                                file.name,
                                                                                                                        contentScale =
                                                                                                                                ContentScale
                                                                                                                                        .Crop,
                                                                                                                        modifier =
                                                                                                                                Modifier.fillMaxSize()
                                                                                                                )
                                                                                                        } else {
                                                                                                                // Loading placeholder
                                                                                                                Box(
                                                                                                                        modifier =
                                                                                                                                Modifier.fillMaxSize()
                                                                                                                                        .background(
                                                                                                                                                FluentTheme
                                                                                                                                                        .colors
                                                                                                                                                        .panelBorder
                                                                                                                                        ),
                                                                                                                        contentAlignment =
                                                                                                                                Alignment
                                                                                                                                        .Center
                                                                                                                ) {
                                                                                                                        CircularProgressIndicator(
                                                                                                                                modifier =
                                                                                                                                        Modifier.size(
                                                                                                                                                20.dp
                                                                                                                                        ),
                                                                                                                                color =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .accent,
                                                                                                                                strokeWidth =
                                                                                                                                        2.dp
                                                                                                                        )
                                                                                                                }
                                                                                                        }
                                                                                                        // Play icon overlay
                                                                                                        Box(
                                                                                                                modifier =
                                                                                                                        Modifier.fillMaxSize()
                                                                                                                                .background(
                                                                                                                                        Color.Black
                                                                                                                                                .copy(
                                                                                                                                                        alpha =
                                                                                                                                                                0.3f
                                                                                                                                                )
                                                                                                                                ),
                                                                                                                contentAlignment =
                                                                                                                        Alignment
                                                                                                                                .Center
                                                                                                        ) {
                                                                                                                Icon(
                                                                                                                        Icons.Default
                                                                                                                                .PlayCircle,
                                                                                                                        contentDescription =
                                                                                                                                "Play",
                                                                                                                        tint =
                                                                                                                                Color.White
                                                                                                                                        .copy(
                                                                                                                                                alpha =
                                                                                                                                                        0.85f
                                                                                                                                        ),
                                                                                                                        modifier =
                                                                                                                                Modifier.size(
                                                                                                                                        36.dp
                                                                                                                                )
                                                                                                                )
                                                                                                        }
                                                                                                        // File name overlay at bottom
                                                                                                        Box(
                                                                                                                modifier =
                                                                                                                        Modifier.fillMaxWidth()
                                                                                                                                .align(
                                                                                                                                        Alignment
                                                                                                                                                .BottomCenter
                                                                                                                                )
                                                                                                                                .background(
                                                                                                                                        Brush.verticalGradient(
                                                                                                                                                colors =
                                                                                                                                                        listOf(
                                                                                                                                                                Color.Transparent,
                                                                                                                                                                Color.Black
                                                                                                                                                                        .copy(
                                                                                                                                                                                alpha =
                                                                                                                                                                                        0.7f
                                                                                                                                                                        )
                                                                                                                                                        )
                                                                                                                                        )
                                                                                                                                )
                                                                                                                                .padding(
                                                                                                                                        horizontal =
                                                                                                                                                6.dp,
                                                                                                                                        vertical =
                                                                                                                                                4.dp
                                                                                                                                )
                                                                                                        ) {
                                                                                                                Text(
                                                                                                                        text =
                                                                                                                                file.name,
                                                                                                                        color =
                                                                                                                                Color.White,
                                                                                                                        fontSize =
                                                                                                                                10.sp,
                                                                                                                        maxLines =
                                                                                                                                1,
                                                                                                                        overflow =
                                                                                                                                TextOverflow
                                                                                                                                        .Ellipsis
                                                                                                                )
                                                                                                        }
                                                                                                        if (isMultiSelectMode
                                                                                                        ) {
                                                                                                                Checkbox(
                                                                                                                        checked =
                                                                                                                                isSelected,
                                                                                                                        onCheckedChange = {
                                                                                                                                if (selectedFiles
                                                                                                                                                .contains(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                ) {
                                                                                                                                        selectedFiles
                                                                                                                                                .remove(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                        if (selectedFiles
                                                                                                                                                        .isEmpty()
                                                                                                                                        )
                                                                                                                                                isMultiSelectMode =
                                                                                                                                                        false
                                                                                                                                } else {
                                                                                                                                        selectedFiles
                                                                                                                                                .add(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                }
                                                                                                                        },
                                                                                                                        colors =
                                                                                                                                CheckboxDefaults
                                                                                                                                        .colors(
                                                                                                                                                checkedColor =
                                                                                                                                                        FluentTheme
                                                                                                                                                                .colors
                                                                                                                                                                .accent
                                                                                                                                        ),
                                                                                                                        modifier =
                                                                                                                                Modifier.align(
                                                                                                                                                Alignment
                                                                                                                                                        .TopEnd
                                                                                                                                        )
                                                                                                                                        .padding(
                                                                                                                                                4.dp
                                                                                                                                        )
                                                                                                                )
                                                                                                        }
                                                                                                }
                                                                                        }
                                                                                }
                                                                                FastScrollbarGrid(
                                                                                        gridState =
                                                                                                videosGridState,
                                                                                        modifier =
                                                                                                Modifier.align(
                                                                                                        Alignment
                                                                                                                .CenterEnd
                                                                                                )
                                                                                )
                                                                        }
                                                                }
                                                        } else {
                                                                DirectoryViewer(
                                                                        files = sorted,
                                                                        selectedFiles =
                                                                                selectedFiles,
                                                                        isMultiSelectMode =
                                                                                isMultiSelectMode,
                                                                        onToggleSelectMode = {
                                                                                isMultiSelectMode =
                                                                                        it
                                                                                if (!it)
                                                                                        selectedFiles
                                                                                                .clear()
                                                                        },
                                                                        isLoading = isLoading,
                                                                        onFileClick = { file ->
                                                                                if (isMultiSelectMode
                                                                                ) {
                                                                                        if (selectedFiles
                                                                                                        .contains(
                                                                                                                file
                                                                                                        )
                                                                                        ) {
                                                                                                selectedFiles
                                                                                                        .remove(
                                                                                                                file
                                                                                                        )
                                                                                                if (selectedFiles
                                                                                                                .isEmpty()
                                                                                                )
                                                                                                        isMultiSelectMode =
                                                                                                                false
                                                                                        } else {
                                                                                                selectedFiles
                                                                                                        .add(
                                                                                                                file
                                                                                                        )
                                                                                        }
                                                                                } else {
                                                                                        openLocalFile(
                                                                                                file =
                                                                                                        file,
                                                                                                context =
                                                                                                        context,
                                                                                                onPdfPreview = {
                                                                                                        previewPdfFile =
                                                                                                                it
                                                                                                },
                                                                                                onVideoPlay = {
                                                                                                        previewVideoFile =
                                                                                                                it
                                                                                                },
                                                                                                onImagePreview = {
                                                                                                        previewImageFiles =
                                                                                                                listOf(
                                                                                                                        file
                                                                                                                )
                                                                                                        previewImageIndex =
                                                                                                                0
                                                                                                },
                                                                                                onApkView = {
                                                                                                        showApkDetailsDialog =
                                                                                                                it
                                                                                                }
                                                                                        )
                                                                                }
                                                                        },
                                                                        onFileLongClick = { file ->
                                                                                if (!isMultiSelectMode
                                                                                ) {
                                                                                        isMultiSelectMode =
                                                                                                true
                                                                                        selectedFiles
                                                                                                .add(
                                                                                                        file
                                                                                                )
                                                                                }
                                                                        },
                                                                        isFavorited = {
                                                                                isFavorited(it)
                                                                        },
                                                                        onToggleFavorite = {
                                                                                toggleFavorite(it)
                                                                        },
                                                                        onRename = {
                                                                                showRenameDialog =
                                                                                        it
                                                                        },
                                                                        onDelete = {
                                                                                deleteFiles(
                                                                                        listOf(it)
                                                                                )
                                                                        },
                                                                        onCompress = {
                                                                                showCompressDialog =
                                                                                        listOf(it)
                                                                        },
                                                                        onExtract = {
                                                                                extractZip(it)
                                                                        },
                                                                        onShare = {
                                                                                shareFiles(
                                                                                        listOf(it)
                                                                                )
                                                                        },
                                                                        onDetails = {
                                                                                showDetailsDialog =
                                                                                        it
                                                                        },
                                                                        onSendToDesktop = { file ->
                                                                                if (isConnectedToDesktop &&
                                                                                                file.isFile
                                                                                ) {
                                                                                        syncViewModel
                                                                                                .sendFilesToDesktop(
                                                                                                        context,
                                                                                                        listOf(
                                                                                                                file
                                                                                                        )
                                                                                                )
                                                                                }
                                                                        },
                                                                        isConnectedToDesktop =
                                                                                isConnectedToDesktop
                                                                )
                                                        }
                                                }
                                        }

                                        // Floating Clipboard/Action Bar for Multi-Select & Paste
                                        // operations
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible =
                                                        isMultiSelectMode ||
                                                                copiedFiles.isNotEmpty(),
                                                enter =
                                                        slideInVertically(initialOffsetY = { it }) +
                                                                fadeIn(),
                                                exit =
                                                        slideOutVertically(targetOffsetY = { it }) +
                                                                fadeOut(),
                                                modifier =
                                                        Modifier.align(Alignment.BottomCenter)
                                                                .padding(bottom = 16.dp)
                                        ) {
                                                Surface(
                                                        modifier =
                                                                Modifier.fillMaxWidth(0.9f)
                                                                        .height(64.dp),
                                                        shape = CircleShape,
                                                        color = FluentTheme.colors.panelBg,
                                                        border =
                                                                BorderStroke(
                                                                        1.dp,
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                                ),
                                                        shadowElevation = 8.dp
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .padding(
                                                                                        horizontal =
                                                                                                16.dp
                                                                                ),
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween,
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                if (isMultiSelectMode) {
                                                                        val visibleFiles =
                                                                                when (currentView) {
                                                                                        ExplorerView
                                                                                                .DIRECTORY ->
                                                                                                sortFileList(
                                                                                                        directoryFiles
                                                                                                )
                                                                                        ExplorerView
                                                                                                .CATEGORY ->
                                                                                                sortFileList(
                                                                                                        categoryFilesList
                                                                                                )
                                                                                        ExplorerView
                                                                                                .FAVORITES ->
                                                                                                sortFileList(
                                                                                                        favoritesList
                                                                                                )
                                                                                        else ->
                                                                                                emptyList()
                                                                                }
                                                                        val isAllSelected =
                                                                                visibleFiles
                                                                                        .isNotEmpty() &&
                                                                                        visibleFiles
                                                                                                .all {
                                                                                                        selectedFiles
                                                                                                                .contains(
                                                                                                                        it
                                                                                                                )
                                                                                                }

                                                                        Column(
                                                                                verticalArrangement =
                                                                                        Arrangement
                                                                                                .Center,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                vertical =
                                                                                                        4.dp
                                                                                        )
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                "${selectedFiles.size} selected",
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .SemiBold,
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .textColor,
                                                                                        fontSize =
                                                                                                13.sp
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                if (isAllSelected
                                                                                                )
                                                                                                        "Deselect All"
                                                                                                else
                                                                                                        "Select All",
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .accent,
                                                                                        fontSize =
                                                                                                12.sp,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Bold,
                                                                                        modifier =
                                                                                                Modifier
                                                                                                        .clickable {
                                                                                                                if (isAllSelected
                                                                                                                ) {
                                                                                                                        visibleFiles
                                                                                                                                .forEach {
                                                                                                                                        selectedFiles
                                                                                                                                                .remove(
                                                                                                                                                        it
                                                                                                                                                )
                                                                                                                                }
                                                                                                                        if (selectedFiles
                                                                                                                                        .isEmpty()
                                                                                                                        ) {
                                                                                                                                isMultiSelectMode =
                                                                                                                                        false
                                                                                                                        }
                                                                                                                } else {
                                                                                                                        visibleFiles
                                                                                                                                .forEach {
                                                                                                                                        file
                                                                                                                                        ->
                                                                                                                                        if (!selectedFiles
                                                                                                                                                        .contains(
                                                                                                                                                                file
                                                                                                                                                        )
                                                                                                                                        ) {
                                                                                                                                                selectedFiles
                                                                                                                                                        .add(
                                                                                                                                                                file
                                                                                                                                                        )
                                                                                                                                        }
                                                                                                                                }
                                                                                                                }
                                                                                                        }
                                                                                                        .padding(
                                                                                                                vertical =
                                                                                                                        2.dp
                                                                                                        )
                                                                                )
                                                                        }
                                                                        Row(
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        8.dp
                                                                                                )
                                                                        ) {
                                                                                IconButton(
                                                                                        onClick = {
                                                                                                shareFiles(
                                                                                                        selectedFiles
                                                                                                )
                                                                                        }
                                                                                ) {
                                                                                        Icon(
                                                                                                Icons.Default
                                                                                                        .Share,
                                                                                                contentDescription =
                                                                                                        "Share",
                                                                                                tint =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textColor
                                                                                        )
                                                                                }
                                                                                // Send to Desktop
                                                                                // button
                                                                                IconButton(
                                                                                        onClick = {
                                                                                                val filesToSend =
                                                                                                        selectedFiles
                                                                                                                .filter {
                                                                                                                        it.isFile
                                                                                                                }
                                                                                                                .toList()
                                                                                                if (filesToSend
                                                                                                                .isNotEmpty()
                                                                                                ) {
                                                                                                        syncViewModel
                                                                                                                .sendFilesToDesktop(
                                                                                                                        context,
                                                                                                                        filesToSend
                                                                                                                )
                                                                                                        selectedFiles
                                                                                                                .clear()
                                                                                                        isMultiSelectMode =
                                                                                                                false
                                                                                                } else {
                                                                                                        android.widget
                                                                                                                .Toast
                                                                                                                .makeText(
                                                                                                                        context,
                                                                                                                        "No files selected (folders cannot be sent)",
                                                                                                                        android.widget
                                                                                                                                .Toast
                                                                                                                                .LENGTH_SHORT
                                                                                                                )
                                                                                                                .show()
                                                                                                }
                                                                                        },
                                                                                        enabled =
                                                                                                isConnectedToDesktop
                                                                                ) {
                                                                                        Icon(
                                                                                                Icons.Default
                                                                                                        .Computer,
                                                                                                contentDescription =
                                                                                                        "Send to Desktop",
                                                                                                tint =
                                                                                                        if (isConnectedToDesktop
                                                                                                        )
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .accent
                                                                                                        else
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .textMuted
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.4f
                                                                                                                        )
                                                                                        )
                                                                                }
                                                                                IconButton(
                                                                                        onClick = {
                                                                                                deleteFiles(
                                                                                                        selectedFiles
                                                                                                )
                                                                                        }
                                                                                ) {
                                                                                        Icon(
                                                                                                Icons.Default
                                                                                                        .Delete,
                                                                                                contentDescription =
                                                                                                        "Delete",
                                                                                                tint =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .dangerText
                                                                                        )
                                                                                }
                                                                                // More options
                                                                                // dropdown menu
                                                                                var showMoreMenu by remember {
                                                                                        mutableStateOf(
                                                                                                false
                                                                                        )
                                                                                }
                                                                                Box {
                                                                                        IconButton(
                                                                                                onClick = {
                                                                                                        showMoreMenu =
                                                                                                                true
                                                                                                }
                                                                                        ) {
                                                                                                Icon(
                                                                                                        Icons.Default
                                                                                                                .MoreVert,
                                                                                                        contentDescription =
                                                                                                                "More options",
                                                                                                        tint =
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .textColor
                                                                                                )
                                                                                        }
                                                                                        DropdownMenu(
                                                                                                expanded =
                                                                                                        showMoreMenu,
                                                                                                onDismissRequest = {
                                                                                                        showMoreMenu =
                                                                                                                false
                                                                                                },
                                                                                                modifier =
                                                                                                        Modifier.background(
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .panelBg
                                                                                                        )
                                                                                        ) {
                                                                                                DropdownMenuItem(
                                                                                                        text = {
                                                                                                                Row(
                                                                                                                        verticalAlignment =
                                                                                                                                Alignment
                                                                                                                                        .CenterVertically
                                                                                                                ) {
                                                                                                                        Icon(
                                                                                                                                Icons.Default
                                                                                                                                        .ContentCopy,
                                                                                                                                contentDescription =
                                                                                                                                        null,
                                                                                                                                tint =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor,
                                                                                                                                modifier =
                                                                                                                                        Modifier.size(
                                                                                                                                                18.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Spacer(
                                                                                                                                modifier =
                                                                                                                                        Modifier.width(
                                                                                                                                                8.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Text(
                                                                                                                                "Copy",
                                                                                                                                color =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor
                                                                                                                        )
                                                                                                                }
                                                                                                        },
                                                                                                        onClick = {
                                                                                                                showMoreMenu =
                                                                                                                        false
                                                                                                                copyOrMoveFiles(
                                                                                                                        selectedFiles,
                                                                                                                        isMove =
                                                                                                                                false
                                                                                                                )
                                                                                                        }
                                                                                                )
                                                                                                DropdownMenuItem(
                                                                                                        text = {
                                                                                                                Row(
                                                                                                                        verticalAlignment =
                                                                                                                                Alignment
                                                                                                                                        .CenterVertically
                                                                                                                ) {
                                                                                                                        Icon(
                                                                                                                                Icons.Default
                                                                                                                                        .ContentCut,
                                                                                                                                contentDescription =
                                                                                                                                        null,
                                                                                                                                tint =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor,
                                                                                                                                modifier =
                                                                                                                                        Modifier.size(
                                                                                                                                                18.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Spacer(
                                                                                                                                modifier =
                                                                                                                                        Modifier.width(
                                                                                                                                                8.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Text(
                                                                                                                                "Cut",
                                                                                                                                color =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor
                                                                                                                        )
                                                                                                                }
                                                                                                        },
                                                                                                        onClick = {
                                                                                                                showMoreMenu =
                                                                                                                        false
                                                                                                                copyOrMoveFiles(
                                                                                                                        selectedFiles,
                                                                                                                        isMove =
                                                                                                                                true
                                                                                                                )
                                                                                                        }
                                                                                                )
                                                                                                val anyNotFavorited =
                                                                                                        selectedFiles
                                                                                                                .any {
                                                                                                                        !isFavorited(
                                                                                                                                it
                                                                                                                        )
                                                                                                                }
                                                                                                DropdownMenuItem(
                                                                                                        text = {
                                                                                                                Row(
                                                                                                                        verticalAlignment =
                                                                                                                                Alignment
                                                                                                                                        .CenterVertically
                                                                                                                ) {
                                                                                                                        Icon(
                                                                                                                                imageVector =
                                                                                                                                        if (anyNotFavorited
                                                                                                                                        )
                                                                                                                                                Icons.Outlined
                                                                                                                                                        .StarBorder
                                                                                                                                        else
                                                                                                                                                Icons.Filled
                                                                                                                                                        .Star,
                                                                                                                                contentDescription =
                                                                                                                                        null,
                                                                                                                                tint =
                                                                                                                                        if (anyNotFavorited
                                                                                                                                        )
                                                                                                                                                FluentTheme
                                                                                                                                                        .colors
                                                                                                                                                        .textColor
                                                                                                                                        else
                                                                                                                                                Color(
                                                                                                                                                        0xFFFFC107
                                                                                                                                                ),
                                                                                                                                modifier =
                                                                                                                                        Modifier.size(
                                                                                                                                                18.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Spacer(
                                                                                                                                modifier =
                                                                                                                                        Modifier.width(
                                                                                                                                                8.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Text(
                                                                                                                                if (anyNotFavorited
                                                                                                                                )
                                                                                                                                        "Favorite"
                                                                                                                                else
                                                                                                                                        "Unfavorite",
                                                                                                                                color =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor
                                                                                                                        )
                                                                                                                }
                                                                                                        },
                                                                                                        onClick = {
                                                                                                                showMoreMenu =
                                                                                                                        false
                                                                                                                selectedFiles
                                                                                                                        .toList()
                                                                                                                        .forEach {
                                                                                                                                file
                                                                                                                                ->
                                                                                                                                val isFav =
                                                                                                                                        isFavorited(
                                                                                                                                                file
                                                                                                                                        )
                                                                                                                                if (anyNotFavorited
                                                                                                                                ) {
                                                                                                                                        if (!isFav
                                                                                                                                        )
                                                                                                                                                toggleFavorite(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                } else {
                                                                                                                                        if (isFav
                                                                                                                                        )
                                                                                                                                                toggleFavorite(
                                                                                                                                                        file
                                                                                                                                                )
                                                                                                                                }
                                                                                                                        }
                                                                                                                selectedFiles
                                                                                                                        .clear()
                                                                                                                isMultiSelectMode =
                                                                                                                        false
                                                                                                        }
                                                                                                )
                                                                                                DropdownMenuItem(
                                                                                                        text = {
                                                                                                                Row(
                                                                                                                        verticalAlignment =
                                                                                                                                Alignment
                                                                                                                                        .CenterVertically
                                                                                                                ) {
                                                                                                                        Icon(
                                                                                                                                Icons.Default
                                                                                                                                        .Archive,
                                                                                                                                contentDescription =
                                                                                                                                        null,
                                                                                                                                tint =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor,
                                                                                                                                modifier =
                                                                                                                                        Modifier.size(
                                                                                                                                                18.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Spacer(
                                                                                                                                modifier =
                                                                                                                                        Modifier.width(
                                                                                                                                                8.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                        Text(
                                                                                                                                "Compress (Zip)",
                                                                                                                                color =
                                                                                                                                        FluentTheme
                                                                                                                                                .colors
                                                                                                                                                .textColor
                                                                                                                        )
                                                                                                                }
                                                                                                        },
                                                                                                        onClick = {
                                                                                                                showMoreMenu =
                                                                                                                        false
                                                                                                                showCompressDialog =
                                                                                                                        selectedFiles
                                                                                                                                .toList()
                                                                                                        }
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                } else {
                                                                        // Clipboard Paste Mode
                                                                        Text(
                                                                                text =
                                                                                        "${copiedFiles.size} copied",
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .SemiBold,
                                                                                color =
                                                                                        FluentTheme
                                                                                                .colors
                                                                                                .textColor,
                                                                                fontSize = 14.sp
                                                                        )
                                                                        Row(
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        8.dp
                                                                                                ),
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                TextButton(
                                                                                        onClick = {
                                                                                                copiedFiles
                                                                                                        .clear()
                                                                                        }
                                                                                ) {
                                                                                        Text(
                                                                                                "Cancel",
                                                                                                color =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textMuted
                                                                                        )
                                                                                }
                                                                                Button(
                                                                                        onClick = {
                                                                                                pasteFiles()
                                                                                        },
                                                                                        colors =
                                                                                                ButtonDefaults
                                                                                                        .buttonColors(
                                                                                                                containerColor =
                                                                                                                        FluentTheme
                                                                                                                                .colors
                                                                                                                                .accent
                                                                                                        ),
                                                                                        shape =
                                                                                                CircleShape
                                                                                ) {
                                                                                        Icon(
                                                                                                Icons.Default
                                                                                                        .ContentPaste,
                                                                                                contentDescription =
                                                                                                        "Paste",
                                                                                                tint =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .onAccent,
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                16.dp
                                                                                                        )
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.width(
                                                                                                                4.dp
                                                                                                        )
                                                                                        )
                                                                                        Text(
                                                                                                "Paste Here",
                                                                                                color =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .onAccent
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

                // Global Collapsed Music Player Widget (Stays active on bottom of home page)
                val playingSong = LocalMusicPlayerManager.currentPlayingFile
                if (playingSong != null) {
                        Box(
                                modifier =
                                        Modifier.align(Alignment.BottomCenter)
                                                .padding(
                                                        bottom =
                                                                if (isMultiSelectMode ||
                                                                                copiedFiles
                                                                                        .isNotEmpty()
                                                                )
                                                                        90.dp
                                                                else 16.dp
                                                )
                                                .padding(horizontal = 16.dp)
                        ) {
                                Surface(
                                        modifier =
                                                Modifier.fillMaxWidth().height(60.dp).clickable {
                                                        showMusicPlayerExpanded = true
                                                },
                                        shape = RoundedCornerShape(12.dp),
                                        color = FluentTheme.colors.panelBg,
                                        border = BorderStroke(1.dp, FluentTheme.colors.panelBorder),
                                        shadowElevation = 6.dp
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = "Playing",
                                                        tint = FluentTheme.colors.accent,
                                                        modifier =
                                                                Modifier.size(36.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                FluentTheme.colors
                                                                                        .surfaceBg
                                                                        )
                                                                        .padding(8.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                text = playingSong.name,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color =
                                                                        FluentTheme.colors
                                                                                .textColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                        val progress =
                                                                if (LocalMusicPlayerManager
                                                                                .duration > 0
                                                                ) {
                                                                        LocalMusicPlayerManager
                                                                                .currentPlaybackPosition
                                                                                .toFloat() /
                                                                                LocalMusicPlayerManager
                                                                                        .duration
                                                                } else 0f
                                                        LinearProgressIndicator(
                                                                progress = progress,
                                                                color = FluentTheme.colors.accent,
                                                                trackColor =
                                                                        FluentTheme.colors
                                                                                .panelBorder,
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .height(3.dp)
                                                                                .padding(top = 4.dp)
                                                        )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                IconButton(
                                                        onClick = {
                                                                if (LocalMusicPlayerManager
                                                                                .isPlaying
                                                                ) {
                                                                        LocalMusicPlayerManager
                                                                                .pause()
                                                                } else {
                                                                        LocalMusicPlayerManager
                                                                                .resume()
                                                                }
                                                        }
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (LocalMusicPlayerManager
                                                                                        .isPlaying
                                                                        )
                                                                                Icons.Default.Pause
                                                                        else
                                                                                Icons.Default
                                                                                        .PlayArrow,
                                                                contentDescription = "Play/Pause",
                                                                tint = FluentTheme.colors.textColor
                                                        )
                                                }
                                                IconButton(
                                                        onClick = { LocalMusicPlayerManager.stop() }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Stop",
                                                                tint = FluentTheme.colors.textMuted
                                                        )
                                                }
                                        }
                                }
                        }
                }

                // Expanded background music player overlay Dialog
                if (showMusicPlayerExpanded && playingSong != null) {
                        LocalMusicPlayerDialog(
                                file = playingSong,
                                onDismiss = { showMusicPlayerExpanded = false }
                        )
                }

                // Action Dialogs
                if (showCreateFolderDialog) {
                        var folderName by remember { mutableStateOf("") }
                        AlertDialog(
                                onDismissRequest = { showCreateFolderDialog = false },
                                title = { Text("New Folder") },
                                text = {
                                        OutlinedTextField(
                                                value = folderName,
                                                onValueChange = { folderName = it },
                                                label = { Text("Folder Name") },
                                                singleLine = true,
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor =
                                                                        FluentTheme.colors.accent,
                                                                focusedLabelColor =
                                                                        FluentTheme.colors.accent
                                                        )
                                        )
                                },
                                confirmButton = {
                                        Button(
                                                onClick = {
                                                        if (folderName.isNotEmpty()) {
                                                                createFolder(folderName)
                                                                showCreateFolderDialog = false
                                                        }
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        FluentTheme.colors.accent
                                                        )
                                        ) { Text("Create", color = FluentTheme.colors.onAccent) }
                                },
                                dismissButton = {
                                        TextButton(onClick = { showCreateFolderDialog = false }) {
                                                Text("Cancel", color = FluentTheme.colors.textColor)
                                        }
                                },
                                containerColor = FluentTheme.colors.pageBg
                        )
                }

                if (showRenameDialog != null) {
                        val file = showRenameDialog!!
                        var renameText by remember { mutableStateOf(file.name) }
                        AlertDialog(
                                onDismissRequest = { showRenameDialog = null },
                                title = { Text("Rename File") },
                                text = {
                                        OutlinedTextField(
                                                value = renameText,
                                                onValueChange = { renameText = it },
                                                label = { Text("New Name") },
                                                singleLine = true,
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor =
                                                                        FluentTheme.colors.accent,
                                                                focusedLabelColor =
                                                                        FluentTheme.colors.accent
                                                        )
                                        )
                                },
                                confirmButton = {
                                        Button(
                                                onClick = {
                                                        if (renameText.isNotEmpty()) {
                                                                renameFile(file, renameText)
                                                                showRenameDialog = null
                                                        }
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        FluentTheme.colors.accent
                                                        )
                                        ) { Text("Rename", color = FluentTheme.colors.onAccent) }
                                },
                                dismissButton = {
                                        TextButton(onClick = { showRenameDialog = null }) {
                                                Text("Cancel", color = FluentTheme.colors.textColor)
                                        }
                                },
                                containerColor = FluentTheme.colors.pageBg
                        )
                }

                filesToDelete?.let { files ->
                        AlertDialog(
                                onDismissRequest = { filesToDelete = null },
                                title = {
                                        Text(
                                                text = "Move to Recycle Bin?",
                                                fontWeight = FontWeight.Bold,
                                                color = FluentTheme.colors.textColor
                                        )
                                },
                                text = {
                                        Text(
                                                text =
                                                        if (files.size == 1) {
                                                                "Are you sure you want to move '${files[0].name}' to the Recycle Bin?"
                                                        } else {
                                                                "Are you sure you want to move ${files.size} items to the Recycle Bin?"
                                                        },
                                                color = FluentTheme.colors.textColor
                                        )
                                },
                                confirmButton = {
                                        Button(
                                                onClick = {
                                                        performDelete(files)
                                                        filesToDelete = null
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        FluentTheme.colors
                                                                                .dangerText
                                                        )
                                        ) { Text("Move to Trash", color = Color.White) }
                                },
                                dismissButton = {
                                        TextButton(onClick = { filesToDelete = null }) {
                                                Text("Cancel", color = FluentTheme.colors.textColor)
                                        }
                                },
                                containerColor = FluentTheme.colors.pageBg,
                                shape = RoundedCornerShape(16.dp)
                        )
                }

                if (showCompressDialog != null) {
                        val files = showCompressDialog!!
                        val initialZipName =
                                if (files.size == 1) files[0].nameWithoutExtension else "archive"
                        var zipName by remember { mutableStateOf(initialZipName) }
                        AlertDialog(
                                onDismissRequest = { showCompressDialog = null },
                                title = { Text("Compress to Zip") },
                                text = {
                                        OutlinedTextField(
                                                value = zipName,
                                                onValueChange = { zipName = it },
                                                label = { Text("Archive Name") },
                                                singleLine = true,
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor =
                                                                        FluentTheme.colors.accent,
                                                                focusedLabelColor =
                                                                        FluentTheme.colors.accent
                                                        )
                                        )
                                },
                                confirmButton = {
                                        Button(
                                                onClick = {
                                                        if (zipName.isNotEmpty()) {
                                                                compressFiles(files, zipName)
                                                                showCompressDialog = null
                                                        }
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        FluentTheme.colors.accent
                                                        )
                                        ) { Text("Compress", color = FluentTheme.colors.onAccent) }
                                },
                                dismissButton = {
                                        TextButton(onClick = { showCompressDialog = null }) {
                                                Text("Cancel", color = FluentTheme.colors.textColor)
                                        }
                                },
                                containerColor = FluentTheme.colors.pageBg
                        )
                }

                if (showApkDetailsDialog != null) {
                        LocalApkDetailsDialog(
                                file = showApkDetailsDialog!!,
                                onDismiss = { showApkDetailsDialog = null }
                        )
                }

                if (showDetailsDialog != null) {
                        LocalFileDetailsDialog(
                                file = showDetailsDialog!!,
                                onDismiss = { showDetailsDialog = null }
                        )
                }

                // Full Screen File Previews
                if (previewPdfFile != null) {
                        LocalPdfViewerDialog(
                                file = previewPdfFile!!,
                                onDismiss = { previewPdfFile = null }
                        )
                }

                if (previewVideoFile != null) {
                        LocalVideoPlayerDialog(
                                file = previewVideoFile!!,
                                onDismiss = { previewVideoFile = null }
                        )
                }

                if (previewImageFiles.isNotEmpty()) {
                        FullScreenSwipeImageViewerDialog(
                                imageFiles = previewImageFiles,
                                initialIndex = previewImageIndex,
                                onDismiss = { previewImageFiles = emptyList() },
                                isFavorited = { isFavorited(it) },
                                onToggleFavorite = { toggleFavorite(it) },
                                onDelete = { deleteFiles(listOf(it)) },
                                onShare = { shareFiles(listOf(it)) },
                                onSendToDesktop = { file ->
                                        if (isConnectedToDesktop && file.isFile) {
                                                syncViewModel.sendFilesToDesktop(
                                                        context,
                                                        listOf(file)
                                                )
                                        } else {
                                                Toast.makeText(
                                                                context,
                                                                "Not connected to Desktop PC",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                        }
                                },
                                onImageSaved = { original, saved ->
                                        val idx = previewImageFiles.indexOf(original)
                                        if (idx != -1) {
                                                val newPreviewList =
                                                        previewImageFiles.toMutableList()
                                                newPreviewList.add(idx + 1, saved)
                                                previewImageFiles = newPreviewList
                                        }
                                        val dirIdx = directoryFiles.indexOf(original)
                                        if (dirIdx != -1) {
                                                val newDirList = directoryFiles.toMutableList()
                                                newDirList.add(dirIdx + 1, saved)
                                                directoryFiles = newDirList
                                        }
                                        val catIdx = categoryFilesList.indexOf(original)
                                        if (catIdx != -1) {
                                                val newCatList = categoryFilesList.toMutableList()
                                                newCatList.add(catIdx + 1, saved)
                                                categoryFilesList = newCatList
                                        }
                                        val recIdx = recentFilesList.indexOf(original)
                                        if (recIdx != -1) {
                                                val newRecList = recentFilesList.toMutableList()
                                                newRecList.add(recIdx + 1, saved)
                                                recentFilesList = newRecList
                                        }
                                }
                        )
                }
        }
}

// ---------------- HEADER COMPONENT ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerHeader(
        currentView: ExplorerView,
        currentDirectory: File,
        currentCategoryName: String,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        onBack: () -> Unit,
        showHiddenFiles: Boolean,
        onToggleHiddenFiles: (Boolean) -> Unit,
        sortType: FileSortType,
        onSortTypeChange: (FileSortType) -> Unit,
        sortAscending: Boolean,
        onToggleSortOrder: () -> Unit,
        onCreateFolder: () -> Unit,
        onBackToPC: () -> Unit,
        onThemesClick: () -> Unit,
        isVideoCategory: Boolean = false,
        useVideoGrid: Boolean = false,
        onToggleVideoGrid: () -> Unit = {},
        isImageCategory: Boolean = false,
        useImageAlbums: Boolean = true,
        onToggleImageAlbums: () -> Unit = {},
        isConnectedToDesktop: Boolean = false,
        onSendToDesktop: () -> Unit = {},
        hasSelectedFiles: Boolean = false
) {
        Surface(color = FluentTheme.colors.pageBg) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 8.dp)
                ) {
                        // First Top Row
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                IconButton(onClick = onBack) {
                                        Icon(
                                                Icons.Default.ArrowBack,
                                                contentDescription = "Back",
                                                tint = FluentTheme.colors.textColor
                                        )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        val title =
                                                when (currentView) {
                                                        ExplorerView.HOME -> "My Files Hub"
                                                        ExplorerView.DIRECTORY -> "Internal Storage"
                                                        ExplorerView.TRASH -> "Recycle Bin"
                                                        ExplorerView.FAVORITES -> "Favorites"
                                                        ExplorerView.CATEGORY -> currentCategoryName
                                                }
                                        Text(
                                                text = title,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FluentTheme.colors.textColor
                                        )
                                        if (currentView == ExplorerView.DIRECTORY) {
                                                Text(
                                                        text = currentDirectory.name,
                                                        fontSize = 12.sp,
                                                        color = FluentTheme.colors.textMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }

                                // Header Action Options
                                if (currentView == ExplorerView.DIRECTORY) {
                                        IconButton(onClick = onCreateFolder) {
                                                Icon(
                                                        Icons.Default.CreateNewFolder,
                                                        contentDescription = "New Folder",
                                                        tint = FluentTheme.colors.textColor
                                                )
                                        }
                                }

                                if (currentView == ExplorerView.HOME) {
                                        IconButton(onClick = onThemesClick) {
                                                Icon(
                                                        Icons.Default.Palette,
                                                        contentDescription = "Themes",
                                                        tint = FluentTheme.colors.textColor
                                                )
                                        }
                                }

                                // Sorting & Options Menu
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                        IconButton(onClick = { showMenu = true }) {
                                                Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = FluentTheme.colors.textColor
                                                )
                                        }
                                        DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false },
                                                modifier =
                                                        Modifier.background(
                                                                FluentTheme.colors.pageBg
                                                        )
                                        ) {
                                                if (currentView == ExplorerView.DIRECTORY) {
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Text(
                                                                                if (showHiddenFiles)
                                                                                        "Hide Hidden Files"
                                                                                else
                                                                                        "Show Hidden Files",
                                                                                color =
                                                                                        FluentTheme
                                                                                                .colors
                                                                                                .textColor
                                                                        )
                                                                },
                                                                onClick = {
                                                                        onToggleHiddenFiles(
                                                                                !showHiddenFiles
                                                                        )
                                                                        showMenu = false
                                                                }
                                                        )
                                                        Divider(
                                                                color =
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                        )
                                                }

                                                if (isVideoCategory) {
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                Icon(
                                                                                        imageVector =
                                                                                                if (useVideoGrid
                                                                                                )
                                                                                                        Icons.Default
                                                                                                                .ViewList
                                                                                                else
                                                                                                        Icons.Default
                                                                                                                .GridView,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .accent,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        18.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        8.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        if (useVideoGrid
                                                                                        )
                                                                                                "List View"
                                                                                        else
                                                                                                "Grid View",
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .textColor
                                                                                )
                                                                        }
                                                                },
                                                                onClick = {
                                                                        onToggleVideoGrid()
                                                                        showMenu = false
                                                                }
                                                        )
                                                        Divider(
                                                                color =
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                        )
                                                }

                                                if (isImageCategory) {
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                Icon(
                                                                                        imageVector =
                                                                                                if (useImageAlbums
                                                                                                )
                                                                                                        Icons.Outlined
                                                                                                                .Image
                                                                                                else
                                                                                                        Icons.Default
                                                                                                                .PhotoAlbum,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .accent,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        18.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        8.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        if (useImageAlbums
                                                                                        )
                                                                                                "Images"
                                                                                        else
                                                                                                "Albums",
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .textColor
                                                                                )
                                                                        }
                                                                },
                                                                onClick = {
                                                                        onToggleImageAlbums()
                                                                        showMenu = false
                                                                }
                                                        )
                                                        Divider(
                                                                color =
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                        )
                                                }

                                                // Sort category
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Sort by Name",
                                                                        fontWeight =
                                                                                if (sortType ==
                                                                                                FileSortType
                                                                                                        .NAME
                                                                                )
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Normal,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onSortTypeChange(FileSortType.NAME)
                                                                showMenu = false
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Sort by Date",
                                                                        fontWeight =
                                                                                if (sortType ==
                                                                                                FileSortType
                                                                                                        .DATE
                                                                                )
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Normal,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onSortTypeChange(FileSortType.DATE)
                                                                showMenu = false
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Sort by Size",
                                                                        fontWeight =
                                                                                if (sortType ==
                                                                                                FileSortType
                                                                                                        .SIZE
                                                                                )
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Normal,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onSortTypeChange(FileSortType.SIZE)
                                                                showMenu = false
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Sort by Type",
                                                                        fontWeight =
                                                                                if (sortType ==
                                                                                                FileSortType
                                                                                                        .TYPE
                                                                                )
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Normal,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onSortTypeChange(FileSortType.TYPE)
                                                                showMenu = false
                                                        }
                                                )
                                                Divider(color = FluentTheme.colors.panelBorder)
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        if (sortAscending)
                                                                                "Descending"
                                                                        else "Ascending",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onToggleSortOrder()
                                                                showMenu = false
                                                        }
                                                )

                                                if (hasSelectedFiles) {
                                                        Divider(
                                                                color =
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                        )
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Computer,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                if (isConnectedToDesktop
                                                                                                )
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent
                                                                                                else
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textMuted
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.4f
                                                                                                                ),
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        18.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        8.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        "Send to Desktop",
                                                                                        color =
                                                                                                if (isConnectedToDesktop
                                                                                                )
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent
                                                                                                else
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textMuted
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.4f
                                                                                                                ),
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .SemiBold
                                                                                )
                                                                        }
                                                                },
                                                                enabled = isConnectedToDesktop,
                                                                onClick = {
                                                                        showMenu = false
                                                                        onSendToDesktop()
                                                                }
                                                        )
                                                }

                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Exit to Connect to PC Guide",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .accent,
                                                                        fontWeight = FontWeight.Bold
                                                                )
                                                        },
                                                        onClick = {
                                                                showMenu = false
                                                                onBackToPC()
                                                        }
                                                )
                                        }
                                }
                        }

                        // Search Bar Row (If browsing directory or categories)
                        if (currentView == ExplorerView.DIRECTORY ||
                                        currentView == ExplorerView.CATEGORY ||
                                        currentView == ExplorerView.FAVORITES
                        ) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .clip(
                                                                RoundedCornerShape(
                                                                        FluentTheme.dims
                                                                                .controlRadius
                                                                )
                                                        )
                                                        .background(FluentTheme.colors.surfaceBg)
                                                        .border(
                                                                1.dp,
                                                                FluentTheme.colors.panelBorder,
                                                                RoundedCornerShape(
                                                                        FluentTheme.dims
                                                                                .controlRadius
                                                                )
                                                        )
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        )
                                ) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = null,
                                                        tint = FluentTheme.colors.textMuted,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                ExplorerBasicTextField(
                                                        value = searchQuery,
                                                        onValueChange = onSearchQueryChange,
                                                        placeholder = "Search in current view...",
                                                        modifier = Modifier.weight(1f)
                                                )
                                                if (searchQuery.isNotEmpty()) {
                                                        Icon(
                                                                imageVector = Icons.Default.Clear,
                                                                contentDescription = "Clear",
                                                                tint = FluentTheme.colors.textMuted,
                                                                modifier =
                                                                        Modifier.size(18.dp)
                                                                                .clickable {
                                                                                        onSearchQueryChange(
                                                                                                ""
                                                                                        )
                                                                                }
                                                        )
                                                }
                                        }
                                }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = FluentTheme.colors.panelBorder)
                }
        }
}

// ---------------- HOME DASHBOARD ----------------
@Composable
fun HomeDashboard(
        onNavigateToDir: (File) -> Unit,
        onNavigateToCategory: (String, List<String>) -> Unit,
        onNavigateToTrash: () -> Unit,
        onNavigateToFavorites: () -> Unit,
        recentFiles: List<File>,
        onOpenFile: (File) -> Unit,
        storages: List<StorageInfo>,
        onAnalyseStorageClick: () -> Unit
) {
        val context = LocalContext.current

        val totalSpace = remember(storages) { storages.sumOf { it.totalBytes } }
        val usedSpace = remember(storages) { storages.sumOf { it.usedBytes } }
        val freeSpace =
                remember(totalSpace, usedSpace) { (totalSpace - usedSpace).coerceAtLeast(0) }
        val usedRatio =
                remember(totalSpace, usedSpace) {
                        if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
                }

        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // 1. STORAGE ANALYZER
                item {
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth().clickable {
                                                onAnalyseStorageClick()
                                        },
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = FluentTheme.colors.surfaceBg
                                        ),
                                border = BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                                text = "Storage Analyzer",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = FluentTheme.colors.textColor
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Main Colorful Progress Bar
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(14.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                                )
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxHeight()
                                                                        .fillMaxWidth(usedRatio)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                Brush.horizontalGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent,
                                                                                                        Color(
                                                                                                                0xFF60CDFF
                                                                                                        )
                                                                                                )
                                                                                )
                                                                        )
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        text = "${formatSize(usedSpace)} Used",
                                                        fontSize = 12.sp,
                                                        color = FluentTheme.colors.textColor,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                        text = "${formatSize(freeSpace)} Free",
                                                        fontSize = 12.sp,
                                                        color = FluentTheme.colors.connectedText,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                        text = "Total ${formatSize(totalSpace)}",
                                                        fontSize = 12.sp,
                                                        color = FluentTheme.colors.textMuted
                                                )
                                        }
                                }
                        }
                }

                // 2. CATEGORIES
                item {
                        Text(
                                text = "Categories",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = FluentTheme.colors.textMuted,
                                letterSpacing = 0.5.sp
                        )
                }

                item {
                        // Category Buttons Grid
                        data class CategoryItem(
                                val name: String,
                                val icon: androidx.compose.ui.graphics.vector.ImageVector,
                                val color: Color,
                                val extensions: List<String>
                        )

                        val categoriesList =
                                listOf(
                                        CategoryItem(
                                                "Images",
                                                Icons.Default.Image,
                                                Color(0xFFF47C60),
                                                listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                                        ),
                                        CategoryItem(
                                                "Videos",
                                                Icons.Default.VideoFile,
                                                Color(0xFFB57EDC),
                                                listOf("mp4", "mkv", "webm", "avi", "3gp", "mov")
                                        ),
                                        CategoryItem(
                                                "Audio files",
                                                Icons.Default.MusicNote,
                                                Color(0xFF75A6FF),
                                                listOf("mp3", "wav", "m4a", "flac", "ogg", "aac")
                                        ),
                                        CategoryItem(
                                                "Documents",
                                                Icons.Default.Description,
                                                Color(0xFFE5B575),
                                                listOf(
                                                        "pdf",
                                                        "doc",
                                                        "docx",
                                                        "xls",
                                                        "xlsx",
                                                        "ppt",
                                                        "pptx",
                                                        "txt"
                                                )
                                        ),
                                        CategoryItem(
                                                "Installation files",
                                                Icons.Default.Android,
                                                Color(0xFF9CCC65),
                                                listOf("apk")
                                        ),
                                        CategoryItem(
                                                "Downloads",
                                                Icons.Default.Download,
                                                Color(0xFF4AC4CF),
                                                emptyList()
                                        )
                                )

                        Row(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        categoriesList.take(3).forEach { cat ->
                                                CategoryCard(
                                                        cat.name,
                                                        cat.icon,
                                                        cat.color,
                                                        onClick = {
                                                                onNavigateToCategory(
                                                                        cat.name,
                                                                        cat.extensions
                                                                )
                                                        }
                                                )
                                        }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        categoriesList.drop(3).forEach { cat ->
                                                CategoryCard(
                                                        cat.name,
                                                        cat.icon,
                                                        cat.color,
                                                        onClick = {
                                                                if (cat.name == "Downloads") {
                                                                        onNavigateToDir(
                                                                                Environment
                                                                                        .getExternalStoragePublicDirectory(
                                                                                                Environment
                                                                                                        .DIRECTORY_DOWNLOADS
                                                                                        )
                                                                        )
                                                                } else {
                                                                        onNavigateToCategory(
                                                                                cat.name,
                                                                                cat.extensions
                                                                        )
                                                                }
                                                        }
                                                )
                                        }
                                }
                        }
                }

                // Quick shortcut locations (Storage drives, Recycle Bin, Favorites)
                item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                storages.forEach { storage ->
                                        Card(
                                                modifier =
                                                        Modifier.fillMaxWidth().clickable {
                                                                onNavigateToDir(File(storage.path))
                                                        },
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        FluentTheme.colors.surfaceBg
                                                        ),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                FluentTheme.colors.panelBorder
                                                        )
                                        ) {
                                                Row(
                                                        modifier = Modifier.padding(14.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (storage.isSdCard)
                                                                                Icons.Default.SdCard
                                                                        else Icons.Default.Storage,
                                                                contentDescription = null,
                                                                tint = FluentTheme.colors.accent,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                                Text(
                                                                        storage.name,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                                Text(
                                                                        "${formatSize(storage.usedBytes)} used of ${formatSize(storage.totalBytes)}",
                                                                        fontSize = 11.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textMuted
                                                                )
                                                        }
                                                }
                                        }
                                }

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        // Recycle Bin Card
                                        Card(
                                                modifier =
                                                        Modifier.weight(1f).clickable {
                                                                onNavigateToTrash()
                                                        },
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        FluentTheme.colors.surfaceBg
                                                        ),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                FluentTheme.colors.panelBorder
                                                        )
                                        ) {
                                                Row(
                                                        modifier = Modifier.padding(14.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Icon(
                                                                Icons.Default.DeleteSweep,
                                                                contentDescription = null,
                                                                tint =
                                                                        FluentTheme.colors
                                                                                .dangerText,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                                Text(
                                                                        "Recycle Bin",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                                Text(
                                                                        "Restore deleted",
                                                                        fontSize = 11.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textMuted
                                                                )
                                                        }
                                                }
                                        }

                                        // Favorites card
                                        Card(
                                                modifier =
                                                        Modifier.weight(1f).clickable {
                                                                onNavigateToFavorites()
                                                        },
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        FluentTheme.colors.surfaceBg
                                                        ),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                FluentTheme.colors.panelBorder
                                                        )
                                        ) {
                                                Row(
                                                        modifier = Modifier.padding(14.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Icon(
                                                                Icons.Default.Star,
                                                                contentDescription = null,
                                                                tint = Color(0xFFFFB900),
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                                Text(
                                                                        "Favorites",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                                Text(
                                                                        "Starred items",
                                                                        fontSize = 11.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textMuted
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }

                // 3. RECENT FILES
                item {
                        Text(
                                text = "Recent Files",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = FluentTheme.colors.textMuted,
                                letterSpacing = 0.5.sp
                        )
                }

                if (recentFiles.isEmpty()) {
                        item {
                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                FluentTheme.colors.surfaceBg
                                                ),
                                        border = BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                                ) {
                                        Box(
                                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        "No recent files found",
                                                        color = FluentTheme.colors.textMuted,
                                                        fontSize = 13.sp
                                                )
                                        }
                                }
                        }
                } else {
                        items(recentFiles) { file ->
                                LocalFileRow(
                                        file = file,
                                        isMultiSelectMode = false,
                                        isSelected = false,
                                        onClick = { onOpenFile(file) },
                                        onLongClick = {},
                                        isFavorited = false,
                                        onToggleFavorite = null,
                                        onRename = null,
                                        onDelete = null,
                                        onCompress = null,
                                        onExtract = null,
                                        onShare = null,
                                        onDetails = null,
                                        onSendToDesktop = null
                                )
                        }
                }
        }
}

@Composable
fun CategoryCard(
        name: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        iconColor: Color,
        onClick: () -> Unit
) {
        Card(
                modifier = Modifier.fillMaxWidth().clickable { onClick() },
                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                border = BorderStroke(1.dp, FluentTheme.colors.panelBorder)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                                text = name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = FluentTheme.colors.textColor
                        )
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageAlbumsGrid(
        albums: List<ImageAlbum>,
        gridState: LazyGridState,
        isLoading: Boolean,
        onAlbumClick: (ImageAlbum) -> Unit,
        modifier: Modifier = Modifier
) {
        if (albums.isEmpty() && !isLoading) {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                        imageVector = Icons.Default.PhotoAlbum,
                                        contentDescription = null,
                                        tint = FluentTheme.colors.textMuted,
                                        modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                        "No albums found",
                                        color = FluentTheme.colors.textColor,
                                        fontWeight = FontWeight.SemiBold
                                )
                        }
                }
                return
        }

        Box(modifier = modifier.fillMaxSize()) {
                LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                ) {
                        items(albums, key = { it.directory.absolutePath }) { album ->
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .combinedClickable(
                                                                onClick = { onAlbumClick(album) }
                                                        )
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .aspectRatio(1f)
                                                                .clip(RoundedCornerShape(28.dp))
                                                                .background(
                                                                        FluentTheme.colors.surfaceBg
                                                                )
                                                                .border(
                                                                        1.dp,
                                                                        FluentTheme.colors
                                                                                .panelBorder,
                                                                        RoundedCornerShape(28.dp)
                                                                )
                                        ) {
                                                AsyncImage(
                                                        model = album.coverFile,
                                                        contentDescription = album.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                                text = album.name,
                                                color = FluentTheme.colors.textColor,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                                text = album.count.toString(),
                                                color = FluentTheme.colors.textMuted,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                        )
                                }
                        }
                }
                FastScrollbarGrid(
                        gridState = gridState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                )
        }
}

// ---------------- DIRECTORY VIEWER ----------------
@Composable
fun DirectoryViewer(
        files: List<File>,
        selectedFiles: List<File>,
        isMultiSelectMode: Boolean,
        onToggleSelectMode: (Boolean) -> Unit,
        onFileClick: (File) -> Unit,
        onFileLongClick: (File) -> Unit,
        isFavorited: (File) -> Boolean,
        onToggleFavorite: (File) -> Unit,
        onRename: (File) -> Unit,
        onDelete: (File) -> Unit,
        onCompress: (File) -> Unit,
        onExtract: (File) -> Unit,
        onShare: (File) -> Unit,
        onDetails: (File) -> Unit,
        onSendToDesktop: (File) -> Unit = {},
        isConnectedToDesktop: Boolean = false,
        isLoading: Boolean = false
) {
        if (files.isEmpty()) {
                if (!isLoading) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                                Icons.Outlined.Folder,
                                                contentDescription = null,
                                                tint = FluentTheme.colors.textMuted,
                                                modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                                "This folder is empty",
                                                color = FluentTheme.colors.textColor,
                                                fontWeight = FontWeight.SemiBold
                                        )
                                }
                        }
                }
        } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(files, key = { it.absolutePath }) { file ->
                                        val isSelected = selectedFiles.contains(file)
                                        LocalFileRow(
                                                file = file,
                                                isMultiSelectMode = isMultiSelectMode,
                                                isSelected = isSelected,
                                                onClick = { onFileClick(file) },
                                                onLongClick = { onFileLongClick(file) },
                                                isFavorited = isFavorited(file),
                                                onToggleFavorite = { onToggleFavorite(file) },
                                                onRename = { onRename(file) },
                                                onDelete = { onDelete(file) },
                                                onCompress = { onCompress(file) },
                                                onExtract = { onExtract(file) },
                                                onShare = { onShare(file) },
                                                onDetails = { onDetails(file) },
                                                onSendToDesktop =
                                                        if (file.isFile) {
                                                                { onSendToDesktop(file) }
                                                        } else null,
                                                isConnectedToDesktop = isConnectedToDesktop
                                        )
                                }
                        }
                        FastScrollbar(
                                listState = listState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                        )
                }
        }
}

// Single File Row Composable
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalFileRow(
        file: File,
        isMultiSelectMode: Boolean,
        isSelected: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        isFavorited: Boolean,
        onToggleFavorite: (() -> Unit)?,
        onRename: (() -> Unit)?,
        onDelete: (() -> Unit)?,
        onCompress: (() -> Unit)?,
        onExtract: (() -> Unit)?,
        onShare: (() -> Unit)?,
        onDetails: (() -> Unit)?,
        onSendToDesktop: (() -> Unit)? = null,
        isConnectedToDesktop: Boolean = false
) {
        val formattedDate =
                remember(file.lastModified()) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(file.lastModified()))
                }

        val detailsText =
                remember(file) {
                        if (file.isDirectory) {
                                val children = file.listFiles()?.size ?: 0
                                "$children items | $formattedDate"
                        } else {
                                "${formatSize(file.length())} | $formattedDate"
                        }
                }

        val iconInfo = remember(file) { getFileIcon(file) }

        Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                if (isSelected)
                                                        FluentTheme.colors.accent.copy(
                                                                alpha = 0.12f
                                                        )
                                                else Color.Transparent
                                        )
                                        .combinedClickable(
                                                onClick = onClick,
                                                onLongClick = onLongClick
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (isMultiSelectMode) {
                                Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onClick() },
                                        colors =
                                                CheckboxDefaults.colors(
                                                        checkedColor = FluentTheme.colors.accent
                                                )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Media thumbnail preview or category icon
                        val isImg =
                                file.extension.lowercase() in
                                        listOf("jpg", "jpeg", "png", "webp", "gif")
                        val isVid =
                                file.extension.lowercase() in
                                        listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
                        if ((isImg || isVid) && !file.isDirectory) {
                                if (isVid) {
                                        // Load video thumbnail asynchronously with caching and
                                        // serialized concurrency
                                        var vidThumb by
                                                remember(file) {
                                                        mutableStateOf<android.graphics.Bitmap?>(
                                                                VideoThumbnailCache.get(
                                                                        file.absolutePath
                                                                )
                                                        )
                                                }
                                        var thumbLoaded by
                                                remember(file) { mutableStateOf(vidThumb != null) }
                                        LaunchedEffect(file) {
                                                if (vidThumb == null) {
                                                        kotlinx.coroutines.withContext(
                                                                kotlinx.coroutines.Dispatchers.IO
                                                        ) {
                                                                vidThumb =
                                                                        VideoThumbnailCache
                                                                                .getOrCreate(
                                                                                        file = file,
                                                                                        size =
                                                                                                android.util
                                                                                                        .Size(
                                                                                                                120,
                                                                                                                120
                                                                                                        ),
                                                                                        isMicro =
                                                                                                true
                                                                                )
                                                        }
                                                        thumbLoaded = true
                                                }
                                        }
                                        if (vidThumb != null) {
                                                Image(
                                                        bitmap = vidThumb!!.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier =
                                                                Modifier.size(44.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                FluentTheme.colors
                                                                                        .panelBorder
                                                                        )
                                                )
                                        } else {
                                                Box(
                                                        modifier =
                                                                Modifier.size(44.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                FluentTheme.colors
                                                                                        .panelBorder
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        if (!thumbLoaded) {
                                                                CircularProgressIndicator(
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        16.dp
                                                                                ),
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .accent,
                                                                        strokeWidth = 2.dp
                                                                )
                                                        } else {
                                                                Icon(
                                                                        Icons.Default.PlayCircle,
                                                                        contentDescription = null,
                                                                        tint =
                                                                                FluentTheme.colors
                                                                                        .accent,
                                                                        modifier =
                                                                                Modifier.size(24.dp)
                                                                )
                                                        }
                                                }
                                        }
                                } else {
                                        AsyncImage(
                                                model = file,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier =
                                                        Modifier.size(44.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                                )
                                        )
                                }
                        } else {
                                Box(
                                        modifier =
                                                Modifier.size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                                iconInfo.second.copy(alpha = 0.15f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = iconInfo.first,
                                                contentDescription = null,
                                                tint = iconInfo.second,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = file.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = FluentTheme.colors.textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Text(
                                                text = formattedDate,
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                        Text(
                                                text =
                                                        if (file.isDirectory)
                                                                "${file.listFiles()?.size ?: 0} items"
                                                        else formatSize(file.length()),
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                }
                        }

                        if (!isMultiSelectMode && onToggleFavorite != null) {
                                // File Context Actions dropdown
                                var showOptions by remember { mutableStateOf(false) }
                                Box {
                                        IconButton(onClick = { showOptions = true }) {
                                                Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Menu",
                                                        tint = FluentTheme.colors.textMuted,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                        DropdownMenu(
                                                expanded = showOptions,
                                                onDismissRequest = { showOptions = false },
                                                modifier =
                                                        Modifier.background(
                                                                FluentTheme.colors.pageBg
                                                        )
                                        ) {
                                                DropdownMenuItem(
                                                        text = {
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        if (isFavorited
                                                                                        )
                                                                                                Icons.Filled
                                                                                                        .Star
                                                                                        else
                                                                                                Icons.Outlined
                                                                                                        .StarBorder,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        if (isFavorited
                                                                                        )
                                                                                                Color(
                                                                                                        0xFFFFC107
                                                                                                )
                                                                                        else
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .textColor,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                18.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                if (isFavorited)
                                                                                        "Unfavorite"
                                                                                else "Favorite",
                                                                                color =
                                                                                        FluentTheme
                                                                                                .colors
                                                                                                .textColor
                                                                        )
                                                                }
                                                        },
                                                        onClick = {
                                                                onToggleFavorite?.invoke()
                                                                showOptions = false
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Rename",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onRename?.invoke()
                                                                showOptions = false
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Share",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onShare?.invoke()
                                                                showOptions = false
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Compress (Zip)",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onCompress?.invoke()
                                                                showOptions = false
                                                        }
                                                )
                                                if (file.extension.lowercase() == "zip") {
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Text(
                                                                                "Extract Here",
                                                                                color =
                                                                                        FluentTheme
                                                                                                .colors
                                                                                                .textColor
                                                                        )
                                                                },
                                                                onClick = {
                                                                        onExtract?.invoke()
                                                                        showOptions = false
                                                                }
                                                        )
                                                }
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Details",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textColor
                                                                )
                                                        },
                                                        onClick = {
                                                                onDetails?.invoke()
                                                                showOptions = false
                                                        }
                                                )
                                                if (!file.isDirectory) {
                                                        Divider(
                                                                color =
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                        )
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Computer,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                if (isConnectedToDesktop
                                                                                                )
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent
                                                                                                else
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textMuted
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.4f
                                                                                                                ),
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        18.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        8.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        "Send to Desktop",
                                                                                        color =
                                                                                                if (isConnectedToDesktop
                                                                                                )
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent
                                                                                                else
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textMuted
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.4f
                                                                                                                ),
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .SemiBold
                                                                                )
                                                                        }
                                                                },
                                                                enabled = isConnectedToDesktop,
                                                                onClick = {
                                                                        onSendToDesktop?.invoke()
                                                                        showOptions = false
                                                                }
                                                        )
                                                }
                                                Divider(color = FluentTheme.colors.panelBorder)
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Delete",
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .dangerText,
                                                                        fontWeight = FontWeight.Bold
                                                                )
                                                        },
                                                        onClick = {
                                                                onDelete?.invoke()
                                                                showOptions = false
                                                        }
                                                )
                                        }
                                }
                        }
                }
                Divider(
                        color = FluentTheme.colors.panelBorder,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 74.dp)
                )
        }
}

// ---------------- RECYCLE BIN VIEWER ----------------
@Composable
fun TrashViewer(
        trashItems: List<LocalTrashManager.TrashItem>,
        onRestore: (LocalTrashManager.TrashItem) -> Unit,
        onDeletePermanently: (LocalTrashManager.TrashItem) -> Unit,
        onEmptyTrash: () -> Unit,
        onItemClick: (LocalTrashManager.TrashItem) -> Unit
) {
        val context = LocalContext.current
        Column(modifier = Modifier.fillMaxSize()) {
                if (trashItems.isNotEmpty()) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = "${trashItems.size} items in Recycle Bin",
                                        fontSize = 12.sp,
                                        color = FluentTheme.colors.textMuted,
                                        fontWeight = FontWeight.Medium
                                )
                                Button(
                                        onClick = onEmptyTrash,
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                FluentTheme.colors.dangerText
                                                ),
                                        contentPadding =
                                                PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(FluentTheme.dims.controlRadius)
                                ) {
                                        Icon(
                                                Icons.Default.DeleteSweep,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                "Empty Recycle Bin",
                                                color = Color.White,
                                                fontSize = 12.sp
                                        )
                                }
                        }
                }

                if (trashItems.isEmpty()) {
                        Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                                Icons.Outlined.DeleteSweep,
                                                contentDescription = null,
                                                tint = FluentTheme.colors.textMuted,
                                                modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                                "Recycle Bin is empty",
                                                color = FluentTheme.colors.textColor,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                } else {
                        val listState = rememberLazyListState()
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        items(trashItems, key = { it.id }) { item ->
                                                val date =
                                                        remember(item.deletedTime) {
                                                                SimpleDateFormat(
                                                                                "yyyy-MM-dd HH:mm",
                                                                                Locale.getDefault()
                                                                        )
                                                                        .format(
                                                                                Date(
                                                                                        item.deletedTime
                                                                                )
                                                                        )
                                                        }
                                                val ext =
                                                        remember(item.name) {
                                                                item.name
                                                                        .substringAfterLast('.')
                                                                        .lowercase()
                                                        }
                                                val isImg =
                                                        ext in
                                                                listOf(
                                                                        "jpg",
                                                                        "jpeg",
                                                                        "png",
                                                                        "webp",
                                                                        "gif",
                                                                        "bmp"
                                                                )
                                                val isVid =
                                                        ext in
                                                                listOf(
                                                                        "mp4",
                                                                        "mkv",
                                                                        "avi",
                                                                        "mov",
                                                                        "webm",
                                                                        "3gp"
                                                                )
                                                val trashFile =
                                                        remember(item.id) {
                                                                File(
                                                                        File(
                                                                                context.filesDir,
                                                                                "omnisearch_trash"
                                                                        ),
                                                                        item.id
                                                                )
                                                        }
                                                Card(
                                                        modifier =
                                                                Modifier.fillMaxWidth().clickable {
                                                                        onItemClick(item)
                                                                },
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                FluentTheme.colors
                                                                                        .surfaceBg
                                                                ),
                                                        border =
                                                                BorderStroke(
                                                                        1.dp,
                                                                        FluentTheme.colors
                                                                                .panelBorder
                                                                )
                                                ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        if ((isImg || isVid) &&
                                                                                        !item.isDirectory
                                                                        ) {
                                                                                AsyncImage(
                                                                                        model =
                                                                                                trashFile,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        contentScale =
                                                                                                ContentScale
                                                                                                        .Crop,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                44.dp
                                                                                                        )
                                                                                                        .clip(
                                                                                                                RoundedCornerShape(
                                                                                                                        8.dp
                                                                                                                )
                                                                                                        )
                                                                                                        .background(
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .panelBorder
                                                                                                        )
                                                                                )
                                                                        } else {
                                                                                Icon(
                                                                                        imageVector =
                                                                                                if (item.isDirectory
                                                                                                )
                                                                                                        Icons.Default
                                                                                                                .Folder
                                                                                                else
                                                                                                        Icons.Default
                                                                                                                .InsertDriveFile,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                if (item.isDirectory
                                                                                                )
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent
                                                                                                else
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .textColor,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        24.dp
                                                                                                )
                                                                                )
                                                                        }
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                10.dp
                                                                                        )
                                                                        )
                                                                        Column(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                item.name,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Bold,
                                                                                        fontSize =
                                                                                                13.sp,
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .textColor,
                                                                                        maxLines =
                                                                                                1,
                                                                                        overflow =
                                                                                                TextOverflow
                                                                                                        .Ellipsis
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                "Deleted: $date | Size: ${formatSize(item.size)}",
                                                                                        fontSize =
                                                                                                11.sp,
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .textMuted
                                                                                )
                                                                        }
                                                                }
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text =
                                                                                "Original: ${item.originalPath}",
                                                                        fontSize = 10.sp,
                                                                        color =
                                                                                FluentTheme.colors
                                                                                        .textMuted,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement.End,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        TextButton(
                                                                                onClick = {
                                                                                        onDeletePermanently(
                                                                                                item
                                                                                        )
                                                                                },
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .textButtonColors(
                                                                                                        contentColor =
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .dangerText
                                                                                                )
                                                                        ) {
                                                                                Text(
                                                                                        "Delete Permanently",
                                                                                        fontSize =
                                                                                                12.sp
                                                                                )
                                                                        }
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Button(
                                                                                onClick = {
                                                                                        onRestore(
                                                                                                item
                                                                                        )
                                                                                },
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .buttonColors(
                                                                                                        containerColor =
                                                                                                                FluentTheme
                                                                                                                        .colors
                                                                                                                        .accent
                                                                                                ),
                                                                                contentPadding =
                                                                                        PaddingValues(
                                                                                                horizontal =
                                                                                                        14.dp,
                                                                                                vertical =
                                                                                                        6.dp
                                                                                        ),
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                FluentTheme
                                                                                                        .dims
                                                                                                        .controlRadius
                                                                                        )
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Restore,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .onAccent,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        14.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        4.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        "Restore",
                                                                                        color =
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .onAccent,
                                                                                        fontSize =
                                                                                                12.sp
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                                FastScrollbar(
                                        listState = listState,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                )
                        }
                }
        }
}

// ---------------- APK DETAILS DIALOG ----------------
@Composable
fun LocalApkDetailsDialog(file: File, onDismiss: () -> Unit) {
        val context = LocalContext.current
        var label by remember { mutableStateOf(file.name) }
        var packageName by remember { mutableStateOf("") }
        var version by remember { mutableStateOf("") }
        var iconDrawable by remember { mutableStateOf<Drawable?>(null) }
        var isValidApk by remember { mutableStateOf(true) }

        LaunchedEffect(file) {
                withContext(Dispatchers.IO) {
                        try {
                                val pm = context.packageManager
                                val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
                                if (info != null) {
                                        val appInfo = info.applicationInfo
                                        if (appInfo != null) {
                                                appInfo.sourceDir = file.absolutePath
                                                appInfo.publicSourceDir = file.absolutePath
                                                val extractedLabel =
                                                        appInfo.loadLabel(pm).toString()
                                                val extractedIcon = appInfo.loadIcon(pm)
                                                val pkgName = info.packageName
                                                val verName = info.versionName ?: "1.0"
                                                withContext(Dispatchers.Main) {
                                                        label = extractedLabel
                                                        iconDrawable = extractedIcon
                                                        packageName = pkgName
                                                        version = verName
                                                }
                                        } else {
                                                isValidApk = false
                                        }
                                } else {
                                        isValidApk = false
                                }
                        } catch (e: Exception) {
                                isValidApk = false
                        }
                }
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("App Package details") },
                text = {
                        if (!isValidApk) {
                                Text(
                                        "Invalid or corrupted APK installer package.",
                                        color = FluentTheme.colors.dangerText
                                )
                        } else {
                                Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        if (iconDrawable != null) {
                                                AndroidView(
                                                        factory = { ctx ->
                                                                android.widget.ImageView(ctx)
                                                                        .apply {
                                                                                setImageDrawable(
                                                                                        iconDrawable
                                                                                )
                                                                        }
                                                        },
                                                        modifier = Modifier.size(64.dp)
                                                )
                                        } else {
                                                Icon(
                                                        Icons.Default.Android,
                                                        contentDescription = null,
                                                        tint = FluentTheme.colors.connectedText,
                                                        modifier = Modifier.size(64.dp)
                                                )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                                label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = FluentTheme.colors.textColor,
                                                textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                "Package: $packageName",
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.textMuted,
                                                textAlign = TextAlign.Center
                                        )
                                        Text(
                                                "Version: $version",
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.textMuted,
                                                textAlign = TextAlign.Center
                                        )
                                        Text(
                                                "Size: ${formatSize(file.length())}",
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.accent,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                        )
                                }
                        }
                },
                confirmButton = {
                        if (isValidApk) {
                                Button(
                                        onClick = {
                                                installApk(context, file)
                                                onDismiss()
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = FluentTheme.colors.accent
                                                )
                                ) { Text("Install", color = FluentTheme.colors.onAccent) }
                        }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) {
                                Text("Cancel", color = FluentTheme.colors.textColor)
                        }
                },
                containerColor = FluentTheme.colors.pageBg
        )
}

enum class VideoScaleMode {
        BEST_FIT,
        STRETCHED,
        CROP_TO_FIT
}

// Helper TextureView-based video player to support Compose transformations (graphicsLayer)
class TextureVideoView(context: android.content.Context) :
        android.view.TextureView(context), android.view.TextureView.SurfaceTextureListener {
        private var mediaPlayer: android.media.MediaPlayer? = null
        private var surface: android.view.Surface? = null
        private var path: String? = null
        private var isPrepared = false
        private var onPreparedListener: ((android.media.MediaPlayer) -> Unit)? = null
        private var onCompletionListener: (() -> Unit)? = null

        private var videoWidth = 0
        private var videoHeight = 0
        private var currentScaleMode = VideoScaleMode.BEST_FIT

        init {
                surfaceTextureListener = this
        }

        val isPlaying: Boolean
                get() = mediaPlayer?.isPlaying ?: false

        val currentPosition: Int
                get() = mediaPlayer?.currentPosition ?: 0

        val duration: Int
                get() = mediaPlayer?.duration ?: 0

        fun setVideoPath(videoPath: String) {
                path = videoPath
                if (isAvailable) {
                        initPlayer()
                }
        }

        fun setOnPreparedListener(listener: (android.media.MediaPlayer) -> Unit) {
                onPreparedListener = listener
        }

        fun setOnCompletionListener(listener: () -> Unit) {
                onCompletionListener = listener
        }

        fun start() {
                mediaPlayer?.start()
        }

        fun pause() {
                mediaPlayer?.pause()
        }

        fun seekTo(msec: Int) {
                val target =
                        if (duration > 0) msec.coerceIn(0, duration) else msec.coerceAtLeast(0)
                try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                mediaPlayer?.seekTo(
                                        target.toLong(),
                                        android.media.MediaPlayer.SEEK_CLOSEST_SYNC
                                )
                        } else {
                                mediaPlayer?.seekTo(target)
                        }
                } catch (e: IllegalStateException) {}
        }

        fun stopPlayback() {
                try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                } catch (e: Exception) {}
                mediaPlayer = null
                isPrepared = false
        }

        fun setScaleMode(mode: VideoScaleMode) {
                currentScaleMode = mode
                adjustAspectRatio()
        }

        private fun adjustAspectRatio() {
                val viewWidth = width.toFloat()
                val viewHeight = height.toFloat()
                if (viewWidth == 0f || viewHeight == 0f || videoWidth == 0 || videoHeight == 0)
                        return

                val matrix = android.graphics.Matrix()
                val sx = viewWidth / videoWidth
                val sy = viewHeight / videoHeight

                when (currentScaleMode) {
                        VideoScaleMode.BEST_FIT -> {
                                // Keep aspect ratio, fit inside view bounds (default)
                                val scale = Math.min(sx, sy)
                                matrix.setScale(
                                        scale / sx,
                                        scale / sy,
                                        viewWidth / 2f,
                                        viewHeight / 2f
                                )
                        }
                        VideoScaleMode.STRETCHED -> {
                                // Stretch to fill view bounds
                                matrix.setScale(1f, 1f)
                        }
                        VideoScaleMode.CROP_TO_FIT -> {
                                // Keep aspect ratio, crop to fill view bounds
                                val scale = Math.max(sx, sy)
                                matrix.setScale(
                                        scale / sx,
                                        scale / sy,
                                        viewWidth / 2f,
                                        viewHeight / 2f
                                )
                        }
                }
                setTransform(matrix)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                adjustAspectRatio()
        }

        private fun initPlayer() {
                if (path == null || surfaceTexture == null) return
                if (mediaPlayer == null) {
                        mediaPlayer =
                                android.media.MediaPlayer().apply {
                                        setOnPreparedListener { mp ->
                                                isPrepared = true
                                                this@TextureVideoView.videoWidth = mp.videoWidth
                                                this@TextureVideoView.videoHeight = mp.videoHeight
                                                post { adjustAspectRatio() }
                                                onPreparedListener?.invoke(mp)
                                        }
                                        setOnCompletionListener { onCompletionListener?.invoke() }
                                }
                }
                try {
                        mediaPlayer?.reset()
                        surface = android.view.Surface(surfaceTexture)
                        mediaPlayer?.setSurface(surface)
                        mediaPlayer?.setDataSource(path)
                        mediaPlayer?.prepareAsync()
                } catch (e: Exception) {
                        e.printStackTrace()
                }
        }

        override fun onSurfaceTextureAvailable(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int
        ) {
                initPlayer()
        }

        override fun onSurfaceTextureSizeChanged(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int
        ) {
                adjustAspectRatio()
        }

        override fun onSurfaceTextureDestroyed(
                surfaceTexture: android.graphics.SurfaceTexture
        ): Boolean {
                mediaPlayer?.setSurface(null)
                surface?.release()
                surface = null
                return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) {}
}

// ---------------- VIDEO PLAYER DIALOG ----------------
@Composable
fun LocalVideoPlayerDialog(file: File, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val activity = context as? Activity
        var isPlaying by remember { mutableStateOf(false) }
        var currentPosition by remember { mutableStateOf(0) }
        var duration by remember { mutableStateOf(0) }
        var videoViewRef by remember { mutableStateOf<TextureVideoView?>(null) }
        var showControls by remember { mutableStateOf(true) }
        var isLandscape by remember { mutableStateOf(false) }
        var showDetails by remember { mutableStateOf(false) }
        var videoZoom by remember { mutableStateOf(1f) }
        var videoOffset by remember { mutableStateOf(Offset.Zero) }

        val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
        val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() }
        val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }
        var currentBrightnessState by remember {
                val initial = activity?.window?.attributes?.screenBrightness ?: -1f
                mutableStateOf(if (initial < 0f) 0.5f else initial)
        }

        DisposableEffect(Unit) {
                onDispose {
                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                screenBrightness = originalBrightness
                        }
                }
        }

        var activeGestureType by remember { mutableStateOf<String?>(null) }
        var gestureValueShow by remember { mutableStateOf("") }
        var showGestureIndicator by remember { mutableStateOf(false) }
        var currentVolumeState by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }

        var seekMessage by remember { mutableStateOf("") }
        var showSeekIndicator by remember { mutableStateOf(false) }

        LaunchedEffect(showSeekIndicator, seekMessage) {
                if (showSeekIndicator) {
                        kotlinx.coroutines.delay(800)
                        showSeekIndicator = false
                }
        }

        var currentScaleMode by remember { mutableStateOf(VideoScaleMode.BEST_FIT) }
        var scaleModeText by remember { mutableStateOf("Best Fit") }
        var showScaleModeIndicator by remember { mutableStateOf(false) }

        LaunchedEffect(showScaleModeIndicator, scaleModeText) {
                if (showScaleModeIndicator) {
                        kotlinx.coroutines.delay(1200)
                        showScaleModeIndicator = false
                }
        }

        if (showDetails) {
                BackHandler { showDetails = false }
        }

        var videoDetails by remember(file) { mutableStateOf<MediaDetails?>(null) }
        LaunchedEffect(file) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val details =
                                try {
                                        val lastMod = file.lastModified()
                                        val dateStr =
                                                java.text.SimpleDateFormat(
                                                                "d MMMM yyyy h:mm a",
                                                                java.util.Locale.getDefault()
                                                        )
                                                        .format(java.util.Date(lastMod))

                                        val bytes = file.length()
                                        val sizeStr =
                                                when {
                                                        bytes >= 1024 * 1024 ->
                                                                String.format(
                                                                        java.util.Locale.US,
                                                                        "%.2f MB",
                                                                        bytes.toFloat() /
                                                                                (1024 * 1024)
                                                                )
                                                        bytes >= 1024 ->
                                                                String.format(
                                                                        java.util.Locale.US,
                                                                        "%.2f KB",
                                                                        bytes.toFloat() / 1024
                                                                )
                                                        else -> "$bytes Bytes"
                                                }

                                        var w = 0
                                        var h = 0
                                        var extraStr = ""

                                        val retriever = android.media.MediaMetadataRetriever()
                                        try {
                                                retriever.setDataSource(file.absolutePath)
                                                w =
                                                        retriever
                                                                .extractMetadata(
                                                                        android.media
                                                                                .MediaMetadataRetriever
                                                                                .METADATA_KEY_VIDEO_WIDTH
                                                                )
                                                                ?.toIntOrNull()
                                                                ?: 0
                                                h =
                                                        retriever
                                                                .extractMetadata(
                                                                        android.media
                                                                                .MediaMetadataRetriever
                                                                                .METADATA_KEY_VIDEO_HEIGHT
                                                                )
                                                                ?.toIntOrNull()
                                                                ?: 0
                                                val durationMs =
                                                        retriever
                                                                .extractMetadata(
                                                                        android.media
                                                                                .MediaMetadataRetriever
                                                                                .METADATA_KEY_DURATION
                                                                )
                                                                ?.toLongOrNull()
                                                                ?: 0L
                                                if (durationMs > 0L) {
                                                        val sec = (durationMs / 1000) % 60
                                                        val min = (durationMs / (1000 * 60)) % 60
                                                        val hr = durationMs / (1000 * 60 * 60)
                                                        extraStr =
                                                                if (hr > 0)
                                                                        String.format(
                                                                                java.util.Locale.US,
                                                                                "%d:%02d:%02d",
                                                                                hr,
                                                                                min,
                                                                                sec
                                                                        )
                                                                else
                                                                        String.format(
                                                                                java.util.Locale.US,
                                                                                "%d:%02d",
                                                                                min,
                                                                                sec
                                                                        )
                                                }
                                        } catch (t: Throwable) {
                                                // Ignore
                                        } finally {
                                                try {
                                                        retriever.release()
                                                } catch (t: Throwable) {}
                                        }

                                        val resStr = if (w > 0 && h > 0) "${w}x${h}" else ""
                                        val folderStr = file.parentFile?.name ?: ""

                                        MediaDetails(
                                                name = file.name,
                                                path = file.parentFile?.absolutePath ?: "",
                                                folder = folderStr,
                                                dateStr = dateStr,
                                                sizeStr = sizeStr,
                                                resolutionStr = resStr,
                                                extraStr = extraStr
                                        )
                                } catch (t: Throwable) {
                                        MediaDetails(
                                                name = file.name,
                                                path = file.parentFile?.absolutePath ?: "",
                                                folder = file.parentFile?.name ?: "",
                                                dateStr = "",
                                                sizeStr = "",
                                                resolutionStr = "",
                                                extraStr = ""
                                        )
                                }
                        videoDetails = details
                }
        }

        val animatedScale by
                animateFloatAsState(
                        targetValue = if (showDetails) 0.65f else 1f,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                )
                )
        val animatedOffsetY by
                animateDpAsState(
                        targetValue = if (showDetails) (-100).dp else 0.dp,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                )
                )

        // Restore orientation and release player resources on dismiss
        DisposableEffect(Unit) {
                onDispose {
                        try {
                                videoViewRef?.stopPlayback()
                        } catch (t: Throwable) {}
                        videoViewRef = null
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
        }

        // Periodically update progress
        LaunchedEffect(isPlaying) {
                while (isPlaying) {
                        videoViewRef?.let {
                                try {
                                        currentPosition = it.currentPosition
                                        if (duration == 0 && it.duration > 0) {
                                                duration = it.duration
                                        }
                                } catch (t: Throwable) {}
                        }
                        kotlinx.coroutines.delay(250)
                }
        }

        LaunchedEffect(showDetails) {
                if (showDetails) {
                        videoZoom = 1f
                        videoOffset = Offset.Zero
                }
        }

        // Auto-hide controls after 3.5 seconds
        LaunchedEffect(showControls, isPlaying) {
                if (showControls && isPlaying) {
                        kotlinx.coroutines.delay(3500)
                        showControls = false
                }
        }

        BackHandler(enabled = !showDetails) { onDismiss() }

        ImmersiveMediaDialogWindow(activity = activity, keepScreenOn = true)

        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        BoxWithConstraints(
                                modifier = Modifier.fillMaxSize().background(Color.Black)
                        ) {
                                val mediaWidth = constraints.maxWidth.toFloat()
                                val mediaHeight = constraints.maxHeight.toFloat()

                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(Color.Black)
                                                        .pointerInput(
                                                                showDetails,
                                                                mediaWidth,
                                                                mediaHeight
                                                        ) {
                                                                if (!showDetails) {
                                                                        awaitEachGesture {
                                                                                awaitFirstDown(
                                                                                        requireUnconsumed =
                                                                                                false
                                                                                )
                                                                                do {
                                                                                        val event =
                                                                                                awaitPointerEvent()
                                                                                        val pointerCount =
                                                                                                event.changes
                                                                                                        .count {
                                                                                                                it.pressed
                                                                                                        }
                                                                                        val zoomChange =
                                                                                                event.calculateZoom()
                                                                                        val panChange =
                                                                                                event.calculatePan()
                                                                                        val shouldHandleZoom =
                                                                                                pointerCount >
                                                                                                        1 ||
                                                                                                        videoZoom >
                                                                                                                1f ||
                                                                                                        zoomChange !=
                                                                                                                1f

                                                                                        if (shouldHandleZoom
                                                                                        ) {
                                                                                                val nextZoom =
                                                                                                        (videoZoom *
                                                                                                                        zoomChange)
                                                                                                                .coerceIn(
                                                                                                                        1f,
                                                                                                                        4f
                                                                                                                )
                                                                                                val nextOffset =
                                                                                                        if (nextZoom >
                                                                                                                        1f
                                                                                                        ) {
                                                                                                                videoOffset +
                                                                                                                        panChange *
                                                                                                                                nextZoom
                                                                                                        } else {
                                                                                                                Offset.Zero
                                                                                                        }
                                                                                                videoZoom =
                                                                                                        nextZoom
                                                                                                videoOffset =
                                                                                                        clampMediaOffset(
                                                                                                                nextOffset,
                                                                                                                nextZoom,
                                                                                                                mediaWidth,
                                                                                                                mediaHeight
                                                                                                        )
                                                                                                if (nextZoom >
                                                                                                                1f
                                                                                                )
                                                                                                        showControls =
                                                                                                                false
                                                                                                event.changes
                                                                                                        .forEach {
                                                                                                                change
                                                                                                                ->
                                                                                                                if (change.positionChange() !=
                                                                                                                                Offset.Zero
                                                                                                                )
                                                                                                                        change.consume()
                                                                                                        }
                                                                                        }
                                                                                } while (event.changes
                                                                                        .any {
                                                                                                it.pressed
                                                                                        })
                                                                        }
                                                                }
                                                        }
                                                        .pointerInput(
                                                                showDetails,
                                                                videoZoom,
                                                                mediaWidth,
                                                                duration
                                                        ) {
                                                                if (videoZoom <= 1.01f) {
                                                                        var totalDragX = 0f
                                                                        var totalDragY = 0f
                                                                        var gestureStartX = 0f
                                                                        var gestureStartPosition =
                                                                                0
                                                                        var pendingSeekPosition =
                                                                                0
                                                                        detectDragGestures(
                                                                                onDragStart = { startOffset ->
                                                                                        totalDragX = 0f
                                                                                        totalDragY = 0f
                                                                                        gestureStartX =
                                                                                                startOffset.x
                                                                                        gestureStartPosition =
                                                                                                videoViewRef
                                                                                                        ?.currentPosition
                                                                                                        ?: currentPosition
                                                                                        pendingSeekPosition =
                                                                                                gestureStartPosition
                                                                                        activeGestureType =
                                                                                                null
                                                                                },
                                                                                onDrag = { change, dragAmount ->
                                                                                        change.consume()
                                                                                        totalDragX +=
                                                                                                dragAmount.x
                                                                                        totalDragY += dragAmount.y
                                                                                        if (activeGestureType ==
                                                                                                        null
                                                                                        ) {
                                                                                                val absX =
                                                                                                        kotlin.math
                                                                                                                .abs(
                                                                                                                        totalDragX
                                                                                                                )
                                                                                                val absY =
                                                                                                        kotlin.math
                                                                                                                .abs(
                                                                                                                        totalDragY
                                                                                                                )
                                                                                                if (absX >= 18f ||
                                                                                                                absY >=
                                                                                                                        18f
                                                                                                ) {
                                                                                                        activeGestureType =
                                                                                                                if (absX >
                                                                                                                                absY *
                                                                                                                                        1.2f
                                                                                                                ) {
                                                                                                                        "seek"
                                                                                                                } else if (gestureStartX <
                                                                                                                                mediaWidth *
                                                                                                                                        0.35f
                                                                                                                ) {
                                                                                                                        currentVolumeState =
                                                                                                                                audioManager
                                                                                                                                        .getStreamVolume(
                                                                                                                                                android.media.AudioManager
                                                                                                                                                        .STREAM_MUSIC
                                                                                                                                        )
                                                                                                                                        .toFloat()
                                                                                                                        gestureValueShow =
                                                                                                                                "Volume: ${((currentVolumeState / maxVolume) * 100).toInt()}%"
                                                                                                                        showGestureIndicator =
                                                                                                                                true
                                                                                                                        "volume"
                                                                                                                } else if (gestureStartX >
                                                                                                                                mediaWidth *
                                                                                                                                        0.65f
                                                                                                                ) {
                                                                                                                        gestureValueShow =
                                                                                                                                "Brightness: ${(currentBrightnessState * 100).toInt()}%"
                                                                                                                        showGestureIndicator =
                                                                                                                                true
                                                                                                                        "brightness"
                                                                                                                } else {
                                                                                                                        "details"
                                                                                                                }
                                                                                                }
                                                                                        }
                                                                                        val deltaY = -dragAmount.y
                                                                                        val sensitivity = 500f
                                                                                        
                                                                                        when (activeGestureType) {
                                                                                                "seek" -> {
                                                                                                        val maxSwipeSeekMs =
                                                                                                                when {
                                                                                                                        duration <=
                                                                                                                                0 ->
                                                                                                                                60_000f
                                                                                                                        duration <
                                                                                                                                60_000 ->
                                                                                                                                duration
                                                                                                                                        .toFloat()
                                                                                                                        else ->
                                                                                                                                minOf(
                                                                                                                                                duration *
                                                                                                                                                        0.20f,
                                                                                                                                                300_000f
                                                                                                                                        )
                                                                                                                                        .coerceAtLeast(
                                                                                                                                                30_000f
                                                                                                                                        )
                                                                                                                }
                                                                                                        val deltaMs =
                                                                                                                ((totalDragX /
                                                                                                                                mediaWidth
                                                                                                                                        .coerceAtLeast(
                                                                                                                                                1f
                                                                                                                                        )) *
                                                                                                                                maxSwipeSeekMs)
                                                                                                                        .toInt()
                                                                                                        pendingSeekPosition =
                                                                                                                (gestureStartPosition +
                                                                                                                                deltaMs)
                                                                                                                        .coerceIn(
                                                                                                                                0,
                                                                                                                                duration
                                                                                                                                        .coerceAtLeast(
                                                                                                                                                0
                                                                                                                                        )
                                                                                                                        )
                                                                                                        val signedDelta =
                                                                                                                pendingSeekPosition -
                                                                                                                        gestureStartPosition
                                                                                                        val prefix =
                                                                                                                if (signedDelta >=
                                                                                                                                0
                                                                                                                )
                                                                                                                        "+"
                                                                                                                else "-"
                                                                                                        seekMessage =
                                                                                                                "$prefix${formatMs(kotlin.math.abs(signedDelta))}  ${formatMs(pendingSeekPosition)}"
                                                                                                        showSeekIndicator =
                                                                                                                true
                                                                                                }
                                                                                                "volume" -> {
                                                                                                        val volumeChange = (deltaY / sensitivity) * maxVolume
                                                                                                        val targetVolume = (currentVolumeState + volumeChange).coerceIn(0f, maxVolume)
                                                                                                        currentVolumeState = targetVolume
                                                                                                        audioManager.setStreamVolume(
                                                                                                                android.media.AudioManager.STREAM_MUSIC,
                                                                                                                targetVolume.toInt(),
                                                                                                                0
                                                                                                        )
                                                                                                        gestureValueShow = "Volume: ${((targetVolume / maxVolume) * 100).toInt()}%"
                                                                                                }
                                                                                                "brightness" -> {
                                                                                                        val brightnessChange = deltaY / sensitivity
                                                                                                        val targetBrightness = (currentBrightnessState + brightnessChange).coerceIn(0.01f, 1f)
                                                                                                        currentBrightnessState = targetBrightness
                                                                                                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                                                                                                screenBrightness = targetBrightness
                                                                                                        }
                                                                                                        gestureValueShow = "Brightness: ${(targetBrightness * 100).toInt()}%"
                                                                                                }
                                                                                        }
                                                                                },
                                                                                onDragEnd = {
                                                                                        showGestureIndicator = false
                                                                                        if (activeGestureType == "seek") {
                                                                                                videoViewRef?.seekTo(
                                                                                                        pendingSeekPosition
                                                                                                )
                                                                                                currentPosition =
                                                                                                        pendingSeekPosition
                                                                                                showSeekIndicator =
                                                                                                        true
                                                                                        } else if (activeGestureType == "details") {
                                                                                                if (totalDragY < -60f && !showDetails) {
                                                                                                        showDetails = true
                                                                                                } else if (totalDragY > 60f && showDetails) {
                                                                                                        showDetails = false
                                                                                                }
                                                                                        }
                                                                                        activeGestureType = null
                                                                                },
                                                                                onDragCancel = {
                                                                                        showGestureIndicator = false
                                                                                        showSeekIndicator = false
                                                                                        activeGestureType = null
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                        .pointerInput(showDetails, mediaWidth, mediaHeight) {
                                                                detectTapGestures(
                                                                        onDoubleTap = { tapOffset ->
                                                                                if (!showDetails) {
                                                                                        if (tapOffset.x < mediaWidth * 0.35f) {
                                                                                                videoViewRef?.let {
                                                                                                        val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                                                                                        it.seekTo(newPos)
                                                                                                        currentPosition = newPos
                                                                                                        seekMessage = "-10s"
                                                                                                        showSeekIndicator = true
                                                                                                }
                                                                                        } else if (tapOffset.x > mediaWidth * 0.65f) {
                                                                                                videoViewRef?.let {
                                                                                                        val newPos = (it.currentPosition + 10000).coerceAtMost(duration)
                                                                                                        it.seekTo(newPos)
                                                                                                        currentPosition = newPos
                                                                                                        seekMessage = "+10s"
                                                                                                        showSeekIndicator = true
                                                                                                }
                                                                                        } else {
                                                                                                videoZoom = if (videoZoom > 1f) 1f else 2f
                                                                                                videoOffset = Offset.Zero
                                                                                                showControls = videoZoom == 1f
                                                                                        }
                                                                                }
                                                                        },
                                                                        onTap = {
                                                                                if (!showDetails)
                                                                                        showControls = !showControls
                                                                        }
                                                                )
                                                        }
                                ) {
                                        AndroidView(
                                                factory = { ctx ->
                                                        TextureVideoView(ctx).apply {
                                                                setScaleMode(currentScaleMode)
                                                                // Defer path initialization
                                                                // slightly to prevent blocking
                                                                // dialog
                                                                // transition
                                                                post {
                                                                        try {
                                                                                setVideoPath(
                                                                                        file.absolutePath
                                                                                )
                                                                        } catch (t: Throwable) {}
                                                                }
                                                                setOnPreparedListener { mp ->
                                                                        duration = mp.duration
                                                                        start()
                                                                        isPlaying = true
                                                                }
                                                                setOnCompletionListener {
                                                                        isPlaying = false
                                                                        currentPosition = duration
                                                                }
                                                                videoViewRef = this
                                                        }
                                                },
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .offset(y = animatedOffsetY)
                                                                .graphicsLayer {
                                                                        scaleX =
                                                                                animatedScale *
                                                                                        videoZoom
                                                                        scaleY =
                                                                                animatedScale *
                                                                                        videoZoom
                                                                        translationX = videoOffset.x
                                                                        translationY = videoOffset.y
                                                                }
                                        )

                                        androidx.compose.animation.AnimatedVisibility(
                                                visible = showControls && !showDetails,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                modifier = Modifier.fillMaxSize()
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .background(
                                                                                Color.Black.copy(
                                                                                        alpha = 0.4f
                                                                                )
                                                                        )
                                                ) {
                                                        // Header with file name and back
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .align(
                                                                                        Alignment
                                                                                                .TopCenter
                                                                                )
                                                                                .statusBarsPadding()
                                                                                .background(
                                                                                        Brush.verticalGradient(
                                                                                                colors =
                                                                                                        listOf(
                                                                                                                Color.Black
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.8f
                                                                                                                        ),
                                                                                                                Color.Transparent
                                                                                                        )
                                                                                                )
                                                                                )
                                                                                .padding(
                                                                                        horizontal =
                                                                                                16.dp,
                                                                                        vertical =
                                                                                                20.dp
                                                                                ),
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween,
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                IconButton(onClick = onDismiss) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .ArrowBack,
                                                                                contentDescription =
                                                                                        "Back",
                                                                                tint = Color.White
                                                                        )
                                                                }
                                                                Text(
                                                                        text = file.name,
                                                                        color = Color.White,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        fontSize = 16.sp,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis,
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        12.dp
                                                                                        )
                                                                )
                                                                // Landscape rotation toggle
                                                                IconButton(
                                                                        onClick = {
                                                                                isLandscape =
                                                                                        !isLandscape
                                                                                activity?.requestedOrientation =
                                                                                        if (isLandscape
                                                                                        ) {
                                                                                                ActivityInfo
                                                                                                        .SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                                                        } else {
                                                                                                ActivityInfo
                                                                                                        .SCREEN_ORIENTATION_UNSPECIFIED
                                                                                        }
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .ScreenRotation,
                                                                                contentDescription =
                                                                                        "Rotate",
                                                                                tint =
                                                                                        if (isLandscape
                                                                                        )
                                                                                                FluentTheme
                                                                                                        .colors
                                                                                                        .accent
                                                                                        else
                                                                                                Color.White
                                                                        )
                                                                }
                                                                // Aspect ratio / Scale mode toggle
                                                                // button
                                                                IconButton(
                                                                        onClick = {
                                                                                val nextMode =
                                                                                        when (currentScaleMode
                                                                                        ) {
                                                                                                VideoScaleMode
                                                                                                        .BEST_FIT ->
                                                                                                        VideoScaleMode
                                                                                                                .STRETCHED
                                                                                                VideoScaleMode
                                                                                                        .STRETCHED ->
                                                                                                        VideoScaleMode
                                                                                                                .CROP_TO_FIT
                                                                                                VideoScaleMode
                                                                                                        .CROP_TO_FIT ->
                                                                                                        VideoScaleMode
                                                                                                                .BEST_FIT
                                                                                        }
                                                                                currentScaleMode =
                                                                                        nextMode
                                                                                videoViewRef
                                                                                        ?.setScaleMode(
                                                                                                nextMode
                                                                                        )
                                                                                scaleModeText =
                                                                                        when (nextMode
                                                                                        ) {
                                                                                                VideoScaleMode
                                                                                                        .BEST_FIT ->
                                                                                                        "Best Fit"
                                                                                                VideoScaleMode
                                                                                                        .STRETCHED ->
                                                                                                        "Stretched"
                                                                                                VideoScaleMode
                                                                                                        .CROP_TO_FIT ->
                                                                                                        "Crop to Fit"
                                                                                        }
                                                                                showScaleModeIndicator =
                                                                                        true
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .AspectRatio,
                                                                                contentDescription =
                                                                                        "Aspect Ratio",
                                                                                tint = Color.White
                                                                        )
                                                                }
                                                        }

                                                        // Center Play/Pause button
                                                        Box(
                                                                modifier =
                                                                        Modifier.align(
                                                                                        Alignment
                                                                                                .Center
                                                                                )
                                                                                .size(72.dp)
                                                                                .clip(CircleShape)
                                                                                .background(
                                                                                        Color.Black
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.6f
                                                                                                )
                                                                                )
                                                                                .clickable {
                                                                                        videoViewRef
                                                                                                ?.let {
                                                                                                        if (it.isPlaying
                                                                                                        ) {
                                                                                                                it.pause()
                                                                                                                isPlaying =
                                                                                                                        false
                                                                                                        } else {
                                                                                                                it.start()
                                                                                                                isPlaying =
                                                                                                                        true
                                                                                                        }
                                                                                                }
                                                                                },
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                if (isPlaying)
                                                                                        Icons.Default
                                                                                                .Pause
                                                                                else
                                                                                        Icons.Default
                                                                                                .PlayArrow,
                                                                        contentDescription =
                                                                                if (isPlaying)
                                                                                        "Pause"
                                                                                else "Play",
                                                                        tint = Color.White,
                                                                        modifier =
                                                                                Modifier.size(40.dp)
                                                                )
                                                        }

                                                        // Bottom Seek and duration bar
                                                        Column(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .align(
                                                                                        Alignment
                                                                                                .BottomCenter
                                                                                )
                                                                                .background(
                                                                                        Brush.verticalGradient(
                                                                                                colors =
                                                                                                        listOf(
                                                                                                                Color.Transparent,
                                                                                                                Color.Black
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.8f
                                                                                                                        )
                                                                                                        )
                                                                                        )
                                                                                )
                                                                                .padding(
                                                                                        horizontal =
                                                                                                24.dp,
                                                                                        vertical =
                                                                                                24.dp
                                                                                )
                                                        ) {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        formatMs(
                                                                                                currentPosition
                                                                                        ),
                                                                                color = Color.White,
                                                                                fontSize = 12.sp
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        formatMs(
                                                                                                duration
                                                                                        ),
                                                                                color =
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.8f
                                                                                                ),
                                                                                fontSize = 12.sp
                                                                        )
                                                                }
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                Slider(
                                                                        value =
                                                                                if (duration > 0)
                                                                                        currentPosition
                                                                                                .toFloat()
                                                                                else 0f,
                                                                        onValueChange = { value ->
                                                                                currentPosition =
                                                                                        value.toInt()
                                                                        },
                                                                        onValueChangeFinished = {
                                                                                videoViewRef
                                                                                        ?.seekTo(
                                                                                                currentPosition
                                                                                        )
                                                                        },
                                                                        valueRange =
                                                                                0f..(if (duration >
                                                                                                        0
                                                                                        )
                                                                                                duration.toFloat()
                                                                                        else 100f),
                                                                        colors =
                                                                                SliderDefaults
                                                                                        .colors(
                                                                                                thumbColor =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent,
                                                                                                activeTrackColor =
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent,
                                                                                                inactiveTrackColor =
                                                                                                        Color.White
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.3f
                                                                                                                )
                                                                                        )
                                                                )
                                                        }
                                                }
                                        }

                                        // Samsung-style Bottom Details Card
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible = showDetails,
                                                enter =
                                                        slideInVertically(initialOffsetY = { it }) +
                                                                fadeIn(),
                                                exit =
                                                        slideOutVertically(targetOffsetY = { it }) +
                                                                fadeOut(),
                                                modifier = Modifier.align(Alignment.BottomCenter)
                                        ) {
                                                Surface(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .navigationBarsPadding(),
                                                        color = Color.Black,
                                                        shape =
                                                                RoundedCornerShape(
                                                                        topStart = 24.dp,
                                                                        topEnd = 24.dp
                                                                )
                                                ) {
                                                        val activeVideoDetails =
                                                                videoDetails
                                                                        ?: MediaDetails(
                                                                                name = file.name,
                                                                                path =
                                                                                        file.parentFile
                                                                                                ?.absolutePath
                                                                                                ?: "",
                                                                                folder =
                                                                                        file.parentFile
                                                                                                ?.name
                                                                                                ?: "",
                                                                                dateStr =
                                                                                        "Loading details...",
                                                                                sizeStr = "",
                                                                                resolutionStr = "",
                                                                                extraStr = ""
                                                                        )
                                                        Column(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(
                                                                                        horizontal =
                                                                                                24.dp,
                                                                                        vertical =
                                                                                                20.dp
                                                                                ),
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                // Drag Handle indicator
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(
                                                                                                40.dp,
                                                                                                4.dp
                                                                                        )
                                                                                        .clip(
                                                                                                CircleShape
                                                                                        )
                                                                                        .background(
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.2f
                                                                                                        )
                                                                                        )
                                                                                        .align(
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                        )
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )

                                                                // Date
                                                                if (activeVideoDetails.dateStr
                                                                                .isNotEmpty()
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        activeVideoDetails
                                                                                                .dateStr,
                                                                                color = Color.White,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 18.sp
                                                                        )
                                                                }

                                                                // Filename
                                                                Text(
                                                                        text =
                                                                                activeVideoDetails
                                                                                        .name,
                                                                        color = Color.White,
                                                                        fontSize = 14.sp,
                                                                        fontWeight =
                                                                                FontWeight.Normal
                                                                )

                                                                // File Path
                                                                Text(
                                                                        text =
                                                                                activeVideoDetails
                                                                                        .path,
                                                                        color =
                                                                                Color.White.copy(
                                                                                        alpha = 0.5f
                                                                                ),
                                                                        fontSize = 12.sp,
                                                                        fontWeight =
                                                                                FontWeight.Normal
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )

                                                                // Folder Name Header
                                                                Text(
                                                                        text =
                                                                                activeVideoDetails
                                                                                        .folder,
                                                                        color =
                                                                                Color.White.copy(
                                                                                        alpha = 0.7f
                                                                                ),
                                                                        fontSize = 14.sp,
                                                                        fontWeight =
                                                                                FontWeight.SemiBold
                                                                )

                                                                // Specs Details: Size | Resolution
                                                                // | Duration
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                12.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        activeVideoDetails
                                                                                                .sizeStr,
                                                                                color =
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.5f
                                                                                                ),
                                                                                fontSize = 13.sp
                                                                        )
                                                                        if (activeVideoDetails
                                                                                        .resolutionStr
                                                                                        .isNotEmpty()
                                                                        ) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                1.dp,
                                                                                                                12.dp
                                                                                                        )
                                                                                                        .background(
                                                                                                                Color.White
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.2f
                                                                                                                        )
                                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                activeVideoDetails
                                                                                                        .resolutionStr,
                                                                                        color =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.5f
                                                                                                        ),
                                                                                        fontSize =
                                                                                                13.sp
                                                                                )
                                                                        }
                                                                        if (activeVideoDetails
                                                                                        .extraStr
                                                                                        .isNotEmpty()
                                                                        ) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                1.dp,
                                                                                                                12.dp
                                                                                                        )
                                                                                                        .background(
                                                                                                                Color.White
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.2f
                                                                                                                        )
                                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                activeVideoDetails
                                                                                                        .extraStr,
                                                                                        color =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.5f
                                                                                                        ),
                                                                                        fontSize =
                                                                                                13.sp
                                                                                )
                                                                        }
                                                                }

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }

                                        // Scale mode indicator overlay
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible = showScaleModeIndicator,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                modifier = Modifier.align(Alignment.Center)
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.clip(
                                                                                RoundedCornerShape(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                Color.Black.copy(
                                                                                        alpha = 0.7f
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 12.dp
                                                                        )
                                                ) {
                                                        Text(
                                                                text = scaleModeText,
                                                                color = Color.White,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                        )
                                                }
                                        }

                                        // Gesture value indicator
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible = showGestureIndicator,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                modifier = Modifier.align(Alignment.Center)
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.clip(
                                                                                RoundedCornerShape(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                Color.Black.copy(
                                                                                        alpha = 0.7f
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 12.dp
                                                                        )
                                                ) {
                                                        Text(
                                                                text = gestureValueShow,
                                                                color = Color.White,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                        )
                                                }
                                        }

                                        // Seek indicator
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible = showSeekIndicator,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                modifier = Modifier.align(Alignment.Center)
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.clip(
                                                                                RoundedCornerShape(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                Color.Black.copy(
                                                                                        alpha = 0.7f
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 12.dp
                                                                        )
                                                ) {
                                                        Text(
                                                                text = seekMessage,
                                                                color = Color.White,
                                                                fontSize = 24.sp,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                }
                                        }
                                }
                        }
        }
}

// ---------------- LOCAL FILE DETAILS DIALOG ----------------
@Composable
fun LocalFileDetailsDialog(file: File, onDismiss: () -> Unit) {
        val formattedDate =
                remember(file.lastModified()) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date(file.lastModified()))
                }
        val sizeText =
                remember(file) {
                        if (file.isDirectory) {
                                val itemsCount = file.listFiles()?.size ?: 0
                                "$itemsCount items"
                        } else {
                                formatSize(file.length())
                        }
                }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                        Text(
                                text = "Properties",
                                color = FluentTheme.colors.textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                        )
                },
                text = {
                        Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Column {
                                        Text(
                                                "Name",
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                        Text(
                                                file.name,
                                                fontSize = 14.sp,
                                                color = FluentTheme.colors.textColor,
                                                fontWeight = FontWeight.Medium
                                        )
                                }
                                Column {
                                        Text(
                                                "Location",
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                        Text(
                                                file.absolutePath,
                                                fontSize = 12.sp,
                                                color = FluentTheme.colors.textColor
                                        )
                                }
                                Column {
                                        Text(
                                                "Size",
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                        Text(
                                                sizeText,
                                                fontSize = 14.sp,
                                                color = FluentTheme.colors.textColor,
                                                fontWeight = FontWeight.Medium
                                        )
                                }
                                Column {
                                        Text(
                                                "Last modified",
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                        Text(
                                                formattedDate,
                                                fontSize = 14.sp,
                                                color = FluentTheme.colors.textColor
                                        )
                                }
                        }
                },
                confirmButton = {
                        Button(
                                onClick = onDismiss,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = FluentTheme.colors.accent
                                        )
                        ) { Text("Close", color = FluentTheme.colors.onAccent) }
                },
                containerColor = FluentTheme.colors.pageBg
        )
}

// ---------------- LOCAL PDF VIEWER DIALOG ----------------
@Composable
fun LocalPdfViewerDialog(file: File, onDismiss: () -> Unit) {
        var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(file) {
                withContext(Dispatchers.IO) {
                        try {
                                val input =
                                        ParcelFileDescriptor.open(
                                                file,
                                                ParcelFileDescriptor.MODE_READ_ONLY
                                        )
                                val renderer = PdfRenderer(input)
                                val pageCount = renderer.pageCount

                                val renderedBitmaps = mutableListOf<Bitmap>()
                                val maxPages =
                                        minOf(pageCount, 15) // Render first 15 pages for safety
                                for (i in 0 until maxPages) {
                                        val page = renderer.openPage(i)
                                        val width = page.width * 2
                                        val height = page.height * 2
                                        val bitmap =
                                                Bitmap.createBitmap(
                                                        width,
                                                        height,
                                                        Bitmap.Config.ARGB_8888
                                                )

                                        val canvas = android.graphics.Canvas(bitmap)
                                        canvas.drawColor(android.graphics.Color.WHITE)

                                        page.render(
                                                bitmap,
                                                null,
                                                null,
                                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                        )
                                        renderedBitmaps.add(bitmap)
                                        page.close()
                                }
                                renderer.close()
                                input.close()

                                withContext(Dispatchers.Main) {
                                        pages = renderedBitmaps
                                        loading = false
                                }
                        } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                        error = e.localizedMessage ?: "Failed to render PDF"
                                        loading = false
                                }
                        }
                }
        }

        Dialog(onDismissRequest = onDismiss) {
                Surface(
                        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = FluentTheme.colors.pageBg,
                        border = BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                                // Header
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = file.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = FluentTheme.colors.textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = onDismiss) {
                                                Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Close",
                                                        tint = FluentTheme.colors.textColor
                                                )
                                        }
                                }

                                Divider(color = FluentTheme.colors.panelBorder)

                                // Render view
                                Box(
                                        modifier =
                                                Modifier.weight(1f)
                                                        .fillMaxWidth()
                                                        .pointerInput(Unit) {
                                                                detectTransformGestures {
                                                                        _,
                                                                        pan,
                                                                        zoom,
                                                                        _ ->
                                                                        scale =
                                                                                (scale * zoom)
                                                                                        .coerceIn(
                                                                                                1f,
                                                                                                5f
                                                                                        )
                                                                        if (scale > 1f)
                                                                                offset += pan
                                                                        else offset = Offset.Zero
                                                                }
                                                        }
                                                        .pointerInput(Unit) {
                                                                detectTapGestures(
                                                                        onDoubleTap = {
                                                                                if (scale > 1f) {
                                                                                        scale = 1f
                                                                                        offset =
                                                                                                Offset.Zero
                                                                                } else {
                                                                                        scale = 2.5f
                                                                                }
                                                                        }
                                                                )
                                                        },
                                        contentAlignment = Alignment.Center
                                ) {
                                        if (loading) {
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        CircularProgressIndicator(
                                                                color = FluentTheme.colors.accent
                                                        )
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        Text(
                                                                "Rendering local PDF pages...",
                                                                fontSize = 12.sp,
                                                                color = FluentTheme.colors.textMuted
                                                        )
                                                }
                                        } else if (error != null) {
                                                Text(
                                                        "Error rendering: $error",
                                                        color = FluentTheme.colors.dangerText,
                                                        fontSize = 13.sp,
                                                        modifier = Modifier.padding(16.dp)
                                                )
                                        } else {
                                                LazyColumn(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .graphicsLayer(
                                                                                scaleX = scale,
                                                                                scaleY = scale,
                                                                                translationX =
                                                                                        offset.x,
                                                                                translationY =
                                                                                        offset.y
                                                                        ),
                                                        contentPadding = PaddingValues(16.dp),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(14.dp)
                                                ) {
                                                        itemsIndexed(pages) { index, bitmap ->
                                                                Column(
                                                                        horizontalAlignment =
                                                                                Alignment
                                                                                        .CenterHorizontally
                                                                ) {
                                                                        Card(
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                4.dp
                                                                                        ),
                                                                                colors =
                                                                                        CardDefaults
                                                                                                .cardColors(
                                                                                                        containerColor =
                                                                                                                Color.White
                                                                                                ),
                                                                                elevation =
                                                                                        CardDefaults
                                                                                                .cardElevation(
                                                                                                        defaultElevation =
                                                                                                                3.dp
                                                                                                ),
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                                                .aspectRatio(
                                                                                                        bitmap.width
                                                                                                                .toFloat() /
                                                                                                                bitmap.height
                                                                                                                        .toFloat()
                                                                                                )
                                                                        ) {
                                                                                Image(
                                                                                        bitmap =
                                                                                                bitmap.asImageBitmap(),
                                                                                        contentDescription =
                                                                                                "Page ${index + 1}",
                                                                                        contentScale =
                                                                                                ContentScale
                                                                                                        .Fit,
                                                                                        modifier =
                                                                                                Modifier.fillMaxSize()
                                                                                )
                                                                        }
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                "Page ${index + 1} of ${pages.size}",
                                                                                fontSize = 11.sp,
                                                                                color =
                                                                                        FluentTheme
                                                                                                .colors
                                                                                                .textMuted
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

// ---------------- BACKGROUND MUSIC PLAYER EXPANDED DIALOG ----------------
@Composable
fun LocalMusicPlayerDialog(file: File, onDismiss: () -> Unit) {
        val duration = LocalMusicPlayerManager.duration
        val position = LocalMusicPlayerManager.currentPlaybackPosition

        Dialog(onDismissRequest = onDismiss) {
                Surface(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = FluentTheme.colors.pageBg,
                        border = BorderStroke(1.dp, FluentTheme.colors.panelBorder)
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                // Header
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                "Now Playing",
                                                fontWeight = FontWeight.Bold,
                                                color = FluentTheme.colors.textMuted,
                                                fontSize = 13.sp
                                        )
                                        IconButton(onClick = onDismiss) {
                                                Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Close",
                                                        tint = FluentTheme.colors.textColor
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Beautiful Glowing Vinyl Disc animation
                                Box(
                                        modifier =
                                                Modifier.size(150.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                Brush.linearGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        FluentTheme
                                                                                                .colors
                                                                                                .accent,
                                                                                        Color(
                                                                                                0xFF60CDFF
                                                                                        )
                                                                                )
                                                                )
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(64.dp)
                                        )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                        text = file.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = FluentTheme.colors.textColor,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Seek slider
                                var sliderValue by
                                        remember(position) { mutableStateOf(position.toFloat()) }
                                Slider(
                                        value = sliderValue,
                                        onValueChange = { sliderValue = it },
                                        onValueChangeFinished = {
                                                LocalMusicPlayerManager.seekTo(sliderValue.toInt())
                                        },
                                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                        colors =
                                                SliderDefaults.colors(
                                                        activeTrackColor =
                                                                FluentTheme.colors.accent,
                                                        thumbColor = FluentTheme.colors.accent
                                                ),
                                        modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Text(
                                                formatMs(position),
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                        Text(
                                                formatMs(duration),
                                                fontSize = 11.sp,
                                                color = FluentTheme.colors.textMuted
                                        )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Controls row
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Loop toggle
                                        IconButton(
                                                onClick = { LocalMusicPlayerManager.toggleLoop() },
                                                modifier = Modifier.size(40.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.Repeat,
                                                        contentDescription = "Loop",
                                                        tint =
                                                                if (LocalMusicPlayerManager
                                                                                .isLooping
                                                                )
                                                                        FluentTheme.colors.accent
                                                                else FluentTheme.colors.textMuted,
                                                        modifier = Modifier.size(22.dp)
                                                )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                                onClick = { LocalMusicPlayerManager.previous() },
                                                modifier = Modifier.size(48.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.SkipPrevious,
                                                        contentDescription = "Prev",
                                                        tint = FluentTheme.colors.textColor,
                                                        modifier = Modifier.size(28.dp)
                                                )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Center Play/Pause Floating Action Button
                                        FloatingActionButton(
                                                onClick = {
                                                        if (LocalMusicPlayerManager.isPlaying) {
                                                                LocalMusicPlayerManager.pause()
                                                        } else {
                                                                LocalMusicPlayerManager.resume()
                                                        }
                                                },
                                                containerColor = FluentTheme.colors.accent,
                                                contentColor = FluentTheme.colors.onAccent,
                                                shape = CircleShape,
                                                modifier = Modifier.size(56.dp)
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                if (LocalMusicPlayerManager
                                                                                .isPlaying
                                                                )
                                                                        Icons.Default.Pause
                                                                else Icons.Default.PlayArrow,
                                                        contentDescription = "Play/Pause",
                                                        tint = FluentTheme.colors.onAccent,
                                                        modifier = Modifier.size(32.dp)
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(
                                                onClick = { LocalMusicPlayerManager.next() },
                                                modifier = Modifier.size(48.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.SkipNext,
                                                        contentDescription = "Next",
                                                        tint = FluentTheme.colors.textColor,
                                                        modifier = Modifier.size(28.dp)
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        // Placeholder for visual balance
                                        Spacer(modifier = Modifier.size(40.dp))
                                }
                        }
                }
        }
}

private data class MediaDetails(
        val name: String,
        val path: String,
        val folder: String,
        val dateStr: String,
        val sizeStr: String,
        val resolutionStr: String,
        val extraStr: String
)

private object VideoThumbnailCache {
        private val cache = android.util.LruCache<String, android.graphics.Bitmap>(40)
        private val semaphore = kotlinx.coroutines.sync.Semaphore(permits = 1)

        fun get(path: String): android.graphics.Bitmap? {
                synchronized(cache) {
                        return cache.get(path)
                }
        }

        suspend fun getOrCreate(
                file: java.io.File,
                size: android.util.Size? = null,
                isMicro: Boolean = false
        ): android.graphics.Bitmap? {
                val path = file.absolutePath
                get(path)?.let {
                        return it
                }

                return semaphore.withPermit {
                        // Check again in case it was loaded while waiting
                        get(path)?.let {
                                return@withPermit it
                        }

                        try {
                                val bm =
                                        if (android.os.Build.VERSION.SDK_INT >=
                                                        android.os.Build.VERSION_CODES.Q &&
                                                        size != null
                                        ) {
                                                try {
                                                        android.media.ThumbnailUtils
                                                                .createVideoThumbnail(
                                                                        file,
                                                                        size,
                                                                        null
                                                                )
                                                } catch (t: Throwable) {
                                                        null
                                                }
                                        } else null

                                val finalBm =
                                        bm
                                                ?: try {
                                                        @Suppress("DEPRECATION")
                                                        val kind =
                                                                if (isMicro)
                                                                        android.provider.MediaStore
                                                                                .Video.Thumbnails
                                                                                .MICRO_KIND
                                                                else
                                                                        android.provider.MediaStore
                                                                                .Video.Thumbnails
                                                                                .MINI_KIND
                                                        android.media.ThumbnailUtils
                                                                .createVideoThumbnail(path, kind)
                                                } catch (t: Throwable) {
                                                        null
                                                }

                                if (finalBm != null) {
                                        synchronized(cache) { cache.put(path, finalBm) }
                                }
                                finalBm
                        } catch (t: Throwable) {
                                null
                        }
                }
        }
}

// ---------------- FULL SCREEN IMAGE SWIPE GALLERY VIEWER DIALOG ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenSwipeImageViewerDialog(
        imageFiles: List<File>,
        initialIndex: Int,
        onDismiss: () -> Unit,
        isFavorited: (File) -> Boolean,
        onToggleFavorite: (File) -> Unit,
        onDelete: (File) -> Unit,
        onShare: (File) -> Unit,
        onSendToDesktop: (File) -> Unit,
        onImageSaved: (originalFile: File, savedFile: File) -> Unit = { _, _ -> }
) {
        val context = LocalContext.current
        val activity = context as? Activity
        var isEditingMode by remember { mutableStateOf(false) }
        val pagerState =
                rememberPagerState(initialPage = initialIndex, pageCount = { imageFiles.size })
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var rotation by remember { mutableStateOf(0f) }
        var isCropMode by remember { mutableStateOf(false) }
        var showEditBar by remember { mutableStateOf(false) }
        var showControls by remember { mutableStateOf(true) }
        var showDetails by remember { mutableStateOf(false) }

        if (showDetails) {
                BackHandler { showDetails = false }
        }

        val currentFile = imageFiles[pagerState.currentPage]
        var mediaDetails by remember(currentFile) { mutableStateOf<MediaDetails?>(null) }
        LaunchedEffect(currentFile) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val details =
                                try {
                                        val lastMod = currentFile.lastModified()
                                        val dateStr =
                                                java.text.SimpleDateFormat(
                                                                "d MMMM yyyy h:mm a",
                                                                java.util.Locale.getDefault()
                                                        )
                                                        .format(java.util.Date(lastMod))

                                        val bytes = currentFile.length()
                                        val sizeStr =
                                                when {
                                                        bytes >= 1024 * 1024 ->
                                                                String.format(
                                                                        java.util.Locale.US,
                                                                        "%.2f MB",
                                                                        bytes.toFloat() /
                                                                                (1024 * 1024)
                                                                )
                                                        bytes >= 1024 ->
                                                                String.format(
                                                                        java.util.Locale.US,
                                                                        "%.2f KB",
                                                                        bytes.toFloat() / 1024
                                                                )
                                                        else -> "$bytes Bytes"
                                                }

                                        val ext = currentFile.extension.lowercase()
                                        val isVideo =
                                                ext in listOf("mp4", "mkv", "webm", "3gp", "avi")

                                        var w = 0
                                        var h = 0
                                        var extraStr = ""

                                        if (isVideo) {
                                                val retriever =
                                                        android.media.MediaMetadataRetriever()
                                                try {
                                                        retriever.setDataSource(
                                                                currentFile.absolutePath
                                                        )
                                                        w =
                                                                retriever
                                                                        .extractMetadata(
                                                                                android.media
                                                                                        .MediaMetadataRetriever
                                                                                        .METADATA_KEY_VIDEO_WIDTH
                                                                        )
                                                                        ?.toIntOrNull()
                                                                        ?: 0
                                                        h =
                                                                retriever
                                                                        .extractMetadata(
                                                                                android.media
                                                                                        .MediaMetadataRetriever
                                                                                        .METADATA_KEY_VIDEO_HEIGHT
                                                                        )
                                                                        ?.toIntOrNull()
                                                                        ?: 0
                                                        val durationMs =
                                                                retriever
                                                                        .extractMetadata(
                                                                                android.media
                                                                                        .MediaMetadataRetriever
                                                                                        .METADATA_KEY_DURATION
                                                                        )
                                                                        ?.toLongOrNull()
                                                                        ?: 0L
                                                        if (durationMs > 0L) {
                                                                val sec = (durationMs / 1000) % 60
                                                                val min =
                                                                        (durationMs / (1000 * 60)) %
                                                                                60
                                                                val hr =
                                                                        durationMs /
                                                                                (1000 * 60 * 60)
                                                                extraStr =
                                                                        if (hr > 0)
                                                                                String.format(
                                                                                        java.util
                                                                                                .Locale
                                                                                                .US,
                                                                                        "%d:%02d:%02d",
                                                                                        hr,
                                                                                        min,
                                                                                        sec
                                                                                )
                                                                        else
                                                                                String.format(
                                                                                        java.util
                                                                                                .Locale
                                                                                                .US,
                                                                                        "%d:%02d",
                                                                                        min,
                                                                                        sec
                                                                                )
                                                        }
                                                } catch (t: Throwable) {
                                                        // Ignore
                                                } finally {
                                                        try {
                                                                retriever.release()
                                                        } catch (t: Throwable) {}
                                                }
                                        } else {
                                                try {
                                                        val options =
                                                                android.graphics.BitmapFactory
                                                                        .Options()
                                                                        .apply {
                                                                                inJustDecodeBounds =
                                                                                        true
                                                                        }
                                                        android.graphics.BitmapFactory.decodeFile(
                                                                currentFile.absolutePath,
                                                                options
                                                        )
                                                        w = options.outWidth
                                                        h = options.outHeight
                                                        if (w > 0 && h > 0) {
                                                                val mp =
                                                                        (w.toFloat() *
                                                                                h.toFloat()) /
                                                                                1_000_000f
                                                                extraStr =
                                                                        String.format(
                                                                                java.util.Locale.US,
                                                                                "%.1f MP",
                                                                                mp
                                                                        )
                                                        }
                                                } catch (t: Throwable) {}
                                        }

                                        val resStr = if (w > 0 && h > 0) "${w}x${h}" else ""
                                        val folderStr = currentFile.parentFile?.name ?: ""

                                        MediaDetails(
                                                name = currentFile.name,
                                                path = currentFile.parentFile?.absolutePath ?: "",
                                                folder = folderStr,
                                                dateStr = dateStr,
                                                sizeStr = sizeStr,
                                                resolutionStr = resStr,
                                                extraStr = extraStr
                                        )
                                } catch (t: Throwable) {
                                        MediaDetails(
                                                name = currentFile.name,
                                                path = currentFile.parentFile?.absolutePath ?: "",
                                                folder = currentFile.parentFile?.name ?: "",
                                                dateStr = "",
                                                sizeStr = "",
                                                resolutionStr = "",
                                                extraStr = ""
                                        )
                                }
                        mediaDetails = details
                }
        }

        val animatedScale by
                animateFloatAsState(
                        targetValue = if (showDetails) 0.65f else 1f,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                )
                )
        val animatedOffsetY by
                animateDpAsState(
                        targetValue = if (showDetails) (-100).dp else 0.dp,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                )
                )

        // Interactive Crop boundary states (normalized 0f to 1f)
        var cropLeft by remember { mutableStateOf(0.1f) }
        var cropTop by remember { mutableStateOf(0.1f) }
        var cropRight by remember { mutableStateOf(0.9f) }
        var cropBottom by remember { mutableStateOf(0.9f) }

        // Reset editing state when swiping to a new page or entering crop mode
        LaunchedEffect(pagerState.currentPage) {
                rotation = 0f
                isCropMode = false
                scale = 1f
                offset = Offset.Zero
                cropLeft = 0.1f
                cropTop = 0.1f
                cropRight = 0.9f
                cropBottom = 0.9f
                showControls = true // Bring controls back on swiping to make sure count updates
                showDetails = false // Hide details on swipe to next image
        }

        LaunchedEffect(isCropMode) {
                if (isCropMode) {
                        scale = 1f
                        offset = Offset.Zero
                        cropLeft = 0.1f
                        cropTop = 0.1f
                        cropRight = 0.9f
                        cropBottom = 0.9f
                }
        }

        // Auto-hide controls effect: wait 3.5 seconds then hide if showEditBar & crop mode are
        // inactive
        LaunchedEffect(showControls, pagerState.currentPage, showEditBar, isCropMode) {
                if (showControls && !showEditBar && !isCropMode) {
                        kotlinx.coroutines.delay(3500)
                        showControls = false
                }
        }

        BackHandler(enabled = !showDetails) { onDismiss() }

        ImmersiveMediaDialogWindow(activity = activity)

        Surface(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        color = Color.Black
                ) {
                        if (isEditingMode) {
                                val currentFile = imageFiles[pagerState.currentPage]
                                AdvancedImageEditor(
                                        file = currentFile,
                                        onDismiss = { isEditingMode = false },
                                        onSaved = { savedFile ->
                                                isEditingMode = false
                                                onImageSaved(currentFile, savedFile)
                                        }
                                )
                        } else {
                                BoxWithConstraints(
                                        modifier =
                                                Modifier.fillMaxSize().pointerInput(
                                                                showDetails,
                                                                isCropMode,
                                                                scale
                                                        ) {
                                                        if (!isCropMode && scale == 1f) {
                                                                var totalDragY = 0f
                                                                detectDragGestures(
                                                                        onDragStart = {
                                                                                _:
                                                                                        androidx.compose.ui.geometry.Offset
                                                                                ->
                                                                                totalDragY = 0f
                                                                        },
                                                                        onDrag = {
                                                                                change:
                                                                                        androidx.compose.ui.input.pointer.PointerInputChange,
                                                                                dragAmount:
                                                                                        androidx.compose.ui.geometry.Offset
                                                                                ->
                                                                                change.consume()
                                                                                totalDragY +=
                                                                                        dragAmount.y
                                                                        },
                                                                        onDragEnd = {
                                                                                if (totalDragY <
                                                                                                -60f &&
                                                                                                !showDetails
                                                                                ) {
                                                                                        showDetails =
                                                                                                true
                                                                                } else if (totalDragY >
                                                                                                60f &&
                                                                                                showDetails
                                                                                ) {
                                                                                        showDetails =
                                                                                                false
                                                                                }
                                                                        }
                                                                )
                                                        }
                                                }
                                ) {
                                        val wBox = constraints.maxWidth.toFloat()
                                        val hBox = constraints.maxHeight.toFloat()

                                        // Horizontal pager swipe viewer (scroll disabled in crop
                                        // mode or details mode)
                                        HorizontalPager(
                                                state = pagerState,
                                                userScrollEnabled = !isCropMode && !showDetails,
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .offset(y = animatedOffsetY)
                                                                .graphicsLayer {
                                                                        scaleX = animatedScale
                                                                        scaleY = animatedScale
                                                                }
                                        ) { page ->
                                                val file = imageFiles[page]
                                                val imageSize =
                                                        remember(file) {
                                                                try {
                                                                        val options =
                                                                                android.graphics
                                                                                        .BitmapFactory
                                                                                        .Options()
                                                                                        .apply {
                                                                                                inJustDecodeBounds =
                                                                                                        true
                                                                                        }
                                                                        android.graphics
                                                                                .BitmapFactory
                                                                                .decodeFile(
                                                                                        file.absolutePath,
                                                                                        options
                                                                                )
                                                                        Pair(
                                                                                options.outWidth,
                                                                                options.outHeight
                                                                        )
                                                                } catch (e: Exception) {
                                                                        Pair(0, 0)
                                                                }
                                                        }
                                                val autoFitScale =
                                                        remember(imageSize, rotation, wBox, hBox) {
                                                                val imgW = imageSize.first
                                                                val imgH = imageSize.second
                                                                if (imgW > 0 &&
                                                                                imgH > 0 &&
                                                                                wBox > 0f &&
                                                                                hBox > 0f &&
                                                                                rotation % 180f !=
                                                                                        0f
                                                                ) {
                                                                        val imgAspectRatio =
                                                                                imgW.toFloat() /
                                                                                        imgH.toFloat()
                                                                        val boxAspectRatio =
                                                                                wBox / hBox
                                                                        val (wFit, hFit) =
                                                                                if (imgAspectRatio >
                                                                                                boxAspectRatio
                                                                                ) {
                                                                                        Pair(
                                                                                                wBox,
                                                                                                wBox /
                                                                                                        imgAspectRatio
                                                                                        )
                                                                                } else {
                                                                                        Pair(
                                                                                                hBox *
                                                                                                        imgAspectRatio,
                                                                                                hBox
                                                                                        )
                                                                                }
                                                                        minOf(
                                                                                1f,
                                                                                minOf(
                                                                                        wBox / hFit,
                                                                                        hBox / wFit
                                                                                )
                                                                        )
                                                                } else {
                                                                        1f
                                                                }
                                                        }

                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .pointerInput(Unit) {
                                                                                detectZoomAndPan(
                                                                                        onGesture = {
                                                                                                pan,
                                                                                                zoom
                                                                                                ->
                                                                                                if (!isCropMode
                                                                                                ) {
                                                                                                        scale =
                                                                                                                (scale *
                                                                                                                                zoom)
                                                                                                                        .coerceIn(
                                                                                                                                1f,
                                                                                                                                5f
                                                                                                                        )
                                                                                                        if (scale >
                                                                                                                        1f
                                                                                                        )
                                                                                                                offset +=
                                                                                                                        pan
                                                                                                        else
                                                                                                                offset =
                                                                                                                        Offset.Zero
                                                                                                }
                                                                                        },
                                                                                        getCurrentScale = {
                                                                                                scale
                                                                                        }
                                                                                )
                                                                        }
                                                                        .pointerInput(Unit) {
                                                                                detectTapGestures(
                                                                                        onDoubleTap = {
                                                                                                if (!isCropMode
                                                                                                ) {
                                                                                                        if (scale >
                                                                                                                        1f
                                                                                                        ) {
                                                                                                                scale =
                                                                                                                        1f
                                                                                                                offset =
                                                                                                                        Offset.Zero
                                                                                                        } else {
                                                                                                                scale =
                                                                                                                        2.5f
                                                                                                        }
                                                                                                }
                                                                                        },
                                                                                        onTap = {
                                                                                                if (!isCropMode &&
                                                                                                                !showEditBar
                                                                                                ) {
                                                                                                        showControls =
                                                                                                                !showControls
                                                                                                }
                                                                                        }
                                                                                )
                                                                        },
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        AsyncImage(
                                                                model =
                                                                        coil.request.ImageRequest
                                                                                .Builder(
                                                                                        LocalContext
                                                                                                .current
                                                                                )
                                                                                .data(file)
                                                                                .size(
                                                                                        4096,
                                                                                        4096
                                                                                )
                                                                                .precision(
                                                                                        coil.size
                                                                                                .Precision
                                                                                                .INEXACT
                                                                                )
                                                                                .allowHardware(
                                                                                        true
                                                                                )
                                                                                .build(),
                                                                contentDescription = file.name,
                                                                contentScale = ContentScale.Fit,
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .graphicsLayer(
                                                                                        scaleX =
                                                                                                scale *
                                                                                                        autoFitScale,
                                                                                        scaleY =
                                                                                                scale *
                                                                                                        autoFitScale,
                                                                                        translationX =
                                                                                                offset.x,
                                                                                        translationY =
                                                                                                offset.y,
                                                                                        rotationZ =
                                                                                                rotation
                                                                                )
                                                        )
                                                }
                                        }

                                        // Crop Mode Overlay
                                        if (isCropMode) {
                                                var activeZone by remember {
                                                        mutableStateOf(0)
                                                } // 0=None, 1=TopLeft, 2=TopRight, 3=BottomLeft,
                                                // 4=BottomRight, 5=Center
                                                val density =
                                                        androidx.compose.ui.platform.LocalDensity
                                                                .current
                                                val touchThreshold = remember {
                                                        with(density) { 36.dp.toPx() }
                                                }
                                                val handleRadius = remember {
                                                        with(density) { 8.dp.toPx() }
                                                }
                                                val strokeWidth = remember {
                                                        with(density) { 2.dp.toPx() }
                                                }

                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxSize().pointerInput(
                                                                                wBox,
                                                                                hBox
                                                                        ) {
                                                                        detectDragGestures(
                                                                                onDragStart = {
                                                                                        startOffset:
                                                                                                androidx.compose.ui.geometry.Offset
                                                                                        ->
                                                                                        val pxLeft =
                                                                                                cropLeft *
                                                                                                        wBox
                                                                                        val pxTop =
                                                                                                cropTop *
                                                                                                        hBox
                                                                                        val pxRight =
                                                                                                cropRight *
                                                                                                        wBox
                                                                                        val pxBottom =
                                                                                                cropBottom *
                                                                                                        hBox

                                                                                        activeZone =
                                                                                                when {
                                                                                                        (startOffset -
                                                                                                                        Offset(
                                                                                                                                pxLeft,
                                                                                                                                pxTop
                                                                                                                        ))
                                                                                                                .getDistance() <
                                                                                                                touchThreshold ->
                                                                                                                1
                                                                                                        (startOffset -
                                                                                                                        Offset(
                                                                                                                                pxRight,
                                                                                                                                pxTop
                                                                                                                        ))
                                                                                                                .getDistance() <
                                                                                                                touchThreshold ->
                                                                                                                2
                                                                                                        (startOffset -
                                                                                                                        Offset(
                                                                                                                                pxLeft,
                                                                                                                                pxBottom
                                                                                                                        ))
                                                                                                                .getDistance() <
                                                                                                                touchThreshold ->
                                                                                                                3
                                                                                                        (startOffset -
                                                                                                                        Offset(
                                                                                                                                pxRight,
                                                                                                                                pxBottom
                                                                                                                        ))
                                                                                                                .getDistance() <
                                                                                                                touchThreshold ->
                                                                                                                4
                                                                                                        startOffset
                                                                                                                .x in
                                                                                                                pxLeft..pxRight &&
                                                                                                                startOffset
                                                                                                                        .y in
                                                                                                                        pxTop..pxBottom ->
                                                                                                                5
                                                                                                        else ->
                                                                                                                0
                                                                                                }
                                                                                },
                                                                                onDrag = {
                                                                                        change:
                                                                                                androidx.compose.ui.input.pointer.PointerInputChange,
                                                                                        dragAmount:
                                                                                                androidx.compose.ui.geometry.Offset
                                                                                        ->
                                                                                        change.consume()
                                                                                        if (wBox <=
                                                                                                        0f ||
                                                                                                        hBox <=
                                                                                                                0f
                                                                                        )
                                                                                                return@detectDragGestures
                                                                                        val dx =
                                                                                                dragAmount
                                                                                                        .x /
                                                                                                        wBox
                                                                                        val dy =
                                                                                                dragAmount
                                                                                                        .y /
                                                                                                        hBox

                                                                                        when (activeZone
                                                                                        ) {
                                                                                                1 -> { // Top-Left
                                                                                                        cropLeft =
                                                                                                                (cropLeft +
                                                                                                                                dx)
                                                                                                                        .coerceIn(
                                                                                                                                0f,
                                                                                                                                cropRight -
                                                                                                                                        0.15f
                                                                                                                        )
                                                                                                        cropTop =
                                                                                                                (cropTop +
                                                                                                                                dy)
                                                                                                                        .coerceIn(
                                                                                                                                0f,
                                                                                                                                cropBottom -
                                                                                                                                        0.15f
                                                                                                                        )
                                                                                                }
                                                                                                2 -> { // Top-Right
                                                                                                        cropRight =
                                                                                                                (cropRight +
                                                                                                                                dx)
                                                                                                                        .coerceIn(
                                                                                                                                cropLeft +
                                                                                                                                        0.15f,
                                                                                                                                1f
                                                                                                                        )
                                                                                                        cropTop =
                                                                                                                (cropTop +
                                                                                                                                dy)
                                                                                                                        .coerceIn(
                                                                                                                                0f,
                                                                                                                                cropBottom -
                                                                                                                                        0.15f
                                                                                                                        )
                                                                                                }
                                                                                                3 -> { // Bottom-Left
                                                                                                        cropLeft =
                                                                                                                (cropLeft +
                                                                                                                                dx)
                                                                                                                        .coerceIn(
                                                                                                                                0f,
                                                                                                                                cropRight -
                                                                                                                                        0.15f
                                                                                                                        )
                                                                                                        cropBottom =
                                                                                                                (cropBottom +
                                                                                                                                dy)
                                                                                                                        .coerceIn(
                                                                                                                                cropTop +
                                                                                                                                        0.15f,
                                                                                                                                1f
                                                                                                                        )
                                                                                                }
                                                                                                4 -> { // Bottom-Right
                                                                                                        cropRight =
                                                                                                                (cropRight +
                                                                                                                                dx)
                                                                                                                        .coerceIn(
                                                                                                                                cropLeft +
                                                                                                                                        0.15f,
                                                                                                                                1f
                                                                                                                        )
                                                                                                        cropBottom =
                                                                                                                (cropBottom +
                                                                                                                                dy)
                                                                                                                        .coerceIn(
                                                                                                                                cropTop +
                                                                                                                                        0.15f,
                                                                                                                                1f
                                                                                                                        )
                                                                                                }
                                                                                                5 -> { // Center Move
                                                                                                        val rectW =
                                                                                                                cropRight -
                                                                                                                        cropLeft
                                                                                                        val rectH =
                                                                                                                cropBottom -
                                                                                                                        cropTop
                                                                                                        val newLeft =
                                                                                                                (cropLeft +
                                                                                                                                dx)
                                                                                                                        .coerceIn(
                                                                                                                                0f,
                                                                                                                                1f -
                                                                                                                                        rectW
                                                                                                                        )
                                                                                                        val newTop =
                                                                                                                (cropTop +
                                                                                                                                dy)
                                                                                                                        .coerceIn(
                                                                                                                                0f,
                                                                                                                                1f -
                                                                                                                                        rectH
                                                                                                                        )
                                                                                                        cropLeft =
                                                                                                                newLeft
                                                                                                        cropRight =
                                                                                                                newLeft +
                                                                                                                        rectW
                                                                                                        cropTop =
                                                                                                                newTop
                                                                                                        cropBottom =
                                                                                                                newTop +
                                                                                                                        rectH
                                                                                                }
                                                                                        }
                                                                                },
                                                                                onDragEnd = {
                                                                                        activeZone =
                                                                                                0
                                                                                }
                                                                        )
                                                                }
                                                ) {
                                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                                                // Mask path
                                                                val maskPath =
                                                                        androidx.compose.ui.graphics
                                                                                .Path()
                                                                                .apply {
                                                                                        addRect(
                                                                                                androidx.compose
                                                                                                        .ui
                                                                                                        .geometry
                                                                                                        .Rect(
                                                                                                                0f,
                                                                                                                0f,
                                                                                                                size.width,
                                                                                                                size.height
                                                                                                        )
                                                                                        )
                                                                                }
                                                                val cropPath =
                                                                        androidx.compose.ui.graphics
                                                                                .Path()
                                                                                .apply {
                                                                                        addRoundRect(
                                                                                                androidx.compose
                                                                                                        .ui
                                                                                                        .geometry
                                                                                                        .RoundRect(
                                                                                                                left =
                                                                                                                        cropLeft *
                                                                                                                                size.width,
                                                                                                                top =
                                                                                                                        cropTop *
                                                                                                                                size.height,
                                                                                                                right =
                                                                                                                        cropRight *
                                                                                                                                size.width,
                                                                                                                bottom =
                                                                                                                        cropBottom *
                                                                                                                                size.height,
                                                                                                                cornerRadius =
                                                                                                                        androidx.compose
                                                                                                                                .ui
                                                                                                                                .geometry
                                                                                                                                .CornerRadius(
                                                                                                                                        8f,
                                                                                                                                        8f
                                                                                                                                )
                                                                                                        )
                                                                                        )
                                                                                }
                                                                val resultPath =
                                                                        androidx.compose.ui.graphics
                                                                                .Path.combine(
                                                                                androidx.compose.ui
                                                                                        .graphics
                                                                                        .PathOperation
                                                                                        .Difference,
                                                                                maskPath,
                                                                                cropPath
                                                                        )
                                                                drawPath(
                                                                        resultPath,
                                                                        color =
                                                                                Color.Black.copy(
                                                                                        alpha =
                                                                                                0.55f
                                                                                )
                                                                )

                                                                // Draw Crop Border Line
                                                                drawRoundRect(
                                                                        color = Color.White,
                                                                        topLeft =
                                                                                Offset(
                                                                                        cropLeft *
                                                                                                size.width,
                                                                                        cropTop *
                                                                                                size.height
                                                                                ),
                                                                        size =
                                                                                androidx.compose.ui
                                                                                        .geometry
                                                                                        .Size(
                                                                                                (cropRight -
                                                                                                        cropLeft) *
                                                                                                        size.width,
                                                                                                (cropBottom -
                                                                                                        cropTop) *
                                                                                                        size.height
                                                                                        ),
                                                                        cornerRadius =
                                                                                androidx.compose.ui
                                                                                        .geometry
                                                                                        .CornerRadius(
                                                                                                8f,
                                                                                                8f
                                                                                        ),
                                                                        style =
                                                                                androidx.compose.ui
                                                                                        .graphics
                                                                                        .drawscope
                                                                                        .Stroke(
                                                                                                width =
                                                                                                        strokeWidth
                                                                                        )
                                                                )

                                                                // Draw Corner Circles (Handles)
                                                                drawCircle(
                                                                        color = Color.White,
                                                                        radius = handleRadius,
                                                                        center =
                                                                                Offset(
                                                                                        cropLeft *
                                                                                                size.width,
                                                                                        cropTop *
                                                                                                size.height
                                                                                )
                                                                )
                                                                drawCircle(
                                                                        color = Color.White,
                                                                        radius = handleRadius,
                                                                        center =
                                                                                Offset(
                                                                                        cropRight *
                                                                                                size.width,
                                                                                        cropTop *
                                                                                                size.height
                                                                                )
                                                                )
                                                                drawCircle(
                                                                        color = Color.White,
                                                                        radius = handleRadius,
                                                                        center =
                                                                                Offset(
                                                                                        cropLeft *
                                                                                                size.width,
                                                                                        cropBottom *
                                                                                                size.height
                                                                                )
                                                                )
                                                                drawCircle(
                                                                        color = Color.White,
                                                                        radius = handleRadius,
                                                                        center =
                                                                                Offset(
                                                                                        cropRight *
                                                                                                size.width,
                                                                                        cropBottom *
                                                                                                size.height
                                                                                )
                                                                )
                                                        }
                                                }
                                        }

                                        // Top Bar (Samsung style: ArrowBack on left, rotate right
                                        // on right, transparent
                                        // background card)
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible =
                                                        showControls && !isCropMode && !showDetails,
                                                enter =
                                                        slideInVertically(
                                                                initialOffsetY = { -it }
                                                        ) + fadeIn(),
                                                exit =
                                                        slideOutVertically(
                                                                targetOffsetY = { -it }
                                                        ) + fadeOut(),
                                                modifier = Modifier.align(Alignment.TopCenter)
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .statusBarsPadding()
                                                                        .background(
                                                                                Brush.verticalGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        Color.Black
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.6f
                                                                                                                ),
                                                                                                        Color.Transparent
                                                                                                )
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 16.dp
                                                                        ),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        IconButton(
                                                                onClick = onDismiss,
                                                                modifier =
                                                                        Modifier.clip(CircleShape)
                                                                                .background(
                                                                                        Color(
                                                                                                0x26FFFFFF
                                                                                        )
                                                                                )
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .ArrowBack,
                                                                        contentDescription = "Back",
                                                                        tint = Color.White
                                                                )
                                                        }

                                                        Text(
                                                                text =
                                                                        "${pagerState.currentPage + 1} / ${imageFiles.size}",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 16.sp
                                                        )

                                                        IconButton(
                                                                onClick = {
                                                                        rotation =
                                                                                (rotation + 90f) %
                                                                                        360f
                                                                },
                                                                modifier =
                                                                        Modifier.clip(CircleShape)
                                                                                .background(
                                                                                        Color(
                                                                                                0x26FFFFFF
                                                                                        )
                                                                                )
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .RotateRight,
                                                                        contentDescription =
                                                                                "Rotate Right",
                                                                        tint = Color.White
                                                                )
                                                        }
                                                }
                                        }

                                        // Bottom Action Bar (Samsung style: Favorite, Edit, Share,
                                        // Delete, Send to PC)
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible =
                                                        showControls && !isCropMode && !showDetails,
                                                enter =
                                                        slideInVertically(initialOffsetY = { it }) +
                                                                fadeIn(),
                                                exit =
                                                        slideOutVertically(targetOffsetY = { it }) +
                                                                fadeOut(),
                                                modifier = Modifier.align(Alignment.BottomCenter)
                                        ) {
                                                val currentFile = imageFiles[pagerState.currentPage]
                                                val favorited = isFavorited(currentFile)

                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .background(
                                                                                Brush.verticalGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        Color.Transparent,
                                                                                                        Color.Black
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.6f
                                                                                                                )
                                                                                                )
                                                                                )
                                                                        )
                                                                        .navigationBarsPadding()
                                                                        .padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 16.dp
                                                                        )
                                                ) {
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceEvenly,
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                // Favorite
                                                                IconButton(
                                                                        onClick = {
                                                                                onToggleFavorite(
                                                                                        currentFile
                                                                                )
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        if (favorited
                                                                                        )
                                                                                                Icons.Default
                                                                                                        .Favorite
                                                                                        else
                                                                                                Icons.Default
                                                                                                        .FavoriteBorder,
                                                                                contentDescription =
                                                                                        "Favorite",
                                                                                tint =
                                                                                        if (favorited
                                                                                        )
                                                                                                Color(
                                                                                                        0xFFFF4B4B
                                                                                                )
                                                                                        else
                                                                                                Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }

                                                                // Edit toggle
                                                                IconButton(
                                                                        onClick = {
                                                                                isEditingMode = true
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Edit,
                                                                                contentDescription =
                                                                                        "Edit",
                                                                                tint = Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }

                                                                // Share
                                                                IconButton(
                                                                        onClick = {
                                                                                onShare(currentFile)
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Share,
                                                                                contentDescription =
                                                                                        "Share",
                                                                                tint = Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }

                                                                // Delete
                                                                IconButton(
                                                                        onClick = {
                                                                                onDelete(
                                                                                        currentFile
                                                                                )
                                                                                if (imageFiles
                                                                                                .size <=
                                                                                                1
                                                                                ) {
                                                                                        onDismiss()
                                                                                }
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Delete,
                                                                                contentDescription =
                                                                                        "Delete",
                                                                                tint = Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }

                                                                // Send to Desktop
                                                                IconButton(
                                                                        onClick = {
                                                                                onSendToDesktop(
                                                                                        currentFile
                                                                                )
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Computer,
                                                                                contentDescription =
                                                                                        "Send to Desktop",
                                                                                tint = Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        // Bottom Edit Toolbar (overlayed slightly above or in place
                                        // of bottom bar when
                                        // showEditBar = true)
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible =
                                                        showEditBar && !isCropMode && !showDetails,
                                                enter =
                                                        slideInVertically(initialOffsetY = { it }) +
                                                                fadeIn(),
                                                exit =
                                                        slideOutVertically(targetOffsetY = { it }) +
                                                                fadeOut(),
                                                modifier =
                                                        Modifier.align(Alignment.BottomCenter)
                                                                .padding(bottom = 72.dp)
                                        ) {
                                                Surface(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 16.dp
                                                                        ),
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = Color.Black.copy(alpha = 0.8f),
                                                        border =
                                                                BorderStroke(
                                                                        1.dp,
                                                                        Color.White.copy(
                                                                                alpha = 0.15f
                                                                        )
                                                                )
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(
                                                                                        horizontal =
                                                                                                16.dp,
                                                                                        vertical =
                                                                                                12.dp
                                                                                ),
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceEvenly,
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                // Rotate Left
                                                                Column(
                                                                        horizontalAlignment =
                                                                                Alignment
                                                                                        .CenterHorizontally
                                                                ) {
                                                                        IconButton(
                                                                                onClick = {
                                                                                        rotation -=
                                                                                                90f
                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .RotateLeft,
                                                                                        contentDescription =
                                                                                                "Rotate Left",
                                                                                        tint =
                                                                                                Color.White,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        28.dp
                                                                                                )
                                                                                )
                                                                        }
                                                                        Text(
                                                                                "Rotate Left",
                                                                                color =
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.7f
                                                                                                ),
                                                                                fontSize = 10.sp
                                                                        )
                                                                }
                                                                // Crop Toggle
                                                                Column(
                                                                        horizontalAlignment =
                                                                                Alignment
                                                                                        .CenterHorizontally
                                                                ) {
                                                                        IconButton(
                                                                                onClick = {
                                                                                        isCropMode =
                                                                                                !isCropMode
                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Crop,
                                                                                        contentDescription =
                                                                                                "Crop",
                                                                                        tint =
                                                                                                if (isCropMode
                                                                                                )
                                                                                                        FluentTheme
                                                                                                                .colors
                                                                                                                .accent
                                                                                                else
                                                                                                        Color.White,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        28.dp
                                                                                                )
                                                                                )
                                                                        }
                                                                        Text(
                                                                                if (isCropMode)
                                                                                        "No Crop"
                                                                                else "Crop Mode",
                                                                                color =
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.7f
                                                                                                ),
                                                                                fontSize = 10.sp
                                                                        )
                                                                }
                                                                // Save Copy (Includes Crop if
                                                                // Active)
                                                                Column(
                                                                        horizontalAlignment =
                                                                                Alignment
                                                                                        .CenterHorizontally
                                                                ) {
                                                                        IconButton(
                                                                                onClick = {
                                                                                        val currentFile =
                                                                                                imageFiles[
                                                                                                        pagerState
                                                                                                                .currentPage]
                                                                                        try {
                                                                                                val srcBitmap =
                                                                                                        android.graphics
                                                                                                                .BitmapFactory
                                                                                                                .decodeFile(
                                                                                                                        currentFile
                                                                                                                                .absolutePath
                                                                                                                )
                                                                                                if (srcBitmap !=
                                                                                                                null
                                                                                                ) {
                                                                                                        // Apply rotation Matrix first to align
                                                                                                        // source layout with view
                                                                                                        val matrix =
                                                                                                                android.graphics
                                                                                                                        .Matrix()
                                                                                                        matrix.postRotate(
                                                                                                                rotation
                                                                                                        )
                                                                                                        val rotated =
                                                                                                                Bitmap.createBitmap(
                                                                                                                        srcBitmap,
                                                                                                                        0,
                                                                                                                        0,
                                                                                                                        srcBitmap
                                                                                                                                .width,
                                                                                                                        srcBitmap
                                                                                                                                .height,
                                                                                                                        matrix,
                                                                                                                        true
                                                                                                                )

                                                                                                        val finalBitmap =
                                                                                                                if (isCropMode
                                                                                                                ) {
                                                                                                                        // Get rotated bitmap dimensions
                                                                                                                        val wImg =
                                                                                                                                rotated.width
                                                                                                                                        .toFloat()
                                                                                                                        val hImg =
                                                                                                                                rotated.height
                                                                                                                                        .toFloat()

                                                                                                                        // Calculate fit scaling factors
                                                                                                                        // to find correct offset
                                                                                                                        // sub-coordinates
                                                                                                                        val scaleFit =
                                                                                                                                minOf(
                                                                                                                                        wBox /
                                                                                                                                                wImg,
                                                                                                                                        hBox /
                                                                                                                                                hImg
                                                                                                                                )
                                                                                                                        val wFit =
                                                                                                                                scaleFit *
                                                                                                                                        wImg
                                                                                                                        val hFit =
                                                                                                                                scaleFit *
                                                                                                                                        hImg
                                                                                                                        val xOffset =
                                                                                                                                (wBox -
                                                                                                                                        wFit) /
                                                                                                                                        2f
                                                                                                                        val yOffset =
                                                                                                                                (hBox -
                                                                                                                                        hFit) /
                                                                                                                                        2f

                                                                                                                        // Crop selection limits in box
                                                                                                                        // screen coordinates
                                                                                                                        val pxLeft =
                                                                                                                                cropLeft *
                                                                                                                                        wBox
                                                                                                                        val pxRight =
                                                                                                                                cropRight *
                                                                                                                                        wBox
                                                                                                                        val pxTop =
                                                                                                                                cropTop *
                                                                                                                                        hBox
                                                                                                                        val pxBottom =
                                                                                                                                cropBottom *
                                                                                                                                        hBox

                                                                                                                        // Map container pixel
                                                                                                                        // coordinates to rotated bitmap
                                                                                                                        // bounds
                                                                                                                        val imgLeft =
                                                                                                                                (((pxLeft -
                                                                                                                                                        xOffset) /
                                                                                                                                                        scaleFit)
                                                                                                                                                .toInt())
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                rotated.width
                                                                                                                                        )
                                                                                                                        val imgRight =
                                                                                                                                (((pxRight -
                                                                                                                                                        xOffset) /
                                                                                                                                                        scaleFit)
                                                                                                                                                .toInt())
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                rotated.width
                                                                                                                                        )
                                                                                                                        val imgTop =
                                                                                                                                (((pxTop -
                                                                                                                                                        yOffset) /
                                                                                                                                                        scaleFit)
                                                                                                                                                .toInt())
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                rotated.height
                                                                                                                                        )
                                                                                                                        val imgBottom =
                                                                                                                                (((pxBottom -
                                                                                                                                                        yOffset) /
                                                                                                                                                        scaleFit)
                                                                                                                                                .toInt())
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                rotated.height
                                                                                                                                        )

                                                                                                                        val cropW =
                                                                                                                                (imgRight -
                                                                                                                                                imgLeft)
                                                                                                                                        .coerceAtLeast(
                                                                                                                                                10
                                                                                                                                        )
                                                                                                                        val cropH =
                                                                                                                                (imgBottom -
                                                                                                                                                imgTop)
                                                                                                                                        .coerceAtLeast(
                                                                                                                                                10
                                                                                                                                        )

                                                                                                                        Bitmap.createBitmap(
                                                                                                                                rotated,
                                                                                                                                imgLeft,
                                                                                                                                imgTop,
                                                                                                                                cropW,
                                                                                                                                cropH
                                                                                                                        )
                                                                                                                } else {
                                                                                                                        rotated
                                                                                                                }

                                                                                                        val ext =
                                                                                                                currentFile
                                                                                                                        .extension
                                                                                                        val baseName =
                                                                                                                currentFile
                                                                                                                        .nameWithoutExtension
                                                                                                        val dest =
                                                                                                                File(
                                                                                                                        currentFile
                                                                                                                                .parentFile,
                                                                                                                        "${baseName}_edited.${ext}"
                                                                                                                )
                                                                                                        val format =
                                                                                                                when (ext.lowercase()
                                                                                                                ) {
                                                                                                                        "png" ->
                                                                                                                                Bitmap.CompressFormat
                                                                                                                                        .PNG
                                                                                                                        "webp" ->
                                                                                                                                Bitmap.CompressFormat
                                                                                                                                        .WEBP
                                                                                                                        else ->
                                                                                                                                Bitmap.CompressFormat
                                                                                                                                        .JPEG
                                                                                                                }
                                                                                                        FileOutputStream(
                                                                                                                        dest
                                                                                                                )
                                                                                                                .use {
                                                                                                                        out
                                                                                                                        ->
                                                                                                                        finalBitmap
                                                                                                                                .compress(
                                                                                                                                        format,
                                                                                                                                        95,
                                                                                                                                        out
                                                                                                                                )
                                                                                                                }

                                                                                                        if (srcBitmap !=
                                                                                                                        rotated
                                                                                                        )
                                                                                                                srcBitmap
                                                                                                                        .recycle()
                                                                                                        if (rotated !=
                                                                                                                        finalBitmap
                                                                                                        )
                                                                                                                rotated.recycle()
                                                                                                        finalBitmap
                                                                                                                .recycle()

                                                                                                        Toast.makeText(
                                                                                                                        context,
                                                                                                                        "Saved: ${dest.name}",
                                                                                                                        Toast.LENGTH_SHORT
                                                                                                                )
                                                                                                                .show()
                                                                                                        isCropMode =
                                                                                                                false
                                                                                                        showEditBar =
                                                                                                                false
                                                                                                } else {
                                                                                                        Toast.makeText(
                                                                                                                        context,
                                                                                                                        "Failed to decode image",
                                                                                                                        Toast.LENGTH_SHORT
                                                                                                                )
                                                                                                                .show()
                                                                                                }
                                                                                        } catch (
                                                                                                e:
                                                                                                        Exception) {
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "Save failed: ${e.localizedMessage}",
                                                                                                                Toast.LENGTH_SHORT
                                                                                                        )
                                                                                                        .show()
                                                                                        }
                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Save,
                                                                                        contentDescription =
                                                                                                "Save",
                                                                                        tint =
                                                                                                Color.White,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        28.dp
                                                                                                )
                                                                                )
                                                                        }
                                                                        Text(
                                                                                "Save",
                                                                                color =
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.7f
                                                                                                ),
                                                                                fontSize = 10.sp
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        // Samsung-style Bottom Details Card
                                        androidx.compose.animation.AnimatedVisibility(
                                                visible = showDetails,
                                                enter =
                                                        slideInVertically(initialOffsetY = { it }) +
                                                                fadeIn(),
                                                exit =
                                                        slideOutVertically(targetOffsetY = { it }) +
                                                                fadeOut(),
                                                modifier = Modifier.align(Alignment.BottomCenter)
                                        ) {
                                                Surface(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .navigationBarsPadding(),
                                                        color = Color.Black,
                                                        shape =
                                                                RoundedCornerShape(
                                                                        topStart = 24.dp,
                                                                        topEnd = 24.dp
                                                                )
                                                ) {
                                                        val activeDetails =
                                                                mediaDetails
                                                                        ?: MediaDetails(
                                                                                name =
                                                                                        currentFile
                                                                                                .name,
                                                                                path =
                                                                                        currentFile
                                                                                                .parentFile
                                                                                                ?.absolutePath
                                                                                                ?: "",
                                                                                folder =
                                                                                        currentFile
                                                                                                .parentFile
                                                                                                ?.name
                                                                                                ?: "",
                                                                                dateStr =
                                                                                        "Loading details...",
                                                                                sizeStr = "",
                                                                                resolutionStr = "",
                                                                                extraStr = ""
                                                                        )
                                                        Column(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(
                                                                                        horizontal =
                                                                                                24.dp,
                                                                                        vertical =
                                                                                                20.dp
                                                                                ),
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(12.dp)
                                                        ) {
                                                                // Drag Handle indicator
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(
                                                                                                40.dp,
                                                                                                4.dp
                                                                                        )
                                                                                        .clip(
                                                                                                CircleShape
                                                                                        )
                                                                                        .background(
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.2f
                                                                                                        )
                                                                                        )
                                                                                        .align(
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                        )
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )

                                                                // Date
                                                                if (activeDetails.dateStr
                                                                                .isNotEmpty()
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        activeDetails
                                                                                                .dateStr,
                                                                                color = Color.White,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 18.sp
                                                                        )
                                                                }

                                                                // Filename
                                                                Text(
                                                                        text = activeDetails.name,
                                                                        color = Color.White,
                                                                        fontSize = 14.sp,
                                                                        fontWeight =
                                                                                FontWeight.Normal
                                                                )

                                                                // File Path
                                                                Text(
                                                                        text = activeDetails.path,
                                                                        color =
                                                                                Color.White.copy(
                                                                                        alpha = 0.5f
                                                                                ),
                                                                        fontSize = 12.sp,
                                                                        fontWeight =
                                                                                FontWeight.Normal
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )

                                                                // Folder Name Header
                                                                Text(
                                                                        text = activeDetails.folder,
                                                                        color =
                                                                                Color.White.copy(
                                                                                        alpha = 0.7f
                                                                                ),
                                                                        fontSize = 14.sp,
                                                                        fontWeight =
                                                                                FontWeight.SemiBold
                                                                )

                                                                // Specs Details: Size | Resolution
                                                                // | Megapixels or Duration
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                12.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        activeDetails
                                                                                                .sizeStr,
                                                                                color =
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.5f
                                                                                                ),
                                                                                fontSize = 13.sp
                                                                        )
                                                                        if (activeDetails
                                                                                        .resolutionStr
                                                                                        .isNotEmpty()
                                                                        ) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                1.dp,
                                                                                                                12.dp
                                                                                                        )
                                                                                                        .background(
                                                                                                                Color.White
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.2f
                                                                                                                        )
                                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                activeDetails
                                                                                                        .resolutionStr,
                                                                                        color =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.5f
                                                                                                        ),
                                                                                        fontSize =
                                                                                                13.sp
                                                                                )
                                                                        }
                                                                        if (activeDetails.extraStr
                                                                                        .isNotEmpty()
                                                                        ) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                1.dp,
                                                                                                                12.dp
                                                                                                        )
                                                                                                        .background(
                                                                                                                Color.White
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.2f
                                                                                                                        )
                                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                activeDetails
                                                                                                        .extraStr,
                                                                                        color =
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.5f
                                                                                                        ),
                                                                                        fontSize =
                                                                                                13.sp
                                                                                )
                                                                        }
                                                                }

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
        }
}

// ---------------- BASIC TEXTFIELD MINIMAL FOR SEARCH ----------------
@Composable
private fun ExplorerBasicTextField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier = Modifier
) {
        androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle =
                        androidx.compose.ui.text.TextStyle(
                                color = FluentTheme.colors.textColor,
                                fontSize = 14.sp
                        ),
                singleLine = true,
                cursorBrush =
                        Brush.verticalGradient(
                                listOf(FluentTheme.colors.accent, FluentTheme.colors.accent)
                        ),
                modifier = modifier,
                decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                                if (value.isEmpty()) {
                                        Text(
                                                text = placeholder,
                                                color = FluentTheme.colors.textMuted,
                                                fontSize = 14.sp
                                        )
                                }
                                innerTextField()
                        }
                }
        )
}

// ---------------- HELPERS ----------------
private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
                Locale.US,
                "%.1f %s",
                bytes / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
        )
}

private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun buildImageAlbums(files: List<File>, showHiddenFiles: Boolean): List<ImageAlbum> =
        files.filter { file ->
                        file.isFile && (showHiddenFiles || !file.name.startsWith("."))
                }
                .groupBy { it.parentFile?.absoluteFile ?: File("") }
                .filterKeys { it.path.isNotEmpty() }
                .mapNotNull { (directory, albumFiles) ->
                        val cover =
                                albumFiles.maxByOrNull { it.lastModified() }
                                        ?: return@mapNotNull null
                        ImageAlbum(
                                name = directory.name.ifBlank { directory.absolutePath },
                                directory = directory,
                                coverFile = cover,
                                count = albumFiles.size,
                                lastModified = cover.lastModified()
                        )
                }

private fun sortImageAlbums(
        albums: List<ImageAlbum>,
        sortType: FileSortType,
        ascending: Boolean
): List<ImageAlbum> {
        val comparator =
                when (sortType) {
                        FileSortType.NAME -> compareBy<ImageAlbum> { it.name.lowercase() }
                        FileSortType.DATE -> compareBy { it.lastModified }
                        FileSortType.SIZE -> compareBy { it.count }
                        FileSortType.TYPE -> compareBy { it.directory.absolutePath.lowercase() }
                }
        val sorted = albums.sortedWith(comparator)
        return if (ascending) sorted else sorted.reversed()
}

private fun getFileIcon(file: File): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
        if (file.isDirectory) {
                return Pair(Icons.Default.Folder, Color(0xFF0078D4))
        }
        return when (file.extension.lowercase()) {
                "pdf" -> Pair(Icons.Default.Description, Color(0xFFC42B1C))
                "png", "jpg", "jpeg", "webp", "gif", "bmp" ->
                        Pair(Icons.Default.Image, Color(0xFF107C41))
                "mp4", "mkv", "webm", "avi", "3gp", "mov" ->
                        Pair(Icons.Default.VideoFile, Color(0xFF8660A9))
                "mp3", "wav", "m4a", "flac", "ogg", "aac" ->
                        Pair(Icons.Default.MusicNote, Color(0xFF0078D4))
                "apk" -> Pair(Icons.Default.Android, Color(0xFF32D74B))
                "zip", "rar", "7z", "tar", "gz" -> Pair(Icons.Default.Archive, Color(0xFFD83B01))
                "txt", "md", "log", "json", "xml", "toml" ->
                        Pair(Icons.Default.Article, Color(0xFF737373))
                else -> Pair(Icons.Default.InsertDriveFile, Color(0xFF737373))
        }
}

private fun externalMimeTypeForFile(file: File): String {
        val extension = file.extension.lowercase()
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (!mapped.isNullOrBlank()) return mapped

        return when (extension) {
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "odt" -> "application/vnd.oasis.opendocument.text"
                "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
                "odp" -> "application/vnd.oasis.opendocument.presentation"
                "rtf" -> "application/rtf"
                "csv" -> "text/csv"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "md", "log", "toml", "yaml", "yml", "ini", "cfg", "conf",
                "rs", "py", "java", "js", "ts", "tsx", "jsx", "kt", "cpp", "c", "h",
                "html", "css", "go", "rb", "sh", "bat", "ps1" -> "text/plain"
                else -> "*/*"
        }
}

private fun launchExternalViewer(context: Context, file: File, mimeType: String) {
        val uri =
                FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                )
        val intent =
                Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

        try {
                context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (firstError: ActivityNotFoundException) {
                if (mimeType == "*/*") throw firstError
                val fallback =
                        Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "*/*")
                                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                context.startActivity(Intent.createChooser(fallback, "Open with"))
        }
}

private fun openLocalFile(
        file: File,
        context: Context,
        onPdfPreview: (File) -> Unit,
        onVideoPlay: (File) -> Unit,
        onImagePreview: () -> Unit,
        onApkView: (File) -> Unit
) {
        when (file.extension.lowercase()) {
                "pdf" -> onPdfPreview(file)
                "mp4", "mkv", "webm", "avi", "3gp", "mov" -> onVideoPlay(file)
                "png", "jpg", "jpeg", "webp", "gif", "bmp" -> onImagePreview()
                "apk" -> onApkView(file)
                "mp3", "wav", "m4a", "flac", "ogg", "aac" -> {
                        // Find all music files in parent folder to create a queue
                        val musicFiles =
                                file.parentFile
                                        ?.listFiles()
                                        ?.filter {
                                                it.isFile &&
                                                        it.extension.lowercase() in
                                                                listOf(
                                                                        "mp3",
                                                                        "wav",
                                                                        "m4a",
                                                                        "flac",
                                                                        "ogg",
                                                                        "aac"
                                                                )
                                        }
                                        ?.toList()
                                        ?: emptyList()
                        LocalMusicPlayerManager.play(file, musicFiles)
                }
                else -> {
                        try {
                                launchExternalViewer(context, file, externalMimeTypeForFile(file))
                        } catch (e: Exception) {
                                Toast.makeText(
                                                context,
                                                "No app available to open this file: ${e.localizedMessage}",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                        }
                }
        }
}

private fun installApk(context: Context, file: File) {
        try {
                val uri =
                        FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                        )
                val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                context.startActivity(intent)
        } catch (e: Exception) {
                Toast.makeText(
                                context,
                                "Installation failed: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                        )
                        .show()
        }
}

suspend fun PointerInputScope.detectZoomAndPan(
        onGesture: (pan: Offset, zoom: Float) -> Unit,
        getCurrentScale: () -> Float
) {
        awaitEachGesture {
                var pastTouchSlop = false
                val touchSlop = viewConfiguration.touchSlop
                var pan = Offset.Zero
                var zoom = 1f

                awaitFirstDown(requireUnconsumed = false)
                do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                        zoom *= zoomChange
                                        pan += panChange

                                        val centroidSize =
                                                event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = Math.abs(1 - zoom) * centroidSize
                                        val panMotion = pan.getDistance()

                                        if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                pastTouchSlop = true
                                        }
                                }

                                if (pastTouchSlop) {
                                        val currentScale = getCurrentScale()
                                        val isZooming = zoomChange != 1f
                                        val shouldConsume = currentScale > 1f || isZooming

                                        if (shouldConsume) {
                                                onGesture(panChange, zoomChange)
                                                event.changes.forEach {
                                                        if (it.positionChange() != Offset.Zero) {
                                                                it.consume()
                                                        }
                                                }
                                        }
                                }
                        }
                } while (!canceled && event.changes.any { it.pressed })
        }
}

@Composable
fun FastScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
        val totalItems = listState.layoutInfo.totalItemsCount
        val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
        val visibleItems = visibleItemsInfo.size

        if (totalItems <= visibleItems || totalItems == 0) return

        val coroutineScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }
        var isScrollActive by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf<Float?>(null) }

        LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                isScrollActive = true
                kotlinx.coroutines.delay(1500)
                isScrollActive = false
        }

        val showScrollbar = isScrollActive || isDragging || listState.isScrollInProgress
        val alpha by
                animateFloatAsState(
                        targetValue = if (showScrollbar) 1f else 0f,
                        animationSpec = tween(durationMillis = if (showScrollbar) 150 else 500),
                        label = "ScrollbarAlpha"
                )

        if (alpha <= 0.01f) return

        BoxWithConstraints(
                modifier = modifier.fillMaxHeight().width(50.dp).graphicsLayer(alpha = alpha)
        ) {
                val containerHeight = constraints.maxHeight.toFloat()
                val handleHeight = with(LocalDensity.current) { 64.dp.toPx() }

                val firstVisibleIndex = listState.firstVisibleItemIndex
                val firstVisibleOffset = listState.firstVisibleItemScrollOffset

                val maxScrollIndex = (totalItems - visibleItems).coerceAtLeast(0)

                val progress =
                        remember(firstVisibleIndex, firstVisibleOffset, maxScrollIndex) {
                                if (maxScrollIndex > 0) {
                                        val firstItemSize =
                                                visibleItemsInfo.firstOrNull()?.size ?: 1
                                        val offsetFraction =
                                                firstVisibleOffset.toFloat() /
                                                        firstItemSize.toFloat().coerceAtLeast(1f)
                                        val smoothIndex =
                                                firstVisibleIndex.toFloat() +
                                                        offsetFraction.coerceIn(0f, 1f)
                                        (smoothIndex / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
                                } else {
                                        0f
                                }
                        }

                val yOffset = dragOffset ?: (progress * (containerHeight - handleHeight))

                val currentContainerHeight by rememberUpdatedState(containerHeight)
                val currentHandleHeight by rememberUpdatedState(handleHeight)
                val currentMaxScrollIndex by rememberUpdatedState(maxScrollIndex)
                val currentProgress by rememberUpdatedState(progress)

                Box(
                        modifier =
                                Modifier.offset(y = with(LocalDensity.current) { yOffset.toDp() })
                                        .align(Alignment.TopEnd)
                                        .padding(end = 6.dp)
                                        .width(28.dp)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .pointerInput(listState) {
                                                detectDragGestures(
                                                        onDragStart = {
                                                                isDragging = true
                                                                dragOffset =
                                                                        currentProgress *
                                                                                (currentContainerHeight -
                                                                                        currentHandleHeight)
                                                        },
                                                        onDragEnd = {
                                                                isDragging = false
                                                                dragOffset = null
                                                        },
                                                        onDragCancel = {
                                                                isDragging = false
                                                                dragOffset = null
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val currentOffset =
                                                                        dragOffset
                                                                                ?: (currentProgress *
                                                                                        (currentContainerHeight -
                                                                                                currentHandleHeight))
                                                                val newOffset =
                                                                        (currentOffset +
                                                                                        dragAmount
                                                                                                .y)
                                                                                .coerceIn(
                                                                                        0f,
                                                                                        currentContainerHeight -
                                                                                                currentHandleHeight
                                                                                )
                                                                dragOffset = newOffset

                                                                val newProgress =
                                                                        newOffset /
                                                                                (currentContainerHeight -
                                                                                        currentHandleHeight)
                                                                val targetIndex =
                                                                        (newProgress *
                                                                                        currentMaxScrollIndex)
                                                                                .toInt()
                                                                                .coerceIn(
                                                                                        0,
                                                                                        currentMaxScrollIndex
                                                                                )
                                                                coroutineScope.launch {
                                                                        listState.scrollToItem(
                                                                                targetIndex
                                                                        )
                                                                }
                                                        }
                                                )
                                        },
                        contentAlignment = Alignment.Center
                ) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                )
                        }
                }
        }
}

@Composable
fun FastScrollbarGrid(gridState: LazyGridState, modifier: Modifier = Modifier) {
        val totalItems = gridState.layoutInfo.totalItemsCount
        val visibleItemsInfo = gridState.layoutInfo.visibleItemsInfo
        val visibleItems = visibleItemsInfo.size

        if (totalItems <= visibleItems || totalItems == 0) return

        val coroutineScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }
        var isScrollActive by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf<Float?>(null) }

        LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
                isScrollActive = true
                kotlinx.coroutines.delay(1500)
                isScrollActive = false
        }

        val showScrollbar = isScrollActive || isDragging || gridState.isScrollInProgress
        val alpha by
                animateFloatAsState(
                        targetValue = if (showScrollbar) 1f else 0f,
                        animationSpec = tween(durationMillis = if (showScrollbar) 150 else 500),
                        label = "ScrollbarAlphaGrid"
                )

        if (alpha <= 0.01f) return

        BoxWithConstraints(
                modifier = modifier.fillMaxHeight().width(50.dp).graphicsLayer(alpha = alpha)
        ) {
                val containerHeight = constraints.maxHeight.toFloat()
                val handleHeight = with(LocalDensity.current) { 64.dp.toPx() }

                val firstVisibleIndex = gridState.firstVisibleItemIndex
                val firstVisibleOffset = gridState.firstVisibleItemScrollOffset

                val maxScrollIndex = (totalItems - visibleItems).coerceAtLeast(0)

                val progress =
                        remember(firstVisibleIndex, firstVisibleOffset, maxScrollIndex) {
                                if (maxScrollIndex > 0) {
                                        val firstItemSize =
                                                visibleItemsInfo.firstOrNull()?.size?.height ?: 1
                                        val offsetFraction =
                                                firstVisibleOffset.toFloat() /
                                                        firstItemSize.toFloat().coerceAtLeast(1f)
                                        val smoothIndex =
                                                firstVisibleIndex.toFloat() +
                                                        offsetFraction.coerceIn(0f, 1f)
                                        (smoothIndex / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
                                } else {
                                        0f
                                }
                        }

                val yOffset = dragOffset ?: (progress * (containerHeight - handleHeight))

                val currentContainerHeight by rememberUpdatedState(containerHeight)
                val currentHandleHeight by rememberUpdatedState(handleHeight)
                val currentMaxScrollIndex by rememberUpdatedState(maxScrollIndex)
                val currentProgress by rememberUpdatedState(progress)

                Box(
                        modifier =
                                Modifier.offset(y = with(LocalDensity.current) { yOffset.toDp() })
                                        .align(Alignment.TopEnd)
                                        .padding(end = 6.dp)
                                        .width(28.dp)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .pointerInput(gridState) {
                                                detectDragGestures(
                                                        onDragStart = {
                                                                isDragging = true
                                                                dragOffset =
                                                                        currentProgress *
                                                                                (currentContainerHeight -
                                                                                        currentHandleHeight)
                                                        },
                                                        onDragEnd = {
                                                                isDragging = false
                                                                dragOffset = null
                                                        },
                                                        onDragCancel = {
                                                                isDragging = false
                                                                dragOffset = null
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val currentOffset =
                                                                        dragOffset
                                                                                ?: (currentProgress *
                                                                                        (currentContainerHeight -
                                                                                                currentHandleHeight))
                                                                val newOffset =
                                                                        (currentOffset +
                                                                                        dragAmount
                                                                                                .y)
                                                                                .coerceIn(
                                                                                        0f,
                                                                                        currentContainerHeight -
                                                                                                currentHandleHeight
                                                                                )
                                                                dragOffset = newOffset

                                                                val newProgress =
                                                                        newOffset /
                                                                                (currentContainerHeight -
                                                                                        currentHandleHeight)
                                                                val targetIndex =
                                                                        (newProgress *
                                                                                        currentMaxScrollIndex)
                                                                                .toInt()
                                                                                .coerceIn(
                                                                                        0,
                                                                                        currentMaxScrollIndex
                                                                                )
                                                                coroutineScope.launch {
                                                                        gridState.scrollToItem(
                                                                                targetIndex
                                                                        )
                                                                }
                                                        }
                                                )
                                        },
                        contentAlignment = Alignment.Center
                ) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                )
                        }
                }
        }
}

// PathBreadcrumbs Component
@Composable
fun PathBreadcrumbs(
        currentDirectory: File,
        onNavigateToDir: (File) -> Unit,
        onNavigateHome: () -> Unit,
        modifier: Modifier = Modifier
) {
        val context = LocalContext.current
        val primaryStoragePath = remember { Environment.getExternalStorageDirectory().absolutePath }

        val segments =
                remember(currentDirectory) {
                        val list = mutableListOf<Pair<String, File>>()
                        var curr: File? = currentDirectory
                        while (curr != null) {
                                val path = curr.absolutePath
                                if (path == primaryStoragePath) {
                                        list.add(0, Pair("Internal Storage", curr))
                                        break
                                } else if (path == "/") {
                                        list.add(0, Pair("Root", curr))
                                        break
                                }

                                // Check SD card matches
                                val dirs = ContextCompat.getExternalFilesDirs(context, null)
                                var isSdRoot = false
                                for (i in 1 until dirs.size) {
                                        val dir = dirs[i] ?: continue
                                        val sdPath =
                                                if (dir.absolutePath.contains("/Android/data")) {
                                                        dir.absolutePath.substringBefore(
                                                                "/Android/data"
                                                        )
                                                } else {
                                                        dir.absolutePath
                                                }
                                        if (path == sdPath) {
                                                list.add(0, Pair("SD Card", curr))
                                                isSdRoot = true
                                                break
                                        }
                                }
                                if (isSdRoot) break

                                list.add(0, Pair(curr.name, curr))
                                curr = curr.parentFile
                        }
                        list
                }

        Row(
                modifier =
                        modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = FluentTheme.colors.accent,
                        modifier = Modifier.size(20.dp).clickable { onNavigateHome() }
                )

                segments.forEachIndexed { index, segment ->
                        Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = FluentTheme.colors.textMuted,
                                modifier = Modifier.size(16.dp).padding(horizontal = 4.dp)
                        )
                        val isLast = index == segments.size - 1
                        Text(
                                text = segment.first,
                                fontSize = 13.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color =
                                        if (isLast) FluentTheme.colors.textColor
                                        else FluentTheme.colors.textMuted,
                                modifier =
                                        Modifier.clickable(!isLast) {
                                                onNavigateToDir(segment.second)
                                        }
                        )
                }
        }
}
