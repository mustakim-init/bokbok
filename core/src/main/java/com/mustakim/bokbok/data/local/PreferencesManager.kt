package com.mustakim.bokbok.data.local
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mustakim.bokbok.ui.theme.AppTheme
import com.mustakim.bokbok.ui.theme.DarkMode
import com.mustakim.bokbok.model.RecordingProfile
import com.mustakim.bokbok.model.RecordConfig
import com.mustakim.bokbok.model.CustomRecordingProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
 
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _prefs = MutableStateFlow<Preferences?>(null)
    val prefs = _prefs.asStateFlow()

    init {
        scope.launch {
            context.dataStore.data.collect { preferences ->
                _prefs.value = preferences
            }
        }
    }

    /**
     * Resilient, non-blocking read from memory cache.
     * If called from a non-main thread during early startup (cache empty), 
     * it falls back to a timed blocking read to prevent race conditions.
     */
    fun <T> getImmediate(key: Preferences.Key<T>): T? {
        val cached = _prefs.value?.get(key)
        if (cached != null) return cached
        
        // Fallback for background threads during early cold start
        if (android.os.Looper.getMainLooper().thread != Thread.currentThread()) {
            return kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(1000) {
                    context.dataStore.data.first()[key]
                }
            }
        }
        return null
    }


    companion object {
        val THEME_KEY = stringPreferencesKey("selected_theme")
        private val SPEAKER_ON_KEY = booleanPreferencesKey("speaker_on")
        private val A2DP_MODE_KEY = booleanPreferencesKey("a2dp_mode")
        private val HIGH_QUALITY_KEY = booleanPreferencesKey("high_quality")
        private val BITRATE_KEY = intPreferencesKey("voice_bitrate")
        private val STEREO_KEY = booleanPreferencesKey("voice_stereo")
        private val MIC_VOLUME_KEY = floatPreferencesKey("mic_volume")
        private val OUTPUT_VOLUME_KEY = floatPreferencesKey("output_volume")
        
        // Recorder Settings
        private val AUTO_PROCESS_KEY = booleanPreferencesKey("recorder_auto_process")
        private val NOISE_REDUCTION_KEY = booleanPreferencesKey("recorder_noise_reduction")
        private val BLEED_REDUCTION_KEY = booleanPreferencesKey("recorder_bleed_reduction")
        private val QUALITY_MODE_KEY = intPreferencesKey("recorder_quality_mode")
        private val STUDIO_MASTER_KEY = booleanPreferencesKey("recorder_studio_master")
        
        // Capture Settings
        private val REC_WIDTH_KEY = intPreferencesKey("rec_width")
        private val REC_HEIGHT_KEY = intPreferencesKey("rec_height")
        private val REC_RES_NAME_KEY = stringPreferencesKey("rec_res_name")
        private val REC_BITRATE_KEY = intPreferencesKey("rec_bitrate")
        private val REC_BITRATE_NAME_KEY = stringPreferencesKey("rec_bitrate_name")
        private val REC_FPS_KEY = intPreferencesKey("rec_fps")
        private val REC_USE_HEVC_KEY = booleanPreferencesKey("rec_use_hevc")
        private val REC_INC_MIC_KEY = booleanPreferencesKey("rec_inc_mic")
        private val REC_INC_INT_KEY = booleanPreferencesKey("rec_inc_int")
        private val REC_USE_COUNTDOWN_KEY = booleanPreferencesKey("rec_use_countdown")
        private val REC_SHOW_FACECAM_KEY = booleanPreferencesKey("rec_show_facecam")
        private val REC_EXPORT_MIC_KEY = booleanPreferencesKey("rec_export_mic")
        private val REC_EXPORT_INTERNAL_KEY = booleanPreferencesKey("rec_export_internal")
        
        private val AUTO_STOP_DURATION_KEY = intPreferencesKey("auto_stop_duration")
        private val AUTO_STOP_BATTERY_KEY = intPreferencesKey("auto_stop_battery")
        private val REC_PROFILE_KEY = stringPreferencesKey("rec_profile")
        private val USE_WATERMARK_TEXT_KEY = booleanPreferencesKey("use_watermark_text")
        private val WATERMARK_TEXT_KEY = stringPreferencesKey("watermark_text")
        private val USE_WATERMARK_IMAGE_KEY = booleanPreferencesKey("use_watermark_image")
        private val WATERMARK_IMAGE_PATH_KEY = stringPreferencesKey("watermark_image_path")
        private val STOP_ON_SCREEN_OFF_KEY = booleanPreferencesKey("stop_on_screen_off")
        private val STOP_ON_SHAKE_KEY = booleanPreferencesKey("stop_on_shake")
        private val AUTO_LAUNCH_PACKAGE_KEY = stringPreferencesKey("auto_launch_package")
        private val SET_VOLUME_ON_START_KEY = booleanPreferencesKey("set_volume_on_start")
        private val START_VOLUME_LEVEL_KEY = intPreferencesKey("start_volume_level")
        private val SHOW_TOUCHES_KEY = booleanPreferencesKey("show_touches")
        private val ORIENTATION_LOCK_KEY = stringPreferencesKey("orientation_lock")
        private val FACECAM_SHAPE_KEY = stringPreferencesKey("facecam_shape")
        private val AUDIO_SAMPLE_RATE_KEY = intPreferencesKey("audio_sample_rate")
        private val AUDIO_BITRATE_KEY = intPreferencesKey("audio_bitrate")
        private val SHAKE_SENSITIVITY_KEY = floatPreferencesKey("shake_sensitivity")
        
        // Overlay Customization
        private val MENU_STYLE_KEY = intPreferencesKey("menu_style")
        private val SHOW_SHORTCUTS_KEY = booleanPreferencesKey("show_shortcuts")
        private val SHORTCUTS_KEY = stringPreferencesKey("shortcuts_list")
        private val MINIMIZING_SIDE_KEY = intPreferencesKey("minimizing_side")
        private val START_MINIMIZED_KEY = booleanPreferencesKey("start_minimized")
        private val SHOW_PAUSE_RESUME_KEY = booleanPreferencesKey("show_pause_resume_menu")
        private val SHOW_CAMERA_BTN_KEY = booleanPreferencesKey("show_camera_btn_menu")
        private val SHOW_DRAW_BTN_KEY = booleanPreferencesKey("show_draw_btn_menu")
        private val SHOW_SCREENSHOT_BTN_KEY = booleanPreferencesKey("show_screenshot_btn_menu")
        private val SHOW_TIME_KEY = booleanPreferencesKey("show_time_menu")
        private val SHOW_TIME_LIMIT_KEY = booleanPreferencesKey("show_time_limit_menu")
        
        // Wi-Fi Sharing
        private val WIFI_SHARE_REQUIRE_PASSWORD_KEY = booleanPreferencesKey("wifi_share_require_password")
        
        private val SELECTED_PROFILE_NAME_KEY = stringPreferencesKey("selected_profile_name")
        
        // AI - Hardware Identity Cache
        private val CACHED_MODEL_NAME_KEY = stringPreferencesKey("ai_cached_model_name")
        private val CACHED_SOC_NAME_KEY = stringPreferencesKey("ai_cached_soc_name")
        private val AI_TTS_MODE_KEY = stringPreferencesKey("ai_tts_mode")

        // Security
        private val VIRUSTOTAL_API_KEY = stringPreferencesKey("virustotal_api_key")

        // Experimental Features
        private val SHOW_EXPERIMENTAL_LAB = booleanPreferencesKey("show_experimental_lab")
        private val WATCHDOG_ENABLED = booleanPreferencesKey("watchdog_enabled")
        private val HEARTBEAT_ENABLED = booleanPreferencesKey("heartbeat_enabled")
        private val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        private val CRASH_REPORT_ENABLED = booleanPreferencesKey("crash_report_enabled")

        // ArchiveTune Expressive Theme
        val THEME_COLOR_KEY = intPreferencesKey("theme_color")
        val PURE_BLACK_KEY = booleanPreferencesKey("pureBlack")
        val THEME_SEED_PALETTE_KEY = stringPreferencesKey("customThemeColor")
        val DARK_MODE_KEY = stringPreferencesKey("darkMode")
        val USE_SYSTEM_FONT_KEY = booleanPreferencesKey("useSystemFont")
        val DISABLE_BLUR_KEY = booleanPreferencesKey("disableBlur")
        val BLUR_RADIUS_KEY = floatPreferencesKey("blurRadius")
        val DYNAMIC_THEME_KEY = booleanPreferencesKey("dynamicTheme")
    }

    val selectedTheme: Flow<AppTheme> = _prefs.filterNotNull().map { preferences ->
        val themeName = preferences[THEME_KEY] ?: AppTheme.MATERIAL_CLASSIC.name
        AppTheme.valueOf(themeName)
    }

    val themeColor: Flow<Int?> = _prefs.filterNotNull().map { it[THEME_COLOR_KEY] }
    val pureBlack: Flow<Boolean> = _prefs.filterNotNull().map { it[PURE_BLACK_KEY] ?: false }
    val themeSeedPalette: Flow<String?> = _prefs.filterNotNull().map { it[THEME_SEED_PALETTE_KEY] }

    val darkMode: Flow<DarkMode> = _prefs.filterNotNull().map { preferences ->
        val modeName = preferences[DARK_MODE_KEY] ?: DarkMode.AUTO.name
        try { DarkMode.valueOf(modeName) } catch (e: Exception) { DarkMode.AUTO }
    }

    val useSystemFont: Flow<Boolean> = _prefs.filterNotNull().map { it[USE_SYSTEM_FONT_KEY] ?: false }
    val disableBlur: Flow<Boolean> = _prefs.filterNotNull().map { it[DISABLE_BLUR_KEY] ?: false }
    val blurRadius: Flow<Float> = _prefs.filterNotNull().map { it[BLUR_RADIUS_KEY] ?: 36f }
    val dynamicThemeEnabled: Flow<Boolean> = _prefs.filterNotNull().map { it[DYNAMIC_THEME_KEY] ?: true }

    val audioSettings: Flow<Map<String, Any>> = _prefs.filterNotNull().map { preferences ->
        mapOf(
            "isSpeakerOn" to (preferences[SPEAKER_ON_KEY] ?: true),
            "isA2dpModeOn" to (preferences[A2DP_MODE_KEY] ?: false),
            "isHighQuality" to (preferences[HIGH_QUALITY_KEY] ?: true),
            "bitrate" to (preferences[BITRATE_KEY] ?: 24),
            "isStereo" to (preferences[STEREO_KEY] ?: false),
            "micVolume" to (preferences[MIC_VOLUME_KEY] ?: 1f),
            "outputVolume" to (preferences[OUTPUT_VOLUME_KEY] ?: 1f)
        )
    }

    val recorderSettings: Flow<Map<String, Any>> = _prefs.filterNotNull().map { preferences ->
        mapOf(
            "autoProcess" to (preferences[AUTO_PROCESS_KEY] ?: true),
            "noiseReduction" to (preferences[NOISE_REDUCTION_KEY] ?: true),
            "bleedReduction" to (preferences[BLEED_REDUCTION_KEY] ?: true),
            "qualityMode" to (preferences[QUALITY_MODE_KEY] ?: 1),
            "studioMaster" to (preferences[STUDIO_MASTER_KEY] ?: false),
            "exportMicOnly" to (preferences[REC_EXPORT_MIC_KEY] ?: false),
            "exportInternalOnly" to (preferences[REC_EXPORT_INTERNAL_KEY] ?: false),
            "autoStopDuration" to (preferences[AUTO_STOP_DURATION_KEY] ?: 0),
            "autoStopBattery" to (preferences[AUTO_STOP_BATTERY_KEY] ?: 0),
            "wifiShareRequirePassword" to (preferences[WIFI_SHARE_REQUIRE_PASSWORD_KEY] ?: false)
        )
    }

    val recordConfig: Flow<RecordConfig> = _prefs.filterNotNull().map { preferences ->
        RecordConfig(
            width = preferences[REC_WIDTH_KEY] ?: 720,
            height = preferences[REC_HEIGHT_KEY] ?: 1280,
            resolutionName = preferences[REC_RES_NAME_KEY] ?: "720p",
            bitrate = preferences[REC_BITRATE_KEY] ?: 8_000_000,
            bitrateName = preferences[REC_BITRATE_NAME_KEY] ?: "8 Mbps",
            fps = preferences[REC_FPS_KEY] ?: 60,
            useHevc = preferences[REC_USE_HEVC_KEY] ?: false,
            includeMic = preferences[REC_INC_MIC_KEY] ?: true,
            includeInternal = preferences[REC_INC_INT_KEY] ?: true,
            useCountdown = preferences[REC_USE_COUNTDOWN_KEY] ?: true,
            showFacecam = preferences[REC_SHOW_FACECAM_KEY] ?: false,
            exportMicOnly = preferences[REC_EXPORT_MIC_KEY] ?: false,
            exportInternalOnly = preferences[REC_EXPORT_INTERNAL_KEY] ?: false,
            autoStopDurationMinutes = preferences[AUTO_STOP_DURATION_KEY] ?: 0,
            autoStopBatteryLevel = preferences[AUTO_STOP_BATTERY_KEY] ?: 0,
            profile = try {
                RecordingProfile.valueOf(preferences[REC_PROFILE_KEY] ?: RecordingProfile.DEFAULT.name)
            } catch (e: Exception) {
                RecordingProfile.DEFAULT
            },
            useWatermarkText = preferences[USE_WATERMARK_TEXT_KEY] ?: false,
            watermarkText = preferences[WATERMARK_TEXT_KEY] ?: "BokBok Screen Recorder",
            watermarkImagePath = preferences[WATERMARK_IMAGE_PATH_KEY] ?: "",
            stopOnScreenOff = preferences[STOP_ON_SCREEN_OFF_KEY] ?: true,
            stopOnShake = preferences[STOP_ON_SHAKE_KEY] ?: false,
            autoLaunchPackage = preferences[AUTO_LAUNCH_PACKAGE_KEY] ?: "",
            setVolumeOnStart = preferences[SET_VOLUME_ON_START_KEY] ?: false,
            startVolumeLevel = preferences[START_VOLUME_LEVEL_KEY] ?: 100,
            showTouches = preferences[SHOW_TOUCHES_KEY] ?: false,
            orientationLock = preferences[ORIENTATION_LOCK_KEY] ?: "Auto",
            facecamShape = preferences[FACECAM_SHAPE_KEY] ?: "Circle",
            audioSampleRate = preferences[AUDIO_SAMPLE_RATE_KEY] ?: 48000,
            audioBitrate = preferences[AUDIO_BITRATE_KEY] ?: 128000,
            shakeSensitivity = preferences[SHAKE_SENSITIVITY_KEY] ?: 20f,
            menuStyle = preferences[MENU_STYLE_KEY] ?: 0,
            showShortcuts = preferences[SHOW_SHORTCUTS_KEY] ?: false,
            shortcuts = preferences[SHORTCUTS_KEY]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            minimizingSide = preferences[MINIMIZING_SIDE_KEY] ?: 0,
            startMinimized = preferences[START_MINIMIZED_KEY] ?: false,
            showPauseResumeOnMenu = preferences[SHOW_PAUSE_RESUME_KEY] ?: true,
            showCameraButtonOnMenu = preferences[SHOW_CAMERA_BTN_KEY] ?: true,
            showDrawButtonOnMenu = preferences[SHOW_DRAW_BTN_KEY] ?: true,
            showScreenshotButtonOnMenu = preferences[SHOW_SCREENSHOT_BTN_KEY] ?: true,
            showTimeOnMenu = preferences[SHOW_TIME_KEY] ?: true,
            showTimeLimitOnMenu = preferences[SHOW_TIME_LIMIT_KEY] ?: false
        )
    }

    suspend fun saveTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    suspend fun saveThemeColor(color: Int) {
        context.dataStore.edit { it[THEME_COLOR_KEY] = color }
    }

    suspend fun savePureBlack(enabled: Boolean) {
        context.dataStore.edit { it[PURE_BLACK_KEY] = enabled }
    }

    suspend fun saveThemeSeedPalette(paletteJson: String?) {
        context.dataStore.edit {
            if (paletteJson == null) it.remove(THEME_SEED_PALETTE_KEY)
            else it[THEME_SEED_PALETTE_KEY] = paletteJson
        }
    }

    suspend fun saveDarkMode(mode: DarkMode) {
        context.dataStore.edit { it[DARK_MODE_KEY] = mode.name }
    }

    suspend fun saveUseSystemFont(enabled: Boolean) {
        context.dataStore.edit { it[USE_SYSTEM_FONT_KEY] = enabled }
    }

    suspend fun saveDisableBlur(enabled: Boolean) {
        context.dataStore.edit { it[DISABLE_BLUR_KEY] = enabled }
    }

    suspend fun saveBlurRadius(radius: Float) {
        context.dataStore.edit { it[BLUR_RADIUS_KEY] = radius }
    }

    suspend fun saveDynamicThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_THEME_KEY] = enabled }
    }

    suspend fun saveRecorderSettings(
        autoProcess: Boolean,
        noiseReduction: Boolean,
        bleedReduction: Boolean,
        qualityMode: Int,
        studioMaster: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PROCESS_KEY] = autoProcess
            preferences[NOISE_REDUCTION_KEY] = noiseReduction
            preferences[BLEED_REDUCTION_KEY] = bleedReduction
            preferences[QUALITY_MODE_KEY] = qualityMode
            preferences[STUDIO_MASTER_KEY] = studioMaster
        }
    }

    suspend fun saveRecordConfig(config: RecordConfig) {
        context.dataStore.edit { preferences ->
            preferences[REC_WIDTH_KEY] = config.width
            preferences[REC_HEIGHT_KEY] = config.height
            preferences[REC_RES_NAME_KEY] = config.resolutionName
            preferences[REC_BITRATE_KEY] = config.bitrate
            preferences[REC_BITRATE_NAME_KEY] = config.bitrateName
            preferences[REC_FPS_KEY] = config.fps
            preferences[REC_USE_HEVC_KEY] = config.useHevc
            preferences[REC_INC_MIC_KEY] = config.includeMic
            preferences[REC_INC_INT_KEY] = config.includeInternal
            preferences[REC_USE_COUNTDOWN_KEY] = config.useCountdown
            preferences[REC_SHOW_FACECAM_KEY] = config.showFacecam
            preferences[REC_EXPORT_MIC_KEY] = config.exportMicOnly
            preferences[REC_EXPORT_INTERNAL_KEY] = config.exportInternalOnly
            preferences[AUTO_STOP_DURATION_KEY] = config.autoStopDurationMinutes
            preferences[AUTO_STOP_BATTERY_KEY] = config.autoStopBatteryLevel
            preferences[REC_PROFILE_KEY] = config.profile.name
            preferences[USE_WATERMARK_TEXT_KEY] = config.useWatermarkText
            preferences[WATERMARK_TEXT_KEY] = config.watermarkText
            preferences[USE_WATERMARK_IMAGE_KEY] = config.useWatermarkImage
            preferences[WATERMARK_IMAGE_PATH_KEY] = config.watermarkImagePath
            preferences[STOP_ON_SCREEN_OFF_KEY] = config.stopOnScreenOff
            preferences[STOP_ON_SHAKE_KEY] = config.stopOnShake
            preferences[AUTO_LAUNCH_PACKAGE_KEY] = config.autoLaunchPackage
            preferences[SET_VOLUME_ON_START_KEY] = config.setVolumeOnStart
            preferences[START_VOLUME_LEVEL_KEY] = config.startVolumeLevel
            preferences[SHOW_TOUCHES_KEY] = config.showTouches
            preferences[ORIENTATION_LOCK_KEY] = config.orientationLock
            preferences[FACECAM_SHAPE_KEY] = config.facecamShape
            preferences[AUDIO_SAMPLE_RATE_KEY] = config.audioSampleRate
            preferences[AUDIO_BITRATE_KEY] = config.audioBitrate
            preferences[SHAKE_SENSITIVITY_KEY] = config.shakeSensitivity
            preferences[MENU_STYLE_KEY] = config.menuStyle
            preferences[SHOW_SHORTCUTS_KEY] = config.showShortcuts
            preferences[SHORTCUTS_KEY] = config.shortcuts.joinToString(",")
            preferences[MINIMIZING_SIDE_KEY] = config.minimizingSide
            preferences[START_MINIMIZED_KEY] = config.startMinimized
            preferences[SHOW_PAUSE_RESUME_KEY] = config.showPauseResumeOnMenu
            preferences[SHOW_CAMERA_BTN_KEY] = config.showCameraButtonOnMenu
            preferences[SHOW_DRAW_BTN_KEY] = config.showDrawButtonOnMenu
            preferences[SHOW_SCREENSHOT_BTN_KEY] = config.showScreenshotButtonOnMenu
            preferences[SHOW_TIME_KEY] = config.showTimeOnMenu
            preferences[SHOW_TIME_LIMIT_KEY] = config.showTimeLimitOnMenu
        }
    }

    suspend fun saveAudioSettings(
        isSpeakerOn: Boolean,
        isA2dpModeOn: Boolean,
        isHighQuality: Boolean,
        bitrate: Int,
        isStereo: Boolean,
        micVolume: Float,
        outputVolume: Float
    ) {
        context.dataStore.edit { preferences ->
            preferences[SPEAKER_ON_KEY] = isSpeakerOn
            preferences[A2DP_MODE_KEY] = isA2dpModeOn
            preferences[HIGH_QUALITY_KEY] = isHighQuality
            preferences[BITRATE_KEY] = bitrate
            preferences[STEREO_KEY] = isStereo
            preferences[MIC_VOLUME_KEY] = micVolume
            preferences[OUTPUT_VOLUME_KEY] = outputVolume
        }
    }

    suspend fun saveWifiSharePasswordRequired(required: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_SHARE_REQUIRE_PASSWORD_KEY] = required
        }
    }

    val selectedProfileName: Flow<String?> = _prefs.filterNotNull().map { preferences ->
        preferences[SELECTED_PROFILE_NAME_KEY]
    }

    suspend fun saveSelectedProfileName(name: String?) {
        context.dataStore.edit { preferences ->
            if (name == null) {
                preferences.remove(SELECTED_PROFILE_NAME_KEY)
            } else {
                preferences[SELECTED_PROFILE_NAME_KEY] = name
            }
        }
    }

    // AI - Hardware Cache
    val deviceIdentity: Flow<Pair<String?, String?>> = _prefs.filterNotNull().map { preferences ->
        preferences[CACHED_MODEL_NAME_KEY] to preferences[CACHED_SOC_NAME_KEY]
    }

    suspend fun saveDeviceIdentity(model: String, soc: String) {
        context.dataStore.edit { preferences ->
            preferences[CACHED_MODEL_NAME_KEY] = model
            preferences[CACHED_SOC_NAME_KEY] = soc
        }
    }

    val aiTtsMode: Flow<String> = _prefs.filterNotNull().map { preferences ->
        preferences[AI_TTS_MODE_KEY] ?: "LEGACY"
    }

    suspend fun saveAiTtsMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_TTS_MODE_KEY] = mode
        }
    }

    // AI - Downloaded Languages
    private val DOWNLOADED_LANGS_KEY = stringPreferencesKey("ai_downloaded_langs")

    val downloadedLanguages: Flow<List<String>> = _prefs.filterNotNull().map { preferences ->
        preferences[DOWNLOADED_LANGS_KEY]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun addDownloadedLanguage(langCode: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[DOWNLOADED_LANGS_KEY]?.split(",")?.toMutableList() ?: mutableListOf()
            if (!current.contains(langCode)) {
                current.add(langCode)
                preferences[DOWNLOADED_LANGS_KEY] = current.joinToString(",")
            }
        }
    }

    // ============= Custom Recording Profiles =============
    private val CUSTOM_PROFILES_KEY = stringPreferencesKey("custom_recording_profiles")

    val customProfiles: Flow<List<CustomRecordingProfile>> = _prefs.filterNotNull().map { preferences ->
        val json = preferences[CUSTOM_PROFILES_KEY] ?: "[]"
        try {
            Json.decodeFromString<List<CustomRecordingProfile>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCustomProfile(profile: CustomRecordingProfile) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[CUSTOM_PROFILES_KEY] ?: "[]"
            val profiles = try {
                Json.decodeFromString<MutableList<CustomRecordingProfile>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            // Replace if name exists, else add
            val existingIndex = profiles.indexOfFirst { p -> p.name == profile.name }
            if (existingIndex >= 0) {
                profiles[existingIndex] = profile
            } else {
                profiles.add(profile)
            }
            preferences[CUSTOM_PROFILES_KEY] = Json.encodeToString(profiles)
        }
    }

    suspend fun deleteCustomProfile(profileName: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[CUSTOM_PROFILES_KEY] ?: "[]"
            val profiles = try {
                Json.decodeFromString<MutableList<CustomRecordingProfile>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            profiles.removeAll { p -> p.name == profileName }
            preferences[CUSTOM_PROFILES_KEY] = Json.encodeToString(profiles)
        }
    }

    val virustotalApiKey: Flow<String> = _prefs.filterNotNull().map { it[VIRUSTOTAL_API_KEY] ?: "" }

    // Experimental Accessors
    val showExperimentalLab: Flow<Boolean> = _prefs.filterNotNull().map { it[SHOW_EXPERIMENTAL_LAB] ?: false }
    val watchdogEnabled: Flow<Boolean> = _prefs.filterNotNull().map { it[WATCHDOG_ENABLED] ?: false }
    val heartbeatEnabled: Flow<Boolean> = _prefs.filterNotNull().map { it[HEARTBEAT_ENABLED] ?: false }
    val overlayEnabled: Flow<Boolean> = _prefs.filterNotNull().map { it[OVERLAY_ENABLED] ?: false }
    val crashReportEnabled: Flow<Boolean> = _prefs.filterNotNull().map { it[CRASH_REPORT_ENABLED] ?: false }

    suspend fun saveVirusTotalApiKey(key: String) {
        context.dataStore.edit { it[VIRUSTOTAL_API_KEY] = key }
    }

    suspend fun setShowExperimentalLab(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_EXPERIMENTAL_LAB] = enabled }
    }

    suspend fun setWatchdogEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WATCHDOG_ENABLED] = enabled }
    }

    suspend fun setHeartbeatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HEARTBEAT_ENABLED] = enabled }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OVERLAY_ENABLED] = enabled }
    }

    suspend fun setCrashReportEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CRASH_REPORT_ENABLED] = enabled }
    }
}
