package com.filebridge.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filebridge.data.db.DeletedFile
import com.filebridge.data.db.UploadedFile
import com.filebridge.data.repository.FileRepository
import com.filebridge.service.HttpServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    private val application: Application,
    private val repository: FileRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FileViewModel"
        private const val PREFS_NAME = "filebridge_prefs"
        private const val KEY_AUTO_START = "auto_start_http"
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val files: StateFlow<List<UploadedFile>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val deletedFiles: StateFlow<List<DeletedFile>> = repository.deletedFiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _serverRunning = MutableStateFlow(HttpServerService.isRunning)
    val serverRunning: StateFlow<Boolean> = _serverRunning.asStateFlow()

    private val _autoStartEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_START, true))
    val autoStartEnabled: StateFlow<Boolean> = _autoStartEnabled.asStateFlow()

    private val _duplicateHashes = MutableStateFlow<Set<String>>(emptySet())
    val duplicateHashes: StateFlow<Set<String>> = _duplicateHashes.asStateFlow()

    private val _deletedDuplicateHashes = MutableStateFlow<Set<String>>(emptySet())
    val deletedDuplicateHashes: StateFlow<Set<String>> = _deletedDuplicateHashes.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.importExistingFiles()
                refreshDuplicateHashes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import existing files", e)
            }
        }

        if (_autoStartEnabled.value && !HttpServerService.isRunning) {
            startHttpServer()
        }
    }

    fun importFiles() {
        viewModelScope.launch {
            try {
                repository.importExistingFiles()
                refreshDuplicateHashes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import files", e)
            }
        }
    }

    fun refreshServerStatus() {
        _serverRunning.value = HttpServerService.isRunning
    }

    fun refreshDuplicateHashes() {
        viewModelScope.launch {
            try {
                _duplicateHashes.value = repository.getDuplicateHashes()
                _deletedDuplicateHashes.value = repository.getDeletedDuplicateHashes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh duplicate hashes", e)
            }
        }
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
        _autoStartEnabled.value = enabled
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.uploadFile(uri)
                    .onSuccess { file ->
                        _toastMessage.value = "已上传: #${file.id} ${file.fileName}"
                        refreshDuplicateHashes()
                    }
                    .onFailure { e ->
                        Log.e(TAG, "Upload failed", e)
                        _toastMessage.value = "上传失败: ${e.message}"
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Upload exception", e)
                _toastMessage.value = "上传失败: ${e.message}"
            }
        }
    }

    fun deleteFile(id: Int) {
        viewModelScope.launch {
            try {
                repository.deleteFile(id)
                _toastMessage.value = "已删除: #$id"
                refreshDuplicateHashes()
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                _toastMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    fun restoreFile(id: Int) {
        viewModelScope.launch {
            try {
                val success = repository.restoreFile(id)
                if (success) {
                    _toastMessage.value = "已恢复: #$id"
                    refreshDuplicateHashes()
                } else {
                    _toastMessage.value = "恢复失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                _toastMessage.value = "恢复失败: ${e.message}"
            }
        }
    }

    fun permanentlyDeleteFile(id: Int) {
        viewModelScope.launch {
            try {
                repository.permanentlyDeleteFile(id)
                _toastMessage.value = "已永久删除: #$id"
                refreshDuplicateHashes()
            } catch (e: Exception) {
                Log.e(TAG, "Permanent delete failed", e)
                _toastMessage.value = "永久删除失败: ${e.message}"
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                repository.emptyTrash()
                _toastMessage.value = "回收站已清空"
                refreshDuplicateHashes()
            } catch (e: Exception) {
                Log.e(TAG, "Empty trash failed", e)
                _toastMessage.value = "清空失败: ${e.message}"
            }
        }
    }

    fun copyToClipboard(id: Int, format: CopyFormat = CopyFormat.NUMBER) {
        viewModelScope.launch {
            try {
                val file = repository.getFileById(id) ?: return@launch
                val text = when (format) {
                    CopyFormat.NUMBER -> "#$id"
                    CopyFormat.PATH -> file.filePath
                    CopyFormat.HTTP_URL -> "http://localhost:${HttpServerService.DEFAULT_PORT}/files/$id"
                }
                val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("FileBridge", text))
                _toastMessage.value = "已复制: $text"
            } catch (e: Exception) {
                Log.e(TAG, "Copy failed", e)
                _toastMessage.value = "复制失败: ${e.message}"
            }
        }
    }

    fun startHttpServer(port: Int = HttpServerService.DEFAULT_PORT) {
        try {
            val intent = Intent(application, HttpServerService::class.java).apply {
                putExtra("port", port)
            }
            application.startForegroundService(intent)
            _serverRunning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server", e)
            _toastMessage.value = "HTTP服务启动失败: ${e.message}"
            _serverRunning.value = false
        }
    }

    fun stopHttpServer() {
        try {
            val intent = Intent(application, HttpServerService::class.java)
            application.stopService(intent)
            _serverRunning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop HTTP server", e)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    enum class CopyFormat {
        NUMBER, PATH, HTTP_URL
    }
}
