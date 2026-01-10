package com.mustakim.bokbok.startup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * StartupManager - Orchestrates staged app initialization
 * 
 * Follows the "Discord pattern" of deferred initialization:
 * - Stage 0: Instant UI, cached auth check only
 * - Stage 1: Navigate to main screen, show skeleton
 * - Stage 2: Start Firebase listeners and sync (after first frame)
 * 
 * This prevents the main thread from being blocked during cold start.
 */
object StartupManager {
    private const val TAG = "StartupManager"
    
    enum class Stage {
        UNINITIALIZED,
        UI_READY,           // Stage 0 complete - UI is shown
        DATA_READY,         // Stage 1 complete - Basic data loaded
        FULLY_INITIALIZED   // Stage 2 complete - All listeners active
    }
    
    private val _currentStage = MutableStateFlow(Stage.UNINITIALIZED)
    val currentStage: StateFlow<Stage> = _currentStage.asStateFlow()
    
    private val _isFirstLaunchComplete = MutableStateFlow(false)
    val isFirstLaunchComplete: StateFlow<Boolean> = _isFirstLaunchComplete.asStateFlow()
    
    // Lightweight scope for startup tasks - uses IO dispatcher
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Deferred initialization callbacks
    private val stage2Tasks = mutableListOf<suspend () -> Unit>()
    
    /**
     * Mark UI as shown - Called from SplashScreen
     */
    fun markUiReady() {
        Log.d(TAG, "Stage 0 complete: UI Ready")
        _currentStage.value = Stage.UI_READY
    }
    
    /**
     * Mark basic data as loaded - Called when skeleton transitions to content
     */
    fun markDataReady() {
        Log.d(TAG, "Stage 1 complete: Data Ready")
        _currentStage.value = Stage.DATA_READY
        
        // Trigger Stage 2 initialization after a delay (let UI fully stabilize)
        startupScope.launch {
            delay(1000) // Increased from 300ms to allow all enter animations to finish
            executeStage2Tasks()
        }
    }
    
    /**
     * Register a task to be executed in Stage 2 (after first frame)
     * Use this for:
     * - Starting Firebase listeners
     * - FCM token refresh
     * - Background sync
     * - Presence updates
     */
    fun registerDeferredTask(task: suspend () -> Unit) {
        synchronized(stage2Tasks) {
            if (_currentStage.value == Stage.FULLY_INITIALIZED) {
                // Already past Stage 2, execute immediately on IO
                startupScope.launch { task() }
            } else {
                stage2Tasks.add(task)
                Log.d(TAG, "Registered deferred task (total: ${stage2Tasks.size})")
            }
        }
    }
    
    private suspend fun executeStage2Tasks() {
        Log.d(TAG, "Executing Stage 2 tasks...")
        
        while (true) {
            val tasksSnapshot = synchronized(stage2Tasks) {
                if (stage2Tasks.isEmpty()) {
                    // All tasks drained - switch to fully initialized state
                    _currentStage.value = Stage.FULLY_INITIALIZED
                    _isFirstLaunchComplete.value = true
                    return@synchronized null
                }
                val snapshot = stage2Tasks.toList()
                stage2Tasks.clear()
                snapshot
            } ?: break

            Log.d(TAG, "Draining ${tasksSnapshot.size} tasks...")
            tasksSnapshot.forEach { task ->
                try {
                    task()
                } catch (e: Exception) {
                    Log.e(TAG, "Stage 2 task failed", e)
                }
            }
        }
        
        Log.d(TAG, "Stage 2 complete: Fully Initialized")
    }
    
    /**
     * Check if deferred initialization should be skipped
     * (e.g., when app is already running and user navigates back)
     */
    fun isAlreadyInitialized(): Boolean {
        return _currentStage.value == Stage.FULLY_INITIALIZED
    }
    
    /**
     * Reset for testing purposes
     */
    fun reset() {
        synchronized(stage2Tasks) {
            _currentStage.value = Stage.UNINITIALIZED
            _isFirstLaunchComplete.value = false
            stage2Tasks.clear()
        }
    }
}
