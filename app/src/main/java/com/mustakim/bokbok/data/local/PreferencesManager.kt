package com.mustakim.bokbok.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mustakim.bokbok.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
 
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bokbok_preferences")
 
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("selected_theme")
        private val SPEAKER_ON_KEY = booleanPreferencesKey("speaker_on")
        private val A2DP_MODE_KEY = booleanPreferencesKey("a2dp_mode")
        private val HIGH_QUALITY_KEY = booleanPreferencesKey("high_quality")
        private val MIC_VOLUME_KEY = floatPreferencesKey("mic_volume")
        private val OUTPUT_VOLUME_KEY = floatPreferencesKey("output_volume")
    }

    val selectedTheme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[THEME_KEY] ?: AppTheme.MATERIAL_CLASSIC.name
        AppTheme.valueOf(themeName)
    }

    val audioSettings: Flow<Map<String, Any>> = context.dataStore.data.map { preferences ->
        mapOf(
            "isSpeakerOn" to (preferences[SPEAKER_ON_KEY] ?: true),
            "isA2dpModeOn" to (preferences[A2DP_MODE_KEY] ?: false),
            "isHighQuality" to (preferences[HIGH_QUALITY_KEY] ?: true),
            "micVolume" to (preferences[MIC_VOLUME_KEY] ?: 1f),
            "outputVolume" to (preferences[OUTPUT_VOLUME_KEY] ?: 1f)
        )
    }

    suspend fun saveTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    suspend fun saveAudioSettings(
        isSpeakerOn: Boolean,
        isA2dpModeOn: Boolean,
        isHighQuality: Boolean,
        micVolume: Float,
        outputVolume: Float
    ) {
        context.dataStore.edit { preferences ->
            preferences[SPEAKER_ON_KEY] = isSpeakerOn
            preferences[A2DP_MODE_KEY] = isA2dpModeOn
            preferences[HIGH_QUALITY_KEY] = isHighQuality
            preferences[MIC_VOLUME_KEY] = micVolume
            preferences[OUTPUT_VOLUME_KEY] = outputVolume
        }
    }
}