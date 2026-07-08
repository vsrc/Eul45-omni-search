package com.omnisearch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import com.omnisearch.app.ui.theme.FluentTheme

private fun formatTransferBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "${bytes} B" else String.format("%.1f %s", value, units[unitIndex])
}

@Composable
fun MainScreen(
    syncViewModel: SyncViewModel,
    securityViewModel: SecurityViewModel,
    openLocalMusicRequest: Boolean = false,
    onOpenLocalMusicHandled: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showLocalExplorer by rememberSaveable { mutableStateOf(false) }
    var openMusicPlayerRequest by rememberSaveable { mutableIntStateOf(0) }
    val isLocked by securityViewModel.isLocked.collectAsState()
    val syncStatus by syncViewModel.status.collectAsState()
    val incomingTransfer by syncViewModel.incomingTransfer.collectAsState()
    val outgoingTransfer by syncViewModel.outgoingTransfer.collectAsState()

    var showThemesScreen by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(incomingTransfer?.id, incomingTransfer?.status) {
        val status = incomingTransfer?.status ?: return@LaunchedEffect
        if (status == "Downloaded" || status == "Failed") {
            kotlinx.coroutines.delay(1800)
            syncViewModel.dismissIncomingTransfer()
        }
    }
    LaunchedEffect(outgoingTransfer?.name, outgoingTransfer?.status) {
        val status = outgoingTransfer?.status ?: return@LaunchedEffect
        if (status == "Completed" || status == "Failed") {
            kotlinx.coroutines.delay(2500)
            syncViewModel.dismissOutgoingTransfer()
        }
    }

    var hasEnteredBackground by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openLocalMusicRequest) {
        if (openLocalMusicRequest) {
            showThemesScreen = false
            selectedTab = 0
            showLocalExplorer = true
            openMusicPlayerRequest++
            onOpenLocalMusicHandled()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    hasEnteredBackground = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (hasEnteredBackground) {
                        securityViewModel.triggerLockOnResume()
                        hasEnteredBackground = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = showThemesScreen && !isLocked) {
        showThemesScreen = false
    }

    Box(modifier = Modifier.fillMaxSize().background(FluentTheme.colors.pageBg)) {
        if (showThemesScreen && !isLocked) {
            ThemesScreen(
                syncViewModel = syncViewModel,
                onBack = { showThemesScreen = false }
            )
        } else if (showLocalExplorer && !isLocked) {
            LocalExplorerScreen(
                onBackToPC = { showLocalExplorer = false },
                onThemesClick = { showThemesScreen = true },
                syncViewModel = syncViewModel,
                openMusicPlayerRequest = openMusicPlayerRequest
            )
        } else {
            Scaffold(
                containerColor = FluentTheme.colors.pageBg,
                bottomBar = {
                    if (!isLocked) {
                        NavigationBar(
                            containerColor = FluentTheme.colors.panelBg,
                            contentColor = FluentTheme.colors.textColor
                        ) {
                            // Search Tab
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search panel"
                                    )
                                },
                                label = { Text("Search") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FluentTheme.colors.accent,
                                    selectedTextColor = FluentTheme.colors.accent,
                                    unselectedIconColor = FluentTheme.colors.textMuted,
                                    unselectedTextColor = FluentTheme.colors.textMuted,
                                    indicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("tab_search")
                            )

                            // Pinned Tab
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Pinned bookmarks hub"
                                    )
                                },
                                label = { Text("Pinned Hub") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FluentTheme.colors.accent,
                                    selectedTextColor = FluentTheme.colors.accent,
                                    unselectedIconColor = FluentTheme.colors.textMuted,
                                    unselectedTextColor = FluentTheme.colors.textMuted,
                                    indicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("tab_pinned")
                            )

                            // Settings Tab
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Box(modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings config panel",
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                        if (syncStatus == ConnectionStatus.CONNECTED) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(FluentTheme.colors.connectedText)
                                                    .align(Alignment.TopEnd)
                                            )
                                        }
                                    }
                                },
                                label = { Text("Settings") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FluentTheme.colors.accent,
                                    selectedTextColor = FluentTheme.colors.accent,
                                    unselectedIconColor = FluentTheme.colors.textMuted,
                                    unselectedTextColor = FluentTheme.colors.textMuted,
                                    indicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("tab_settings")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (!isLocked) {
                        when (selectedTab) {
                            0 -> SearchScreen(
                                syncViewModel = syncViewModel,
                                onOpenExplorer = { showLocalExplorer = true }
                            )
                            1 -> PinnedScreen(syncViewModel = syncViewModel)
                            2 -> SettingsScreen(syncViewModel = syncViewModel, securityViewModel = securityViewModel)
                        }
                    }
                }
            }
        }

        if (!isLocked && incomingTransfer != null) {
            val transfer = incomingTransfer!!
            val progress = if (transfer.size > 0L) {
                (transfer.bytesReceived.toFloat() / transfer.size.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = FluentTheme.colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transfer.name,
                                color = FluentTheme.colors.textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "${formatTransferBytes(transfer.bytesReceived)} / ${formatTransferBytes(transfer.size)} - ${transfer.status}",
                                color = FluentTheme.colors.textMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = FluentTheme.colors.accent,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        color = FluentTheme.colors.accent,
                        trackColor = FluentTheme.colors.panelBorder
                    )
                }
            }
        }

        // Outgoing (phone → desktop) transfer progress
        if (!isLocked && outgoingTransfer != null) {
            val upload = outgoingTransfer!!
            val progress = if (upload.size > 0L) {
                (upload.bytesSent.toFloat() / upload.size.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = if (incomingTransfer != null) 80.dp else 8.dp),
                colors = CardDefaults.cardColors(containerColor = FluentTheme.colors.surfaceBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = FluentTheme.colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = upload.name,
                                color = FluentTheme.colors.textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "${formatTransferBytes(upload.bytesSent)} / ${formatTransferBytes(upload.size)} - ${upload.status}",
                                color = FluentTheme.colors.textMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = FluentTheme.colors.accent,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        color = FluentTheme.colors.accent,
                        trackColor = FluentTheme.colors.panelBorder
                    )
                }
            }
        }

        if (isLocked) {
            AppLockScreen(securityViewModel = securityViewModel)
        }
    }
}
