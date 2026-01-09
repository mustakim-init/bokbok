package com.mustakim.bokbok.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir = File(context.filesDir, "deepfilternet")
    
    private val _modelState = MutableStateFlow(ModelState.CHECKING)
    val modelState = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    enum class ModelState {
        CHECKING,
        MISSING,
        DOWNLOADING,
        READY,
        ERROR
    }

    private val modelFiles = listOf("enc.onnx", "erb_dec.onnx", "df_dec.onnx", "config.ini")
    private val baseUrl = "https://raw.githubusercontent.com/mustakim-init/bokbok-nrm/master/"

    init {
        checkModels()
    }

    fun checkModels() {
        _modelState.value = ModelState.CHECKING
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val allExist = modelFiles.all { File(modelsDir, it).exists() }
        _modelState.value = if (allExist) ModelState.READY else ModelState.MISSING
    }

    suspend fun downloadModels() {
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        _modelState.value = ModelState.DOWNLOADING
        _downloadProgress.value = 0f
        
        try {
            withContext(Dispatchers.IO) {
                val totalFiles = modelFiles.size
                var completedFiles = 0

                for (fileName in modelFiles) {
                    val file = File(modelsDir, fileName)
                    // Skip if already exists (simple check, maybe add hash verif later)
                    if (file.exists()) {
                        completedFiles++
                        _downloadProgress.value = completedFiles.toFloat() / totalFiles
                        continue
                    }

                    val url = URL("$baseUrl$fileName")
                    val connection = url.openConnection()
                    connection.connect()

                    val input = BufferedInputStream(url.openStream(), 8192)
                    val output = FileOutputStream(file)

                    val data = ByteArray(1024)
                    var count: Int
                    
                    // Note: This simple loop doesn't track bytes per file for total progress perfectly 
                    // but assumes files are roughly similar or just tracks file counts. 
                    // Better: track bytes if content-length is available.
                    
                    while (input.read(data).also { count = it } != -1) {
                        output.write(data, 0, count)
                    }

                    output.flush()
                    output.close()
                    input.close()
                    
                    completedFiles++
                    _downloadProgress.value = completedFiles.toFloat() / totalFiles
                }
            }
            _modelState.value = ModelState.READY
        } catch (e: Exception) {
            e.printStackTrace()
            _modelState.value = ModelState.ERROR
            // Cleanup partials?
        }
    }

    fun deleteModels() {
        try {
            modelsDir.deleteRecursively()
            checkModels()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getModelDirectory(): String {
        return modelsDir.absolutePath
    }
}
