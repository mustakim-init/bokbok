package com.mustakim.bokbok.data.media

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import com.mustakim.bokbok.data.api.GroqApi
import com.k2fsa.sherpa.onnx.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import android.media.AudioTrack
import android.media.AudioManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groqApi: GroqApi
) : TextToSpeech.OnInitListener {

    companion object {
        init {
            try {
                // Explicitly load onnxruntime first to ensure symbols are available
                System.loadLibrary("onnxruntime")
                System.loadLibrary("sherpa-onnx-jni")
            } catch (t: Throwable) {
                android.util.Log.e("VoiceManager", "Failed to load native libraries", t)
            }
        }
    }

    private var tts: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val ttsEngines = mutableMapOf<String, OfflineTts>()
    private var audioTrack: AudioTrack? = null
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    // Manual Recording State
    private var outputStream: FileOutputStream? = null
    private var tempAudioFile: File? = null
    private var totalBytesWritten = 0
    private var ambientJob: Job? = null
    private var synthesisJob: Job? = null
    
    // VAD Parameters (removed, but sampleRate and bufferSize are still needed)
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    init {
        tts = TextToSpeech(context, this)
        // We will load engines on-demand to save memory
    }


    private fun findFile(dir: File, suffix: String): File? {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findFile(file, suffix)
                if (found != null) return found
            } else if (file.name.endsWith(suffix)) {
                return file
            }
        }
        return null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            _isTtsReady.value = true
        }
    }

    fun speak(text: String, useQualityTts: Boolean = false) {
        if (useQualityTts) {
            speakQuality(text)
        } else {
            speakLegacy(text)
        }
    }

    private fun detectLanguage(text: String): String {
        val banglaRegex = Regex("[\u0980-\u09FF]+")
        return if (banglaRegex.containsMatchIn(text)) "bn" else "en"
    }

    private fun splitIntoSegments(text: String): List<Pair<String, String>> {
        val segments = mutableListOf<Pair<String, String>>()
        if (text.isEmpty()) return segments

        val banglaRegex = Regex("[\u0980-\u09FF]+")
        var currentStart = 0
        var isCurrentlyBangla = banglaRegex.containsMatchIn(text.substring(0, 1))

        for (i in 1 until text.length) {
            val char = text.substring(i, i + 1)
            val isBangla = banglaRegex.containsMatchIn(char)
            if (char[0].isWhitespace() || char.all { !it.isLetterOrDigit() }) continue // Skip boundary check for non-letters

            if (isBangla != isCurrentlyBangla) {
                segments.add(text.substring(currentStart, i) to if (isCurrentlyBangla) "bn" else "en")
                currentStart = i
                isCurrentlyBangla = isBangla
            }
        }
        segments.add(text.substring(currentStart) to if (isCurrentlyBangla) "bn" else "en")
        return segments
    }

    private fun cleanText(text: String, lang: String): String {
        return when (lang) {
            "bn" -> {
                // Convert English digits to Bangla digits
                var cleaned = text.replace(Regex("[0-9]")) { match ->
                    when (match.value) {
                        "0" -> "০"; "1" -> "১"; "2" -> "২"; "3" -> "৩"; "4" -> "৪"
                        "5" -> "৫"; "6" -> "৬"; "7" -> "৭"; "8" -> "৮"; "9" -> "৯"
                        else -> match.value
                    }
                }
                // Strip English letters from Bangla segments
                cleaned.replace(Regex("[a-zA-Z]"), "")
            }
            "en" -> {
                // Strip Bangla from English segments
                text.replace(Regex("[\u0980-\u09FF]"), "")
            }
            else -> text
        }
    }

    private fun speakQuality(text: String) {
        synthesisJob?.cancel()
        synthesisJob = serviceScope.launch(Dispatchers.Default) {
            _isSpeaking.value = true
            try {
                val segments = splitIntoSegments(text)
                for ((rawSegment, lang) in segments) {
                    val engine = getOrLoadEngine(lang) ?: continue
                    
                    // Split by sentences for streaming (Bangla uses । (0964) as full stop)
                    val sentences = rawSegment.split(Regex("(?<=[.!?।])\\s+"))
                    for (sentence in sentences) {
                        if (!isActive) break
                        val cleaned = cleanText(sentence, lang).trim()
                        if (cleaned.isBlank()) continue
                        
                        try {
                            val audio = engine.generate(cleaned)
                            if (audio != null && isActive) {
                                playAudioStream(audio.samples, audio.sampleRate)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceManager", "Sherpa generate failed", e)
                        }
                    }
                }
            } finally {
                _isSpeaking.value = false
                _amplitude.value = 0f
            }
        }
    }

    private suspend fun getOrLoadEngine(lang: String): OfflineTts? = withContext(Dispatchers.IO) {
        ttsEngines[lang]?.let { return@withContext it }
        
        val langDir = File(File(context.filesDir, "tts_models"), lang)
        if (!langDir.exists()) return@withContext null

        // Flexible discovery for different model types (Piper, Mimic3, etc.)
        val modelFile = findFile(langDir, ".onnx")
        val tokensFile = findFile(langDir, "tokens.txt") ?: findFile(langDir, "vits-tokens.txt")
        val lexiconFile = findFile(langDir, "lexicon.txt") ?: findFile(langDir, "vits-lexicon.txt")

        android.util.Log.d("VoiceManager", "Loading $lang model: onnx=${modelFile?.name}, tokens=${tokensFile?.name}, lexicon=${lexiconFile?.name}")

        if (modelFile != null && tokensFile != null) {
            val config = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokensFile.absolutePath,
                lexicon = lexiconFile?.absolutePath ?: "",
                dataDir = modelFile.parentFile.absolutePath, // Points to the specific model folder (Piper/Mimic3)
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )
            val ttsConfig = OfflineTtsModelConfig(vits = config, numThreads = 1)
            val offlineTtsConfig = OfflineTtsConfig(model = ttsConfig, maxNumSentences = 1)
            
            try {
                // SHERPA-ONNX: Pass null for assetManager when model is on the filesystem
                val engine = OfflineTts(null, offlineTtsConfig)
                ttsEngines[lang] = engine
                engine
            } catch (t: Throwable) {
                android.util.Log.e("VoiceManager", "Failed to init Sherpa TTS for $lang", t)
                null
            }
        } else null
    }


    private fun playAudioStream(samples: FloatArray, sampleRate: Int) {
        if (audioTrack == null || audioTrack?.sampleRate != sampleRate) {
            audioTrack?.release()
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(android.media.AudioAttributes.FLAG_LOW_LATENCY)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, samples.size * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
        }

        audioTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        
        // Update visualizer
        val peak = samples.maxOrNull()?.let { Math.abs(it) } ?: 0f
        _amplitude.value = peak * 1000
    }

    private fun speakLegacy(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun stopSpeaking() {
        synthesisJob?.cancel()
        tts?.stop()
        audioTrack?.stop()
        audioTrack?.flush()
        _isSpeaking.value = false
        _amplitude.value = 0f
    }

    fun startAmbientListening() {
        if (ambientJob?.isActive == true) return
        
        ambientJob = serviceScope.launch(Dispatchers.IO) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("VoiceManager", "Microphone permission not granted for ambient listening")
                return@launch
            }
            
            val buffer = ShortArray(bufferSize)
            var recorder: AudioRecord? = null
            
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                recorder.startRecording()
                
                while (isActive) {
                    val read = recorder.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        // Calculate RMS for Edge Glow
                        val rms = calculateRMS(buffer, read)
                        // If speaking (TTS), merge that amplitude? 
                        // Actually, if using AEC or just mic, mic will pick up speaker too. 
                        // But let's just use mic input for visualizer for now.
                        // If system feels disjointed, we can merge TTS amplitude logic later.
                        
                        // BUT: If TTS is playing, we want visualizer to show it.
                        // Ideally we'd mix `ttsAmplitude` and `micAmplitude`.
                        // For now, let's let mic pick it up (simplest).
                        
                        if (!_isSpeaking.value) {
                             _amplitude.value = rms.toFloat()
                        }
                        
                        // If manually recording, write to file
                        if (_isRecording.value) {
                            outputStream?.let { stream ->
                                for (i in 0 until read) {
                                    val shortVal = buffer[i]
                                    stream.write(shortVal.toInt() and 0xFF)
                                    stream.write((shortVal.toInt() shr 8) and 0xFF)
                                }
                                totalBytesWritten += read * 2
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceManager", "Ambient loop failed", e)
            } finally {
                recorder?.stop()
                recorder?.release()
                if (!_isSpeaking.value) { // Only reset if TTS isn't active
                    _amplitude.value = 0f
                }
            }
        }
    }

    fun stopAmbientListening() {
        ambientJob?.cancel()
        ambientJob = null
        _amplitude.value = 0f
    }

    fun startManualRecording() {
        if (_isRecording.value) return
        
        tempAudioFile = File(context.cacheDir, "voice_input.wav")
        try {
            outputStream = FileOutputStream(tempAudioFile)
            outputStream?.write(ByteArray(44)) // Reserve header
            totalBytesWritten = 0
            _isRecording.value = true
        } catch (e: Exception) {
            android.util.Log.e("VoiceManager", "Failed to start recording", e)
        }
    }

    fun stopManualRecording(onTranscription: (String) -> Unit) {
        if (!_isRecording.value) return
        _isRecording.value = false
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                outputStream?.close()
                outputStream = null
                
                tempAudioFile?.let { file ->
                    if (totalBytesWritten > 0) {
                        writeWavHeader(file, totalBytesWritten)
                        if (file.length() > 44) {
                            val transcription = transcribe(file)
                            withContext(Dispatchers.Main) {
                                onTranscription(transcription)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceManager", "Failed to stop recording", e)
            }
        }
    }

    private fun writeWavHeader(file: File, pcmDataSize: Int) {
        val totalSize = 36 + pcmDataSize
        val byteBuffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(totalSize)
        byteBuffer.put("WAVE".toByteArray())
        
        // fmt chunk
        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16) // Subchunk1Size (16 for PCM)
        byteBuffer.putShort(1.toShort()) // AudioFormat (1 for PCM)
        byteBuffer.putShort(1.toShort()) // NumChannels
        byteBuffer.putInt(sampleRate) // SampleRate
        byteBuffer.putInt(sampleRate * 2) // ByteRate
        byteBuffer.putShort(2.toShort()) // BlockAlign
        byteBuffer.putShort(16.toShort()) // BitsPerSample
        
        // data chunk
        byteBuffer.put("data".toByteArray())
        byteBuffer.putInt(pcmDataSize)
        
        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(byteBuffer.array())
        raf.close()
    }

    private fun calculateRMS(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / read)
    }

    private suspend fun transcribe(file: File): String {
        return try {
            val requestFile = file.asRequestBody("audio/wav".toMediaType())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            // Pass null for language to enable auto-detection
            val response = groqApi.transcribeAudio(file = body, language = null)
            
            if (response.isSuccessful) {
                response.body()?.text ?: ""
            } else {
                android.util.Log.e("VoiceManager", "Groq error: ${response.errorBody()?.string()}")
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceManager", "Transcription failed", e)
            ""
        }
    }

    fun onDestroy() {
        tts?.shutdown()
        ttsEngines.values.forEach { it.release() }
        ttsEngines.clear()
        serviceJob.cancel()
        audioTrack?.release()
    }
}
