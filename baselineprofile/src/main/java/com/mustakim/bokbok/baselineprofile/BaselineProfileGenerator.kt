package com.mustakim.bokbok.baselineprofile

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Comprehensive Baseline Profile Generator for BokBok
 * 
 * This script exercises ALL major user flows in the app to maximize AOT compilation coverage.
 * It covers: Startup, Lounge, Chats, Optimizer (all 6 tabs), AI Companion, Profile, Settings.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "com.mustakim.bokbok"
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
            maxIterations = 1
        ) {
            try {
                // =============================================================================
                // PHASE 1: STARTUP & INITIAL LOAD
                // =============================================================================
                Log.d("BokBokProfiler", "--- STARTING BOKBOK BASELINE PROFILE GENERATION ---")
                
                setupPermissions(packageName)
                pressHome()
                startActivityAndWait()
                
                // Wait for initial content to load
                Thread.sleep(3000)
                handlePermissionDialogs()
                
                // =============================================================================
                // PHASE 2: LOUNGE SCREEN (Main Dashboard)
                // =============================================================================
                runStep("Lounge Screen") {
                    // The app should start on Lounge by default
                    // Scroll the main content to trigger list rendering
                    blindScroll()
                    Thread.sleep(1000)
                    
                    // Click bottom nav items to trigger their composition
                    clickBottomNavItem("Lounge|lounge")
                    Thread.sleep(1000)
                }

                // =============================================================================
                // PHASE 3: CHATS SCREEN
                // =============================================================================
                runStep("Chats Screen") {
                    clickBottomNavItem("Chats|chats")
                    Thread.sleep(1500)
                    
                    // Scroll chat list
                    blindScroll()
                    Thread.sleep(1000)
                }

                // =============================================================================
                // PHASE 4: OPTIMIZER SCREEN (6 Tabs - Critical for Performance)
                // =============================================================================
                runStep("Optimizer - Navigate to Screen") {
                    clickBottomNavItem("Optimizer|optimizer")
                    Thread.sleep(2000)
                }

                // Swipe through all 6 Optimizer tabs
                runStep("Optimizer - Tab Swiping Flow") {
                    // Tabs: Game Boost -> App Manager -> Usage Stats -> Device Monitor -> Screen Record -> Security
                    repeat(6) { tabIndex ->
                        Log.d("BokBokProfiler", "Swiping to tab $tabIndex")
                        waitForIdle()
                        
                        // Swipe Left to next tab
                        val startX = (device.displayWidth * 0.85).toInt()
                        val endX = (device.displayWidth * 0.15).toInt()
                        val centerY = (device.displayHeight * 0.55).toInt()
                        
                        device.swipe(startX, centerY, endX, centerY, 30)
                        Thread.sleep(1500) // Allow ViewModel initialization
                        
                        // Scroll content in each tab
                        blindScroll()
                    }
                    
                    // Swipe back through all tabs
                    repeat(6) {
                        val startX = (device.displayWidth * 0.15).toInt()
                        val endX = (device.displayWidth * 0.85).toInt()
                        val centerY = (device.displayHeight * 0.55).toInt()
                        
                        device.swipe(startX, centerY, endX, centerY, 30)
                        Thread.sleep(800)
                    }
                }

                // =============================================================================
                // PHASE 5: AI COMPANION SCREEN
                // =============================================================================
                runStep("AI Companion Screen") {
                    // AI Companion is accessed via a button in the Optimizer toolbar
                    val aiButton = device.wait(Until.findObject(By.desc("AI Companion")), 2000)
                        ?: device.wait(Until.findObject(By.text("AI")), 1000)
                    
                    if (aiButton != null) {
                        aiButton.click()
                        Thread.sleep(2000)
                        
                        // Scroll AI chat content
                        blindScroll()
                        
                        // Navigate back
                        device.pressBack()
                        Thread.sleep(1000)
                    } else {
                        Log.w("BokBokProfiler", "AI Companion button not found, skipping")
                    }
                }

                // =============================================================================
                // PHASE 6: PROFILE SCREEN
                // =============================================================================
                runStep("Profile Screen") {
                    // Navigate to Profile (usually in bottom nav or drawer)
                    clickBottomNavItem("Profile|profile")
                    Thread.sleep(1500)
                    
                    // Scroll profile content
                    blindScroll()
                    Thread.sleep(1000)
                }

                // =============================================================================
                // PHASE 7: SETTINGS SCREEN
                // =============================================================================
                runStep("Settings Screen") {
                    val settingsPattern = Pattern.compile(".*(Settings|settings|gear).*", Pattern.CASE_INSENSITIVE)
                    val settingsBtn = device.findObject(By.desc(settingsPattern))
                        ?: device.findObject(By.text("Settings"))
                    
                    if (settingsBtn != null) {
                        settingsBtn.click()
                        Thread.sleep(2000)
                        
                        // Scroll settings list
                        blindScroll()
                        
                        // Navigate back
                        device.pressBack()
                        Thread.sleep(1000)
                    } else {
                        Log.w("BokBokProfiler", "Settings button not found, skipping")
                    }
                }

                // =============================================================================
                // PHASE 8: RETURN TO LOUNGE (Cleanup)
                // =============================================================================
                runStep("Return to Lounge") {
                    clickBottomNavItem("Lounge|lounge")
                    Thread.sleep(1000)
                }

                Log.d("BokBokProfiler", "--- BASELINE PROFILE GENERATION COMPLETE ---")
                pressHome()

            } catch (e: Exception) {
                Log.e("BokBokProfiler", "Fatal error during profile generation: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // ====================================================================================
    // HELPER FUNCTIONS
    // ====================================================================================

    private fun runStep(name: String, block: () -> Unit) {
        try {
            Log.d("BokBokProfiler", ">> STEP: $name")
            block()
            Log.d("BokBokProfiler", ">> OK: $name")
        } catch (e: Exception) {
            Log.e("BokBokProfiler", ">> FAILED: $name - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun MacrobenchmarkScope.setupPermissions(packageName: String) {
        val permissions = listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO"
        )
        permissions.forEach {
            try {
                device.executeShellCommand("pm grant $packageName $it")
            } catch (ignore: Exception) {}
        }
    }

    private fun MacrobenchmarkScope.handlePermissionDialogs() {
        val pattern = Pattern.compile("Allow|Permitir|Accept|While using|Continue", Pattern.CASE_INSENSITIVE)
        repeat(3) {
            device.findObject(By.text(pattern))?.click()
            Thread.sleep(500)
        }
    }

    private fun MacrobenchmarkScope.clickBottomNavItem(itemPattern: String) {
        val pattern = Pattern.compile(itemPattern, Pattern.CASE_INSENSITIVE)
        val item = device.findObject(By.desc(pattern))
            ?: device.findObject(By.text(pattern))
        item?.let {
            device.click(it.visibleCenter.x, it.visibleCenter.y)
            Thread.sleep(1000)
        }
    }

    private fun MacrobenchmarkScope.blindScroll() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.30).toInt()
        
        // Scroll down
        device.swipe(midX, bottomY, midX, topY, 40)
        Thread.sleep(1000)
        
        // Scroll up
        device.swipe(midX, topY, midX, bottomY, 40)
        Thread.sleep(800)
    }
}
