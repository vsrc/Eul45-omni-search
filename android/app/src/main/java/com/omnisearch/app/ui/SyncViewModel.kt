package com.omnisearch.app.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnisearch.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AWAITING_APPROVAL,
    CONNECTED
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val OFFLINE_PREVIEW_CACHE_LIMIT_BYTES = 1024L * 1024L
    }

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.pinnedFileDao()
    private val prefs = PreferencesManager(context)

    // UI State flows
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _drives = MutableStateFlow<List<DriveInfo>>(emptyList())
    val drives: StateFlow<List<DriveInfo>> = _drives.asStateFlow()

    private val _indexStatus = MutableStateFlow<IndexStatus?>(null)
    val indexStatus: StateFlow<IndexStatus?> = _indexStatus.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private val _actionMessage = MutableStateFlow("")
    val actionMessage: StateFlow<String> = _actionMessage.asStateFlow()

    private val _serverAddress = MutableStateFlow("")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()

    private val _pairingLink = MutableStateFlow("")
    val pairingLink: StateFlow<String> = _pairingLink.asStateFlow()

    private val _pairingMessage = MutableStateFlow("")
    val pairingMessage: StateFlow<String> = _pairingMessage.asStateFlow()

    // File content preview state
    data class FileContent(
        val path: String,
        val contentType: String,
        val data: String // base64
    )

    data class IncomingTransferProgress(
        val id: String,
        val name: String,
        val size: Long,
        val bytesReceived: Long,
        val status: String
    )

    private data class IncomingTransfer(
        val id: String,
        val name: String,
        val path: String,
        val extension: String,
        val size: Long,
        val createdUnix: Long,
        val modifiedUnix: Long,
        val contentType: String,
        val totalChunks: Int,
        val outputUri: Uri,
        val outputStream: OutputStream,
        val previewBuffer: ByteArrayOutputStream?
    )

    private val _fileContent = MutableStateFlow<FileContent?>(null)
    val fileContent: StateFlow<FileContent?> = _fileContent.asStateFlow()

    private val _isLoadingFileContent = MutableStateFlow(false)
    val isLoadingFileContent: StateFlow<Boolean> = _isLoadingFileContent.asStateFlow()

    private val _incomingTransfer = MutableStateFlow<IncomingTransferProgress?>(null)
    val incomingTransfer: StateFlow<IncomingTransferProgress?> = _incomingTransfer.asStateFlow()
    private val activeUploadConfirmChannel = Channel<JSONObject>(capacity = 16)

    @Volatile
    private var isUploadingToDesktop = false
    private val _outgoingTransfer = MutableStateFlow<OutgoingTransferProgress?>(null)
    val outgoingTransfer: StateFlow<OutgoingTransferProgress?> = _outgoingTransfer.asStateFlow()

    data class OutgoingTransferProgress(
        val name: String,
        val size: Long,
        val bytesSent: Long,
        val status: String // "Uploading", "Completed", "Failed"
    )

    private val _activeDriveLetter = MutableStateFlow("C")
    val activeDriveLetter: StateFlow<String> = _activeDriveLetter.asStateFlow()

    // Offline pinned files
    val pinnedFiles: StateFlow<List<PinnedFileEntity>> = dao.getAllPinnedFiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var pairingJob: Job? = null
    private var transferPollJob: Job? = null
    @Volatile
    private var activeIncomingTransfer: IncomingTransfer? = null
    private var connectionGeneration = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Load the last pairing link for convenience, but do not auto-connect on app startup.
        // Old pairing tokens expire when the desktop sync server restarts and auto-connect
        // can race with a fresh QR scan.
        viewModelScope.launch {
            prefs.omniSearchAddressFlow.firstOrNull()?.let { savedAddress ->
                if (savedAddress.isNotEmpty()) {
                    _serverAddress.value = parseHost(savedAddress)
                    _pairingLink.value = savedAddress
                }
            }
        }
        viewModelScope.launch {
            prefs.activeSearchDriveFlow.collect { drive ->
                _activeDriveLetter.value = drive
            }
        }
    }

    fun clearNotifications() {
        _error.value = ""
        _actionMessage.value = ""
    }

    fun dismissIncomingTransfer() {
        if (activeIncomingTransfer == null) {
            _incomingTransfer.value = null
        }
    }

    private fun parseHost(input: String): String {
        return try {
            val normalized = when {
                input.startsWith("omnisearch://") -> "http://" + input.substring("omnisearch://".length)
                input.startsWith("http://") || input.startsWith("https://") -> input
                else -> "http://$input"
            }
            val uri = java.net.URI(normalized)
            uri.host ?: input
        } catch (e: Exception) {
            input
        }
    }

    fun connect(connectionInput: String) {
        connectionGeneration += 1
        val generation = connectionGeneration
        closeActiveConnection()

        val trimmed = connectionInput.trim()
        if (trimmed.isEmpty()) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "Enter a valid pairing link or scan the QR code."
            return
        }

        // Parse pairing components
        val normalized = when {
            trimmed.startsWith("omnisearch://") -> "http://" + trimmed.substring("omnisearch://".length)
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }

        val address: String
        val token: String?
        val certFingerprint: String?
        try {
            val uri = java.net.URI(normalized)
            address = uri.authority ?: ""
            val queryParams = parseQueryParams(uri.rawQuery ?: "")
            token = queryParams["token"]
            certFingerprint = queryParams["cert"]
        } catch (e: Exception) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "Invalid pairing address. Please scan QR or check format."
            return
        }

        if (address.isEmpty()) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "Valid server address host:port required."
            return
        }

        if (token == null || token.isEmpty()) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "Pairing link is missing token parameter. Scan QR from PC."
            return
        }

        if (certFingerprint == null || certFingerprint.isEmpty()) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "Secure connection required. Please scan the updated QR code."
            return
        }

        _serverAddress.value = address
        _pairingLink.value = trimmed
        _status.value = ConnectionStatus.CONNECTING
        _error.value = ""
        _pairingMessage.value = "Connecting..."

        viewModelScope.launch {
            // Only save the host:port, do NOT save the sensitive token to preferences
            prefs.saveAddress(address)
            val deviceId = prefs.getOrCreateDeviceId()
            val deviceSuffix = deviceId.takeLast(4).uppercase()
            val deviceName = "Android Phone $deviceSuffix"

            setupWebSocket(address, token, deviceId, deviceName, generation, certFingerprint)
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val separator = pair.indexOf("=")
            if (separator <= 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8.name())
            key to value
        }.toMap()
    }

    private fun setupWebSocket(
        address: String,
        token: String,
        deviceId: String,
        deviceName: String,
        generation: Int,
        certFingerprint: String
    ) {
        val url = "wss://$address"
        val request: Request
        try {
            request = Request.Builder().url(url).build()
        } catch (e: Exception) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "Invalid websocket URL: ${e.message}"
            return
        }

        val clientBuilder = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)

        try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain.isNullOrEmpty()) throw javax.net.ssl.SSLException("No server certificate")
                    val serverCert = chain[0]
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = digest.digest(serverCert.encoded)
                    val serverFingerprint = hash.joinToString(":") { "%02x".format(it) }
                    if (!serverFingerprint.equals(certFingerprint, ignoreCase = true)) {
                        throw javax.net.ssl.SSLException(
                            "Certificate fingerprint mismatch. Expected: $certFingerprint, Got: $serverFingerprint"
                        )
                    }
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), java.security.SecureRandom())
            clientBuilder.sslSocketFactory(sslContext.socketFactory, trustManager)
            clientBuilder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            _status.value = ConnectionStatus.DISCONNECTED
            _error.value = "TLS setup failed: ${e.message}"
            return
        }

        client = clientBuilder.build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            @Volatile private var isApproved = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != connectionGeneration) return
                _error.value = ""
                _pairingMessage.value = "Requesting approval from your desktop..."

                // Start pairing loop every 1800ms
                pairingJob = viewModelScope.launch(Dispatchers.IO) {
                    while (true) {
                        if (isApproved) break
                        if (generation != connectionGeneration) break
                        try {
                            val pairMsg = JSONObject().apply {
                                put("type", "pair_request")
                                put("payload", JSONObject().apply {
                                    put("token", token)
                                    put("device_id", deviceId)
                                    put("device_name", deviceName)
                                })
                            }
                            webSocket.send(pairMsg.toString())
                        } catch (e: Exception) {
                            Log.e("WebSocket", "Pair request generation error", e)
                        }
                        delay(1800)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != connectionGeneration) return
                try {
                    val root = JSONObject(text)
                    val type = root.optString("type")
                    val payload = root.optJSONObject("payload") ?: JSONObject()

                    when (type) {
                        "pairing_status" -> {
                            val pairingStatus = payload.optString("status")
                            val pairingMsg = payload.optString("message")

                            if (pairingStatus == "approved") {
                                isApproved = true
                                pairingJob?.cancel()
                                _status.value = ConnectionStatus.CONNECTED
                                _pairingMessage.value = ""
                                _error.value = ""
                                // Query server status and drives on startup
                                queryDrives()
                                queryIndexStatus()
                                startTransferPolling()
                            } else if (pairingStatus == "pending") {
                                _status.value = ConnectionStatus.AWAITING_APPROVAL
                                _pairingMessage.value = pairingMsg
                                _error.value = ""
                            } else {
                                isApproved = false
                                pairingJob?.cancel()
                                _status.value = ConnectionStatus.DISCONNECTED
                                _pairingMessage.value = ""
                                _error.value = pairingMsg
                                webSocket.close(1000, "Pairing rejected")
                            }
                        }

                        "search_response" -> {
                            _isSearching.value = false
                            val resultsArray = payload.optJSONArray("results") ?: JSONArray()
                            _searchResults.value = SearchResult.fromJsonArray(resultsArray)
                        }

                        "drives_response" -> {
                            val drivesArray = payload.optJSONArray("drives") ?: JSONArray()
                            _drives.value = DriveInfo.fromJsonArray(drivesArray)
                        }

                        "index_status_response" -> {
                            val statusObj = payload.optJSONObject("status")
                            if (statusObj != null) {
                                val status = IndexStatus.fromJson(statusObj)
                                _indexStatus.value = status
                                if (status.indexing) {
                                    viewModelScope.launch {
                                        delay(1000)
                                        queryIndexStatus()
                                    }
                                }
                            }
                        }

                        "duplicates_response" -> {
                            _isScanningDuplicates.value = false
                            val groupsArray = payload.optJSONArray("groups") ?: JSONArray()
                            _duplicateGroups.value = DuplicateGroup.fromJsonArray(groupsArray)
                        }

                        "confirm" -> {
                            val success = payload.optBoolean("success")
                            val message = payload.optString("message")
                            // Route upload-related confirms to the upload channel
                            if (message.contains("Manifest received") ||
                                message.contains("Chunk ") ||
                                (isUploadingToDesktop && !success)
                            ) {
                                activeUploadConfirmChannel.trySend(payload)
                            } else {
                                if (success) {
                                    _actionMessage.value = message
                                    viewModelScope.launch {
                                        delay(3000)
                                        if (_actionMessage.value == message) _actionMessage.value = ""
                                    }
                                } else {
                                    _error.value = message
                                    viewModelScope.launch {
                                        delay(4000)
                                        if (_error.value == message) _error.value = ""
                                    }
                                }
                            }
                        }

                        "file_content_response" -> {
                            _isLoadingFileContent.value = false
                            val filePath = payload.optString("path", "")
                            val contentType = if (payload.has("content_type")) {
                                payload.optString("content_type", "")
                            } else {
                                payload.optString("contentType", "")
                            }
                            val data = payload.optString("data", "")
                            _fileContent.value = FileContent(filePath, contentType, data)

                            // Cache content locally in database if file is pinned
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val pinned = dao.getPinnedFileByPath(filePath)
                                    if (pinned != null && pinned.fileData == null) {
                                        dao.insertPinnedFile(pinned.copy(fileData = data))
                                    }
                                } catch (e: Exception) {
                                    Log.e("SyncViewModel", "Error caching pinned file", e)
                                }
                            }
                        }

                        "desktop_transfer_manifest" -> {
                            val transferObj = payload.optJSONObject("transfer") ?: return
                            val transferId = transferObj.optString("id", "")
                            if (transferId.isNotEmpty()) {
                                val name = transferObj.optString("name", "file")
                                val contentType = transferObj.optString("contentType", "application/octet-stream")
                                val outputUri = createDownloadUri(name, contentType)
                                val outputStream = outputUri?.let { context.contentResolver.openOutputStream(it) }
                                if (outputUri == null || outputStream == null) {
                                    _error.value = "Could not create a phone download file for $name"
                                    return
                                }

                                val transfer = IncomingTransfer(
                                    id = transferId,
                                    name = name,
                                    path = transferObj.optString("path", ""),
                                    extension = transferObj.optString("extension", ""),
                                    size = transferObj.optLong("size", 0L),
                                    createdUnix = transferObj.optLong("createdUnix", 0L),
                                    modifiedUnix = transferObj.optLong("modifiedUnix", 0L),
                                    contentType = contentType,
                                    totalChunks = transferObj.optInt("totalChunks", 1).coerceAtLeast(1),
                                    outputUri = outputUri,
                                    outputStream = outputStream,
                                    previewBuffer = if (
                                        transferObj.optLong("size", 0L) <= OFFLINE_PREVIEW_CACHE_LIMIT_BYTES &&
                                        (isImageTransfer(name, contentType) || isDocumentTransfer(name, contentType))
                                    ) {
                                        ByteArrayOutputStream()
                                    } else {
                                        null
                                    }
                                )
                                activeIncomingTransfer = transfer
                                _incomingTransfer.value = IncomingTransferProgress(
                                    id = transfer.id,
                                    name = transfer.name,
                                    size = transfer.size,
                                    bytesReceived = 0L,
                                    status = "Downloading"
                                )
                                requestTransferChunk(transfer.id, 0)
                            }
                        }

                        "desktop_transfer_chunk" -> {
                            val transferId = payload.optString("transfer_id")
                                .ifEmpty { payload.optString("transferId") }
                            val transfer = activeIncomingTransfer
                            if (transfer == null || transfer.id != transferId) return

                            val chunkIndex = payload.optInt("chunk_index", payload.optInt("chunkIndex", 0))
                            val data = payload.optString("data", "")
                            val done = payload.optBoolean("done", false)
                            val bytesSent = payload.optLong("bytes_sent", payload.optLong("bytesSent", 0L))

                            try {
                                val decoded = Base64.decode(data, Base64.DEFAULT)
                                transfer.outputStream.write(decoded)
                                transfer.previewBuffer?.write(decoded)
                                _incomingTransfer.value = IncomingTransferProgress(
                                    id = transfer.id,
                                    name = transfer.name,
                                    size = transfer.size,
                                    bytesReceived = bytesSent,
                                    status = if (done) "Saving" else "Downloading"
                                )

                                if (done) {
                                    finishIncomingTransfer(transfer)
                                } else {
                                    viewModelScope.launch {
                                        delay(16)
                                        requestTransferChunk(transfer.id, chunkIndex + 1)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SyncViewModel", "Transfer chunk error", e)
                                try {
                                    transfer.outputStream.close()
                                } catch (_: Exception) {}
                                activeIncomingTransfer = null
                                _incomingTransfer.value = IncomingTransferProgress(
                                    id = transfer.id,
                                    name = transfer.name,
                                    size = transfer.size,
                                    bytesReceived = bytesSent,
                                    status = "Failed"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Message parsing error: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation != connectionGeneration) return
                pairingJob?.cancel()
                transferPollJob?.cancel()
                closeActiveIncomingTransfer()
                _isSearching.value = false
                _isScanningDuplicates.value = false
                _status.value = ConnectionStatus.DISCONNECTED
                val detail = t.localizedMessage?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                _error.value = "Connection failed: $detail"
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != connectionGeneration) return
                pairingJob?.cancel()
                transferPollJob?.cancel()
                closeActiveIncomingTransfer()
                _status.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != connectionGeneration) return
                pairingJob?.cancel()
                transferPollJob?.cancel()
                closeActiveIncomingTransfer()
                _status.value = ConnectionStatus.DISCONNECTED
            }
        })
    }

    fun disconnect() {
        connectionGeneration += 1
        closeActiveConnection()
        _status.value = ConnectionStatus.DISCONNECTED
        _searchResults.value = emptyList()
        _drives.value = emptyList()
        _indexStatus.value = null
        _duplicateGroups.value = emptyList()
        _isSearching.value = false
        _isScanningDuplicates.value = false
        _pairingMessage.value = ""
        closeActiveIncomingTransfer()
        _incomingTransfer.value = null

        viewModelScope.launch {
            prefs.clearAddress()
        }
    }

    private fun closeActiveConnection() {
        pairingJob?.cancel()
        transferPollJob?.cancel()
        try {
            webSocket?.close(1000, "Connection replaced")
        } catch (_: Exception) {}
        webSocket = null
        client = null
        closeActiveIncomingTransfer()
    }

    private fun startTransferPolling() {
        transferPollJob?.cancel()
        transferPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (_status.value == ConnectionStatus.CONNECTED) {
                if (activeIncomingTransfer == null) {
                    sendMsg(JSONObject().apply {
                        put("type", "request_desktop_transfer")
                    })
                }
                delay(1200)
            }
        }
    }

    private fun closeActiveIncomingTransfer() {
        try {
            activeIncomingTransfer?.outputStream?.close()
        } catch (_: Exception) {}
        activeIncomingTransfer = null
    }

    // Remote Actions
    fun executeSearch(query: String, extension: String? = null, limit: Int = 100) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        sendMsg(JSONObject().apply {
            put("type", "search_query")
            put("payload", JSONObject().apply {
                put("query", query)
                put("limit", limit)
                put("extension", extension ?: JSONObject.NULL)
            })
        })
    }

    fun queryDrives() {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "request_drives")
        })
    }

    fun queryIndexStatus() {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "request_index_status")
        })
    }

    fun startIndexing(driveLetter: String) {
        if (_status.value != ConnectionStatus.CONNECTED) return

        // Immediately reset search results to prevent displaying old drive results
        _searchResults.value = emptyList()
        _isSearching.value = false

        // Let's set a temporary indexing status so the UI knows we just requested indexing
        _indexStatus.value = IndexStatus(indexing = true, ready = false, indexedCount = 0L, lastError = null)

        viewModelScope.launch {
            prefs.saveActiveSearchDrive(driveLetter)
            _activeDriveLetter.value = driveLetter
        }

        sendMsg(JSONObject().apply {
            put("type", "start_indexing")
            put("payload", JSONObject().apply {
                put("drive", driveLetter)
            })
        })

        viewModelScope.launch {
            delay(500)
            queryIndexStatus()
        }
    }

    fun scanDuplicates(minSizeBytes: Long = 50L * 1024 * 1024) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        _isScanningDuplicates.value = true
        sendMsg(JSONObject().apply {
            put("type", "request_duplicates")
            put("payload", JSONObject().apply {
                put("minSize", minSizeBytes)
            })
        })
    }

    fun openFileOnPC(path: String) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "remote_action")
            put("payload", JSONObject().apply {
                put("action", "open_on_pc")
                put("path", path)
            })
        })
    }

    fun revealInPCExplorer(path: String) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "remote_action")
            put("payload", JSONObject().apply {
                put("action", "reveal_in_explorer")
                put("path", path)
            })
        })
    }

    fun deleteFileOnPC(path: String, recycleBin: Boolean = true) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "remote_action")
            put("payload", JSONObject().apply {
                put("action", "delete")
                put("path", path)
                put("recycleBin", recycleBin)
            })
        })
    }

    private fun requestTransferChunk(transferId: String, chunkIndex: Int) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "request_desktop_transfer_chunk")
            put("payload", JSONObject().apply {
                put("transfer_id", transferId)
                put("chunk_index", chunkIndex)
            })
        })
    }

    private fun createDownloadUri(fileName: String, contentType: String): Uri? {
        val mimeType = storageMimeType(fileName, contentType)
        val categoryDir = transferFolderName(fileName, mimeType)
        val subDir = when {
            isImageTransfer(fileName, mimeType) -> Environment.DIRECTORY_PICTURES
            isVideoTransfer(fileName, mimeType) -> Environment.DIRECTORY_MOVIES
            isAudioTransfer(fileName, mimeType) -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val relativePath = "$subDir/OmniSearch/$categoryDir"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Modern MediaStore path (API 29+)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            return when {
                isImageTransfer(fileName, mimeType) ->
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                isVideoTransfer(fileName, mimeType) ->
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                isAudioTransfer(fileName, mimeType) ->
                    context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                else ->
                    context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }
        } else {
            // Legacy file-based path for Android < 10 (pre-Q).
            // MediaStore.Downloads does NOT exist before API 29 and will crash.
            try {
                val publicDir = Environment.getExternalStoragePublicDirectory(subDir)
                val omniDir = java.io.File(java.io.File(publicDir, "OmniSearch"), categoryDir)
                if (!omniDir.exists()) omniDir.mkdirs()

                // Handle duplicate file names
                var targetFile = java.io.File(omniDir, fileName)
                if (targetFile.exists()) {
                    val baseName = fileName.substringBeforeLast(".", fileName)
                    val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
                    var counter = 1
                    while (targetFile.exists()) {
                        targetFile = java.io.File(omniDir, "${baseName}_${counter}${ext}")
                        counter++
                    }
                }
                targetFile.createNewFile()
                return Uri.fromFile(targetFile)
            } catch (e: Exception) {
                Log.e("SyncViewModel", "Legacy createDownloadUri failed", e)
                return null
            }
        }
    }

    private fun transferFolderName(fileName: String, mimeType: String): String = when {
        isImageTransfer(fileName, mimeType) -> "Images"
        isVideoTransfer(fileName, mimeType) -> "Videos"
        isAudioTransfer(fileName, mimeType) -> "Audio"
        isDocumentTransfer(fileName, mimeType) -> "Documents"
        isArchiveTransfer(fileName, mimeType) -> "Archives"
        isAppTransfer(fileName, mimeType) -> "Apps"
        else -> "Files"
    }

    private fun fileExtension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    private fun storageMimeType(fileName: String, contentType: String): String {
        val extension = fileExtension(fileName)
        val normalized = contentType.ifBlank { "application/octet-stream" }.lowercase()

        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (!mapped.isNullOrBlank() && mapped != "text/plain") {
            return mapped
        }

        return when (extension) {
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "log" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
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
            "rs", "py", "java", "js", "ts", "tsx", "jsx", "kt", "cpp", "c", "h",
            "html", "css", "go", "rb", "sh", "bat", "ps1", "toml", "yaml", "yml",
            "ini", "cfg", "conf" -> "application/octet-stream"
            else -> normalized.takeUnless { it == "text/plain" } ?: "application/octet-stream"
        }
    }

    private fun isImageTransfer(fileName: String, mimeType: String): Boolean =
        mimeType.startsWith("image/") ||
            fileExtension(fileName) in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    private fun isVideoTransfer(fileName: String, mimeType: String): Boolean =
        mimeType.startsWith("video/") ||
            fileExtension(fileName) in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v")

    private fun isAudioTransfer(fileName: String, mimeType: String): Boolean =
        mimeType.startsWith("audio/") ||
            fileExtension(fileName) in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

    private fun isDocumentTransfer(fileName: String, mimeType: String): Boolean {
        val extension = fileExtension(fileName)
        return mimeType.startsWith("text/") ||
            mimeType == "application/pdf" ||
            mimeType.contains("document") ||
            mimeType.contains("spreadsheet") ||
            mimeType.contains("presentation") ||
            extension in setOf(
                "pdf", "txt", "md", "rtf", "csv", "json", "xml", "doc", "docx", "xls", "xlsx",
                "ppt", "pptx", "odt", "ods", "odp"
            )
    }

    private fun isArchiveTransfer(fileName: String, mimeType: String): Boolean =
        mimeType.contains("zip") ||
            mimeType.contains("rar") ||
            mimeType.contains("7z") ||
            mimeType.contains("tar") ||
            mimeType.contains("gzip") ||
            fileExtension(fileName) in setOf("zip", "rar", "7z", "tar", "gz", "tgz")

    private fun isAppTransfer(fileName: String, mimeType: String): Boolean =
        mimeType == "application/vnd.android.package-archive" || fileExtension(fileName) == "apk"

    private fun finalizeDownloadUri(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    private fun finishIncomingTransfer(transfer: IncomingTransfer) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                transfer.outputStream.flush()
                transfer.outputStream.close()
                finalizeDownloadUri(transfer.outputUri)

                // On pre-Q devices using file:// URIs, notify the media scanner
                // so the file appears in file managers and gallery immediately
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && transfer.outputUri.scheme == "file") {
                    val filePath = transfer.outputUri.path
                    if (filePath != null) {
                        android.media.MediaScannerConnection.scanFile(
                            context, arrayOf(filePath), null, null
                        )
                    }
                }
                val base64Data = transfer.previewBuffer?.let {
                    Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
                }
                val entity = PinnedFileEntity(
                    path = transfer.path,
                    name = transfer.name,
                    extension = transfer.extension,
                    size = transfer.size,
                    createdUnix = transfer.createdUnix,
                    modifiedUnix = transfer.modifiedUnix,
                    isDirectory = false,
                    fileData = base64Data
                )
                dao.insertPinnedFile(entity)
                withContext(Dispatchers.Main) {
                    activeIncomingTransfer = null
                    _incomingTransfer.value = IncomingTransferProgress(
                        id = transfer.id,
                        name = transfer.name,
                        size = transfer.size,
                        bytesReceived = transfer.size,
                        status = "Downloaded"
                    )
                    _actionMessage.value = "Downloaded ${transfer.name}"
                }
            } catch (e: Exception) {
                Log.e("SyncViewModel", "Error saving incoming transfer", e)
                try {
                    transfer.outputStream.close()
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    activeIncomingTransfer = null
                    _incomingTransfer.value = IncomingTransferProgress(
                        id = transfer.id,
                        name = transfer.name,
                        size = transfer.size,
                        bytesReceived = transfer.previewBuffer?.size()?.toLong() ?: 0L,
                        status = "Failed"
                    )
                    _error.value = "Transfer save failed: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun sendMsg(json: JSONObject) {
        webSocket?.let { ws ->
            try {
                ws.send(json.toString())
            } catch (e: Exception) {
                Log.e("WebSocket", "Send error", e)
            }
        }
    }

    fun requestFileContent(path: String) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        _isLoadingFileContent.value = true
        _fileContent.value = null
        sendMsg(JSONObject().apply {
            put("type", "request_file_content")
            put("payload", JSONObject().apply {
                put("path", path)
            })
        })
    }

    fun triggerRemoteTransfer(path: String) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        sendMsg(JSONObject().apply {
            put("type", "remote_action")
            put("payload", JSONObject().apply {
                put("action", "transfer_to_phone")
                put("path", path)
            })
        })
    }

    fun clearFileContent() {
        _fileContent.value = null
        _isLoadingFileContent.value = false
    }

    fun saveFileToDisk(context: Context, fileName: String, contentType: String, base64Data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val mimeType = storageMimeType(fileName, contentType)
                val subDir = when {
                    mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
                    else -> Environment.DIRECTORY_DOWNLOADS
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
                    }
                }

                val uri = when {
                    mimeType.startsWith("image/") ->
                        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    else ->
                        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                }

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved: $fileName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Local Pinned Files Actions
    fun pinFile(result: SearchResult, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = PinnedFileEntity(
                    path = result.path,
                    name = result.name,
                    extension = result.extension,
                    size = result.size,
                    createdUnix = result.createdUnix,
                    modifiedUnix = result.modifiedUnix,
                    isDirectory = result.isDirectory
                )
                dao.insertPinnedFile(entity)

                // Immediately fetch content from server in the background to save it offline
                if (_status.value == ConnectionStatus.CONNECTED) {
                    sendMsg(JSONObject().apply {
                        put("type", "request_file_content")
                        put("payload", JSONObject().apply {
                            put("path", result.path)
                        })
                    })
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Pinned to local Device Hub", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error pinning file: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun unpinFile(path: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.deletePinnedFileByPath(path)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Unpinned file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error unpinning file: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadFileContentLocally(path: String, contentType: String, data: String) {
        _isLoadingFileContent.value = false
        _fileContent.value = FileContent(path, contentType, data)
    }

    fun dismissOutgoingTransfer() {
        _outgoingTransfer.value = null
    }

    /**
     * Send files from the phone to the connected desktop.
     * Each file is uploaded sequentially using chunked base64 WebSocket messages.
     */
    fun sendFilesToDesktop(context: Context, files: List<java.io.File>) {
        if (_status.value != ConnectionStatus.CONNECTED) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Not connected to desktop", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (isUploadingToDesktop) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Already uploading...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isUploadingToDesktop = true
            // Drain any stale confirms in the channel
            while (activeUploadConfirmChannel.tryReceive().isSuccess) { /* drain */ }

            try {
                for (file in files) {
                    if (_status.value != ConnectionStatus.CONNECTED) {
                        withContext(Dispatchers.Main) {
                            _outgoingTransfer.value = OutgoingTransferProgress(
                                name = file.name, size = file.length(),
                                bytesSent = 0, status = "Failed"
                            )
                        }
                        break
                    }
                    uploadSingleFile(file)
                }
            } finally {
                isUploadingToDesktop = false
            }
        }
    }

    private suspend fun uploadSingleFile(file: java.io.File) {
        val chunkSize = 192 * 1024L  // Match desktop TRANSFER_CHUNK_BYTES
        val fileSize = file.length()
        val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val transferId = java.util.UUID.randomUUID().toString()

        val contentType = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "txt", "md", "log", "json", "xml", "csv" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }

        withContext(Dispatchers.Main) {
            _outgoingTransfer.value = OutgoingTransferProgress(
                name = file.name, size = fileSize, bytesSent = 0, status = "Uploading"
            )
        }

        // Send manifest
        sendMsg(JSONObject().apply {
            put("type", "phone_upload_manifest")
            put("payload", JSONObject().apply {
                put("id", transferId)
                put("name", file.name)
                put("size", fileSize)
                put("content_type", contentType)
                put("total_chunks", totalChunks)
            })
        })

        // Wait for manifest confirmation (10s timeout)
        val manifestReply = withTimeoutOrNull(10_000L) {
            activeUploadConfirmChannel.receive()
        }
        if (manifestReply == null || !manifestReply.optBoolean("success", false)) {
            val err = manifestReply?.optString("message") ?: "Manifest rejected / timeout"
            withContext(Dispatchers.Main) {
                _outgoingTransfer.value = OutgoingTransferProgress(
                    name = file.name, size = fileSize, bytesSent = 0, status = "Failed"
                )
                _error.value = "Upload failed: $err"
            }
            return
        }

        // Send chunks
        file.inputStream().buffered().use { inputStream ->
            for (chunkIndex in 0 until totalChunks) {
                val bytesToRead = minOf(chunkSize, fileSize - (chunkIndex * chunkSize)).toInt()
                val buffer = ByteArray(bytesToRead)
                var offset = 0
                while (offset < bytesToRead) {
                    val read = inputStream.read(buffer, offset, bytesToRead - offset)
                    if (read <= 0) break
                    offset += read
                }
                val base64Chunk = Base64.encodeToString(buffer, Base64.NO_WRAP)
                val isDone = chunkIndex == totalChunks - 1

                sendMsg(JSONObject().apply {
                    put("type", "phone_upload_chunk")
                    put("payload", JSONObject().apply {
                        put("transfer_id", transferId)
                        put("chunk_index", chunkIndex)
                        put("data", base64Chunk)
                        put("done", isDone)
                    })
                })

                // Wait for chunk confirmation (15s timeout)
                val chunkReply = withTimeoutOrNull(15_000L) {
                    activeUploadConfirmChannel.receive()
                }
                if (chunkReply == null || !chunkReply.optBoolean("success", false)) {
                    val err = chunkReply?.optString("message") ?: "Chunk $chunkIndex rejected / timeout"
                    withContext(Dispatchers.Main) {
                        _outgoingTransfer.value = OutgoingTransferProgress(
                            name = file.name, size = fileSize,
                            bytesSent = (chunkIndex * chunkSize).coerceAtMost(fileSize),
                            status = "Failed"
                        )
                        _error.value = "Upload failed: $err"
                    }
                    return
                }

                val bytesSent = minOf((chunkIndex + 1) * chunkSize, fileSize)
                withContext(Dispatchers.Main) {
                    _outgoingTransfer.value = OutgoingTransferProgress(
                        name = file.name, size = fileSize,
                        bytesSent = bytesSent, status = if (isDone) "Completed" else "Uploading"
                    )
                }
            }
        }
    }

    val themeMode: StateFlow<String> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, context.getSharedPreferences("omnisearch_widget_theme", Context.MODE_PRIVATE).getString("theme_mode", "SYSTEM") ?: "SYSTEM")

    val themeColor: StateFlow<String> = prefs.themeColorFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SLATE")

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            prefs.saveThemeMode(mode)
            com.omnisearch.app.widget.WidgetThemeUtil.saveThemeMode(context, mode)
            updateAllWidgets()
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        
        val explorerIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.LocalExplorerWidgetProvider::class.java)
        )
        if (explorerIds.isNotEmpty()) {
            com.omnisearch.app.widget.LocalExplorerWidgetProvider().onUpdate(context, appWidgetManager, explorerIds)
        }
        
        val scanIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.ScanConnectWidgetProvider::class.java)
        )
        for (id in scanIds) {
            com.omnisearch.app.widget.ScanConnectWidgetProvider.updateAppWidget(context, appWidgetManager, id)
        }
        
        val musicIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.MusicWidgetProvider::class.java)
        )
        for (id in musicIds) {
            com.omnisearch.app.widget.MusicWidgetProvider.updateAppWidget(context, appWidgetManager, id)
        }
        
        val musicSmallIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.MusicWidgetSmallProvider::class.java)
        )
        for (id in musicSmallIds) {
            com.omnisearch.app.widget.MusicWidgetSmallProvider.updateAppWidgetSmall(context, appWidgetManager, id)
        }

        val searchIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.SearchWidgetProvider::class.java)
        )
        for (id in searchIds) {
            com.omnisearch.app.widget.SearchWidgetProvider.updateAppWidget(context, appWidgetManager, id)
        }
    }

    fun setThemeColor(color: String) {
        viewModelScope.launch {
            prefs.saveThemeColor(color)
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
