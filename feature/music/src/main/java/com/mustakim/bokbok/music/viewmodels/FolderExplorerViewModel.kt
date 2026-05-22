package com.mustakim.bokbok.music.viewmodels

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mustakim.bokbok.data.local.PreferenceStore
import com.mustakim.bokbok.data.local.dataStore
import com.mustakim.bokbok.music.constants.LocalMusicAllowedDirsKey
import com.mustakim.bokbok.music.constants.LocalMusicBlockedDirsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FolderExplorerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _folders = MutableStateFlow<List<File>>(emptyList())
    val folders: StateFlow<List<File>> = _folders.asStateFlow()

    private val _allowedFolders = MutableStateFlow<Set<String>>(emptySet())
    val allowedFolders: StateFlow<Set<String>> = _allowedFolders.asStateFlow()

    private val _blockedFolders = MutableStateFlow<Set<String>>(emptySet())
    val blockedFolders: StateFlow<Set<String>> = _blockedFolders.asStateFlow()

    init {
        loadFolders(_currentPath.value)
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            val allowedRaw = context.dataStore.data.first()[LocalMusicAllowedDirsKey] ?: ""
            val blockedRaw = context.dataStore.data.first()[LocalMusicBlockedDirsKey] ?: ""
            
            _allowedFolders.value = allowedRaw.split(",").filter { it.isNotBlank() }.toSet()
            _blockedFolders.value = blockedRaw.split(",").filter { it.isNotBlank() }.toSet()
        }
    }

    fun loadFolders(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val directory = File(path)
            if (directory.isDirectory) {
                _currentPath.value = path
                val list = directory.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() } ?: emptyList()
                _folders.value = list
            }
        }
    }

    fun navigateUp(): Boolean {
        val parent = File(_currentPath.value).parentFile
        if (parent != null && parent.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
            loadFolders(parent.absolutePath)
            return true
        }
        return false
    }

    fun toggleAllowed(path: String) {
        val current = _allowedFolders.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
            // If we allow a folder, it shouldn't be blocked
            _blockedFolders.value = _blockedFolders.value.filter { it != path }.toSet()
        }
        _allowedFolders.value = current
        savePreferences()
    }

    fun toggleBlocked(path: String) {
        val current = _blockedFolders.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
            // If we block a folder, it shouldn't be allowed
            _allowedFolders.value = _allowedFolders.value.filter { it != path }.toSet()
        }
        _blockedFolders.value = current
        savePreferences()
    }

    private fun savePreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            PreferenceStore.launchEdit(context.dataStore) {
                this[LocalMusicAllowedDirsKey] = _allowedFolders.value.joinToString(",")
                this[LocalMusicBlockedDirsKey] = _blockedFolders.value.joinToString(",")
            }
        }
    }
}
