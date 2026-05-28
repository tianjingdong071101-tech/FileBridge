package com.filebridge.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val files: StateFlow<List<UploadedFile>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            repository.uploadFile(uri)
                .onSuccess { file ->
                    _toastMessage.value = "已上传: #${file.id} ${file.fileName}"
                }
                .onFailure { e ->
                    _toastMessage.value = "上传失败: ${e.message}"
                }
        }
    }

    fun deleteFile(id: Int) {
        viewModelScope.launch {
            repository.deleteFile(id)
            _toastMessage.value = "已删除: #$id"
        }
    }

    fun copyToClipboard(id: Int, format: CopyFormat = CopyFormat.NUMBER) {
        viewModelScope.launch {
            val file = repository.getFileById(id) ?: return@launch
            val text = when (format) {
                CopyFormat.NUMBER -> "#$id"
                CopyFormat.PATH -> file.filePath
                CopyFormat.HTTP_URL -> "http://localhost:${HttpServerService.DEFAULT_PORT}/files/$id"
            }
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FileBridge", text))
            _toastMessage.value = "已复制: $text"
        }
    }

    fun startHttpServer(port: Int = HttpServerService.DEFAULT_PORT) {
        val intent = Intent(application, HttpServerService::class.java).apply {
            putExtra("port", port)
        }
        application.startForegroundService(intent)
        _serverRunning.value = true
    }

    fun stopHttpServer() {
        val intent = Intent(application, HttpServerService::class.java)
        application.stopService(intent)
        _serverRunning.value = false
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    enum class CopyFormat {
        NUMBER, PATH, HTTP_URL
    }
}
