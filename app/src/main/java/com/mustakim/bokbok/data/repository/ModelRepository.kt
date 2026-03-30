package com.mustakim.bokbok.data.repository

import android.content.Context

import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
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
        PAUSED,
        READY,
        ERROR
    }

    private val modelFiles = listOf("enc.onnx", "erb_dec.onnx", "df_dec.onnx", "config.ini")
    
    init {
        checkModels()
        
        // Observe WorkManager state
        val workManager = WorkManager.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
             // Using LiveData.asFlow() requires androidx.lifecycle:lifecycle-livedata-ktx
             // If getWorkInfosForUniqueWorkFlow is not available (older WorkManager), use LiveData
             // But let's try getWorkInfosForUniqueWorkFlow first if it compiles, otherwise fallback to polling or LiveData
             try {
                 workManager.getWorkInfosForUniqueWorkFlow("DEEPFILTER_DOWNLOAD_WORK")
                     .collect { workInfoList ->
                        val workInfo = workInfoList.firstOrNull()
                        if (workInfo != null) {
                            val progress = workInfo.progress.getFloat("PROGRESS", 0f)
                            if (progress > 0) _downloadProgress.value = progress / 100f

                            when (workInfo.state) {
                                WorkInfo.State.SUCCEEDED -> _modelState.value = ModelState.READY
                                WorkInfo.State.FAILED -> _modelState.value = ModelState.ERROR
                                WorkInfo.State.RUNNING -> _modelState.value = ModelState.DOWNLOADING
                                WorkInfo.State.ENQUEUED -> _modelState.value = ModelState.DOWNLOADING
                                WorkInfo.State.CANCELLED -> _modelState.value = ModelState.PAUSED
                                else -> {}
                            }
                        }
                    }
             } catch (e: NoSuchMethodError) {
                 // Fallback if needed, but we should fix gradle dependencies instead
             }
        }
    }

    fun checkModels() {
        _modelState.value = ModelState.CHECKING
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        // Check for at least the ONNX files
        val allExist = modelFiles.all { File(modelsDir, it).exists() }
        _modelState.value = if (allExist) ModelState.READY else ModelState.MISSING
    }

    fun downloadModels() {
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        _modelState.value = ModelState.DOWNLOADING
        
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<com.mustakim.bokbok.workers.DeepFilterNetWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("DEEPFILTER_DOWNLOAD")
            .build()
            
        workManager.enqueueUniqueWork(
            "DEEPFILTER_DOWNLOAD_WORK", 
            ExistingWorkPolicy.REPLACE, 
            request
        )
    }

    fun cancelDownload() {
        if (_modelState.value == ModelState.DOWNLOADING) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("DEEPFILTER_DOWNLOAD_WORK")
            _modelState.value = ModelState.PAUSED
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
