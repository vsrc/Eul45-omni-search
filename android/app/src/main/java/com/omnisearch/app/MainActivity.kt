package com.omnisearch.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.omnisearch.app.ui.LocalMusicPlaybackService
import com.omnisearch.app.ui.MainScreen
import com.omnisearch.app.ui.SecurityViewModel
import com.omnisearch.app.ui.SyncViewModel
import com.omnisearch.app.ui.theme.OmniSearchTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private val openLocalMusicRequest = mutableStateOf(false)
    private val autoPlayFilePath = mutableStateOf<String?>(null)
    private val openScannerRequest = mutableStateOf(false)
    private val openLocalExplorerRequest = mutableStateOf(false)
    private val explorerCategory = mutableStateOf<String?>(null)
    
    private val openOmniSearchRequest = mutableStateOf(false)
    private val openLocalSearchRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openMusic = shouldOpenLocalMusic(intent)
        openLocalMusicRequest.value = openMusic
        if (openMusic) {
            explorerCategory.value = "music"
            autoPlayFilePath.value = intent?.getStringExtra("AUTO_PLAY_FILE_PATH")
        }
        openScannerRequest.value = intent?.action == "com.omnisearch.app.ACTION_OPEN_SCANNER"
        openLocalExplorerRequest.value =
                intent?.action == "com.omnisearch.app.ACTION_OPEN_LOCAL_EXPLORER"
        if (intent?.getStringExtra("EXPLORER_CATEGORY") != null) {
            explorerCategory.value = intent.getStringExtra("EXPLORER_CATEGORY")
        }
        
        if (intent?.action == "com.omnisearch.app.ACTION_OPEN_OMNI_SEARCH") {
            val target = intent.getStringExtra("TARGET")
            openOmniSearchRequest.value = target == "PC"
            openLocalSearchRequest.value = target == "LOCAL"
            if (target == "LOCAL") {
                explorerCategory.value = "internal"
            }
        } else {
            openOmniSearchRequest.value = intent?.getBooleanExtra("EXTRA_OPEN_OMNI_SEARCH", false) ?: false
            openLocalSearchRequest.value = intent?.getBooleanExtra("EXTRA_SEARCH_MODE_LOCAL", false) ?: false
        }

        // Initialize global Coil ImageLoader with VideoFrameDecoder for video thumbnail generation
        val imageLoader =
                ImageLoader.Builder(applicationContext)
                        .components { add(VideoFrameDecoder.Factory()) }
                        .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge(
                statusBarStyle =
                        SystemBarStyle.auto(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                        ),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val syncViewModel = ViewModelProvider(this)[SyncViewModel::class.java]
        val securityViewModel = ViewModelProvider(this)[SecurityViewModel::class.java]

        setContent {
            val themeMode by syncViewModel.themeMode.collectAsState()
            val themeColor by syncViewModel.themeColor.collectAsState()

            androidx.compose.runtime.LaunchedEffect(themeMode) {
                com.omnisearch.app.widget.WidgetThemeUtil.saveThemeMode(this@MainActivity, themeMode)
            }

            OmniSearchTheme(themeMode = themeMode, themeColor = themeColor) {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = com.omnisearch.app.ui.theme.FluentTheme.colors.pageBg
                ) {
                    MainScreen(
                            syncViewModel = syncViewModel,
                            securityViewModel = securityViewModel,
                            openLocalMusicRequest = openLocalMusicRequest.value,
                            autoPlayFilePath = autoPlayFilePath.value,
                            onOpenLocalMusicHandled = { 
                                openLocalMusicRequest.value = false 
                                autoPlayFilePath.value = null
                            },
                            openScannerRequest = openScannerRequest.value,
                            onOpenScannerHandled = { openScannerRequest.value = false },
                            openLocalExplorerRequest = openLocalExplorerRequest.value,
                            initialExplorerCategory = explorerCategory.value,
                            onOpenLocalExplorerHandled = { openLocalExplorerRequest.value = false },
                            onInitialCategoryHandled = { explorerCategory.value = null },
                            openOmniSearchRequest = openOmniSearchRequest.value,
                            onOpenOmniSearchHandled = { openOmniSearchRequest.value = false },
                            openLocalSearchRequest = openLocalSearchRequest.value,
                            onOpenLocalSearchHandled = { openLocalSearchRequest.value = false }
                    )
                }
            }
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldOpenLocalMusic(intent)) {
            openLocalMusicRequest.value = true
            explorerCategory.value = "music"
            autoPlayFilePath.value = intent.getStringExtra("AUTO_PLAY_FILE_PATH")
        }
        if (intent.action == "com.omnisearch.app.ACTION_OPEN_SCANNER") {
            openScannerRequest.value = true
        }
        if (intent.action == "com.omnisearch.app.ACTION_OPEN_LOCAL_EXPLORER") {
            openLocalExplorerRequest.value = true
            explorerCategory.value = intent.getStringExtra("EXPLORER_CATEGORY")
        }
        if (intent.action == "com.omnisearch.app.ACTION_OPEN_OMNI_SEARCH") {
            val target = intent.getStringExtra("TARGET")
            if (target == "PC") openOmniSearchRequest.value = true
            if (target == "LOCAL") {
                openLocalSearchRequest.value = true
                explorerCategory.value = "internal"
            }
        } else {
            if (intent.getBooleanExtra("EXTRA_OPEN_OMNI_SEARCH", false)) {
                openOmniSearchRequest.value = true
            }
            if (intent.getBooleanExtra("EXTRA_SEARCH_MODE_LOCAL", false)) {
                openLocalSearchRequest.value = true
            }
        }
        handleShareIntent(intent)
    }

    private fun shouldOpenLocalMusic(intent: Intent?): Boolean =
            intent?.action == LocalMusicPlaybackService.ACTION_OPEN_LOCAL_MUSIC ||
                    intent?.getBooleanExtra(
                            LocalMusicPlaybackService.EXTRA_OPEN_LOCAL_MUSIC,
                            false
                    ) == true

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            val uri = intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
            if (uri != null) {
                processSharedUris(listOf(uri))
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (uris != null) {
                processSharedUris(uris)
            }
        }
    }

    private fun processSharedUris(uris: List<Uri>) {
        val syncViewModel = ViewModelProvider(this)[SyncViewModel::class.java]

        if (syncViewModel.status.value != com.omnisearch.app.ui.ConnectionStatus.CONNECTED) {
            Toast.makeText(this, "Connect OmniSearch to desktop app first", Toast.LENGTH_LONG)
                    .show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val tempFiles = mutableListOf<File>()
            for (uri in uris) {
                val file = copyUriToTempFile(this@MainActivity, uri)
                if (file != null) {
                    tempFiles.add(file)
                }
            }
            if (tempFiles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    syncViewModel.sendFilesToDesktop(this@MainActivity, tempFiles)
                    Toast.makeText(
                                    this@MainActivity,
                                    "Sending ${tempFiles.size} file(s) to Desktop...",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }
        }
    }

    private fun copyUriToTempFile(context: android.content.Context, uri: Uri): File? {
        try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            val name =
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index != -1) it.getString(index) else null
                        } else null
                    }
                            ?: "shared_file_${System.currentTimeMillis()}"

            val cleanName = File(name).name
            val tempFile = File(context.cacheDir, cleanName)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
