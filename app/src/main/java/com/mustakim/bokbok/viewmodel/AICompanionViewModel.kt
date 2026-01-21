package com.mustakim.bokbok.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.media.VoiceManager
import com.mustakim.bokbok.data.model.AIMessage
import com.mustakim.bokbok.data.model.AISession
import com.mustakim.bokbok.data.model.MessageRole
import com.mustakim.bokbok.data.repository.AIRepository
import com.mustakim.bokbok.util.ModelDownloader
import com.mustakim.bokbok.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.mustakim.bokbok.data.service.BokBokAgentService
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AICompanionViewModel @Inject constructor(
    private val repository: AIRepository,
    private val voiceManager: VoiceManager,
    private val modelDownloader: ModelDownloader,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Dynamic session management
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _sessions = MutableStateFlow<List<AISession>>(emptyList())
    val sessions: StateFlow<List<AISession>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<List<AIMessage>?>(null)
    val messages: StateFlow<List<AIMessage>?> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _uiState = MutableStateFlow<CompanionUiState>(CompanionUiState.Idle)
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private val _isVoiceModeEnabled = MutableStateFlow(false)
    val isVoiceModeEnabled: StateFlow<Boolean> = _isVoiceModeEnabled.asStateFlow()

    private val _ttsMode = MutableStateFlow(TtsMode.LEGACY)
    val ttsMode: StateFlow<TtsMode> = _ttsMode.asStateFlow()

    val isListening = voiceManager.isRecording
    val isSpeaking = voiceManager.isSpeaking
    val amplitude = voiceManager.amplitude

    private val _selectedImage = MutableStateFlow<Uri?>(null)
    val selectedImage: StateFlow<Uri?> = _selectedImage.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()
    
    private val _showOverlayPermissionDialog = MutableStateFlow(false)
    val showOverlayPermissionDialog: StateFlow<Boolean> = _showOverlayPermissionDialog.asStateFlow()
    
    val downloadedLanguages = preferencesManager.downloadedLanguages

    fun downloadLanguage(langCode: String) {
        modelDownloader.downloadPremiumTTS(langCode)
    }

    fun isLanguageDownloaded(langCode: String): Boolean {
        return modelDownloader.isModelDownloaded(langCode)
    }

    fun getDownloadStatus(langCode: String) = modelDownloader.getDownloadStatus(langCode)

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
        _showOverlayPermissionDialog.value = false
    }

    fun toggleVoiceMode() {
        if (_isVoiceModeEnabled.value) {
            _isVoiceModeEnabled.value = false
            com.mustakim.bokbok.data.service.BokBokAgentService.getInstance()?.exitInteractiveMode()
        } else {
            checkAndEnableVoiceMode()
        }
    }

    private fun checkAndEnableVoiceMode() {
        // 1. Check Overlay Permission first (critical for visuals)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && 
            !android.provider.Settings.canDrawOverlays(context)) {
            _showOverlayPermissionDialog.value = true
            return
        }

        // 2. Check Accessibility Service
        if (com.mustakim.bokbok.data.service.BokBokAgentService.isServiceEnabled.value) {
            enableVoice()
        } else {
            viewModelScope.launch {
                // Try Shizuku first, fallback to manual prompt
                if (com.mustakim.bokbok.util.ShizukuUtils.enableAccessibilityService()) {
                    enableVoice()
                } else {
                    _showPermissionDialog.value = true
                }
            }
        }
    }

    private fun enableVoice() {
        _isVoiceModeEnabled.value = true
        com.mustakim.bokbok.data.service.BokBokAgentService.getInstance()?.enterInteractiveMode()
    }

    fun setTtsMode(mode: TtsMode) {
        if (mode == TtsMode.QUALITY && !modelDownloader.isModelDownloaded("en")) {
            modelDownloader.downloadPremiumTTS("en")
        }
        _ttsMode.value = mode
        viewModelScope.launch {
            preferencesManager.saveAiTtsMode(mode.name)
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun selectImage(uri: Uri?) {
        _selectedImage.value = uri
    }

    fun clearImage() {
        _selectedImage.value = null
    }


    init {
        observeSessions()
        
        // Sync voice mode state with Service
        viewModelScope.launch {
            BokBokAgentService.isInteractiveModeEnabled.collect { enabled ->
                _isVoiceModeEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferencesManager.aiTtsMode.collect { modeName ->
                try {
                    _ttsMode.value = TtsMode.valueOf(modeName)
                } catch (e: Exception) {
                    _ttsMode.value = TtsMode.LEGACY
                }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.getAllSessions().collect {
                _sessions.value = it
            }
        }
    }

    companion object {
        private var hasCreatedInitialSession = false
        private var lastActiveSessionId: String? = null
    }

    fun onScreenVisible() {
        if (!hasCreatedInitialSession) {
            // First time entering the module in this app lifecycle
            viewModelScope.launch {
                val sessionsList = repository.getAllSessions().first()
                if (sessionsList.isNotEmpty()) {
                    val latest = sessionsList.first()
                    val count = repository.getMessageCount(latest.id)
                    if (count == 0) {
                        selectSession(latest.id)
                    } else {
                        createNewSession()
                    }
                } else {
                    createNewSession()
                }
                hasCreatedInitialSession = true
            }
        } else if (_currentSessionId.value == null) {
            // Re-navigating and no session selected
            if (lastActiveSessionId != null) {
                selectSession(lastActiveSessionId!!)
            } else if (_sessions.value.isNotEmpty()) {
                selectSession(_sessions.value.first().id)
            } else {
                createNewSession()
            }
        }
    }

    private fun createNewSession() {
        // Guard: Don't create if current one is already empty/new
        val currentMsgs = _messages.value
        if (currentMsgs != null && currentMsgs.isEmpty() && _currentSessionId.value != null) {
            return
        }

        viewModelScope.launch {
            val newSessionId = UUID.randomUUID().toString()
            val newSession = AISession(
                id = newSessionId,
                title = "New Chat",
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertSession(newSession)
            _currentSessionId.value = newSessionId
            lastActiveSessionId = newSessionId
            loadMessages(newSessionId)
        }
    }

    fun selectSession(sessionId: String) {
        if (_currentSessionId.value == sessionId) return
        _currentSessionId.value = sessionId
        lastActiveSessionId = sessionId
        loadMessages(sessionId)
    }

    fun startNewChat() {
        // Explicitly check if current session is empty
        val currentMsgs = _messages.value
        if (currentMsgs != null && currentMsgs.isEmpty()) {
            // Already in an empty chat, just stay here
            return
        }
        createNewSession()
    }

    private var messageJob: Job? = null
    private fun loadMessages(sessionId: String) {
        messageJob?.cancel()
        _messages.value = null // Show loading
        messageJob = viewModelScope.launch {
            repository.getConversationHistory(sessionId).collect {
                _messages.value = it
            }
        }
    }

    private var sendJob: Job? = null
    fun sendMessage() {
        val prompt = _inputText.value
        val sessionId = _currentSessionId.value ?: return
        if (prompt.isBlank() && selectedImage.value == null) return
        
        // Prevent concurrent sends
        if (sendJob?.isActive == true) {
            android.util.Log.d("AIViewModel", "SendMessage ignored: already sending.")
            return
        }

        sendJob = viewModelScope.launch {
            val imageUri = selectedImage.value
            _inputText.value = ""
            _selectedImage.value = null
            
            // If this is the first message, update session title
            if (_messages.value.isNullOrEmpty()) {
                val title = if (prompt.length > 30) prompt.take(27) + "..." else prompt
                repository.updateSessionTitle(sessionId, title)
            }

            val userMessage = AIMessage(
                conversationId = sessionId,
                role = MessageRole.USER,
                content = prompt,
                imageUri = imageUri?.toString()
            )
            repository.insertMessage(userMessage)
            repository.updateSessionTimestamp(sessionId, System.currentTimeMillis())

            _uiState.value = CompanionUiState.Generating
            var fullResponse = ""
            
            val history = _messages.value ?: emptyList()
            try {
                repository.sendMessageStream(prompt, sessionId, history, imageUri).collect { chunk ->
                    if (_uiState.value !is CompanionUiState.Streaming) {
                        _uiState.value = CompanionUiState.Streaming("")
                    }
                    fullResponse += chunk
                    _uiState.value = CompanionUiState.Streaming(fullResponse)
                }

                val aiMessage = AIMessage(
                    conversationId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = fullResponse
                )
                repository.insertMessage(aiMessage)
                repository.updateSessionTimestamp(sessionId, System.currentTimeMillis())
                _uiState.value = CompanionUiState.Idle
                
                // Automatic voice response
                if (_isVoiceModeEnabled.value) {
                    speakResponse(fullResponse)
                }
            } catch (e: Exception) {
                _uiState.value = CompanionUiState.Error(e.message ?: "Unknown error")
            }
        }
    }


    fun startVoiceInput() {
        if (!voiceManager.isRecording.value) {
            voiceManager.startManualRecording()
        } else {
             stopVoiceInput()
        }
    }

    private fun stopVoiceInput() {
         voiceManager.stopManualRecording { transcription ->
            if (transcription.isNotEmpty()) {
                onInputChange(_inputText.value + (if (_inputText.value.isNotEmpty()) " " else "") + transcription)
                sendMessage() // Auto-send on voice input completion
            }
        }
    }

    fun speakResponse(text: String) {
        voiceManager.speak(text, useQualityTts = _ttsMode.value == TtsMode.QUALITY)
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    override fun onCleared() {
        super.onCleared()
        // Do not destroy VoiceManager here, it is a Singleton used by the Foreground Service.
    }

    enum class TtsMode { LEGACY, QUALITY }
    
    fun clearConversation() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            repository.clearMessagesForSession(sessionId)
            _messages.value = emptyList()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            repository.clearMessagesForSession(sessionId)
            if (lastActiveSessionId == sessionId) {
                lastActiveSessionId = null
            }
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                _messages.value = null
                onScreenVisible()
            }
        }
    }
}
