package com.filebridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.filebridge.data.db.FileDao
import com.filebridge.data.repository.FileRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class HttpServerService : Service() {

    @Inject lateinit var repository: FileRepository
    @Inject lateinit var fileDao: FileDao

    private var server: ApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val TAG = "HttpServerService"
        const val CHANNEL_ID = "filebridge_http"
        const val NOTIFICATION_ID = 1
        const val DEFAULT_PORT = 8765
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", DEFAULT_PORT) ?: DEFAULT_PORT
        try {
            startServer(port)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            server?.stop(1000, 5000)
            scope.cancel()
            isRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        super.onDestroy()
    }

    private fun startServer(port: Int) {
        if (server != null) return

        try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }

                routing {
                    get("/health") {
                        call.respond(mapOf("status" to "ok"))
                    }

                    get("/files") {
                        try {
                            val files = fileDao.getAllFiles().first()
                            val response = files.map { file ->
                                FileResponse(
                                    id = file.id,
                                    fileName = file.fileName,
                                    fileSize = file.fileSize,
                                    mimeType = file.mimeType,
                                    termuxPath = file.filePath
                                )
                            }
                            call.respond(response)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error listing files", e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                        }
                    }

                    get("/files/{id}") {
                        try {
                            val id = call.parameters["id"]?.toIntOrNull()
                            if (id == null) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                                return@get
                            }
                            val file = repository.getFileById(id)
                            if (file == null) {
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
                                return@get
                            }
                            call.respondFile(File(file.filePath))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting file", e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                        }
                    }

                    get("/files/{id}/info") {
                        try {
                            val id = call.parameters["id"]?.toIntOrNull()
                            if (id == null) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                                return@get
                            }
                            val file = repository.getFileById(id)
                            if (file == null) {
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
                                return@get
                            }
                            call.respond(
                                FileInfoResponse(
                                    id = file.id,
                                    fileName = file.fileName,
                                    fileSize = file.fileSize,
                                    mimeType = file.mimeType,
                                    termuxPath = file.filePath,
                                    createdAt = file.createdAt
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting file info", e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                        }
                    }

                    delete("/files/{id}") {
                        try {
                            val id = call.parameters["id"]?.toIntOrNull()
                            if (id == null) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                                return@delete
                            }
                            val success = repository.deleteFile(id)
                            if (success) {
                                call.respond(mapOf("success" to true))
                            } else {
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting file", e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                        }
                    }

                    post("/upload") {
                        try {
                            val multipart = call.receiveMultipart()
                            var uploadedId: Int? = null

                            multipart.forEachPart { part ->
                                if (part is PartData.FileItem) {
                                    val fileBytes = part.streamProvider().readBytes()
                                    val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}")
                                    tempFile.writeBytes(fileBytes)

                                    val result = repository.uploadFile(android.net.Uri.fromFile(tempFile))
                                    result.onSuccess { file ->
                                        uploadedId = file.id
                                    }
                                    tempFile.delete()
                                }
                                part.dispose()
                            }

                            if (uploadedId != null) {
                                val file = repository.getFileById(uploadedId!!)
                                call.respond(
                                    HttpStatusCode.Created,
                                    UploadResponse(
                                        id = uploadedId!!,
                                        fileName = file?.fileName ?: "",
                                        termuxPath = file?.filePath ?: ""
                                    )
                                )
                            } else {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error uploading file", e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                        }
                    }
                }
            }.also {
                it.start(wait = false)
                isRunning = true
                Log.i(TAG, "HTTP server started on port $port")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/start server", e)
            isRunning = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FileBridge HTTP Server",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FileBridge")
            .setContentText("HTTP server running on port $DEFAULT_PORT")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Serializable
    data class FileResponse(
        val id: Int,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val termuxPath: String
    )

    @Serializable
    data class FileInfoResponse(
        val id: Int,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val termuxPath: String,
        val createdAt: Long
    )

    @Serializable
    data class UploadResponse(
        val id: Int,
        val fileName: String,
        val termuxPath: String
    )
}
