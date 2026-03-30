#!/system/bin/sh
# BokBok Sentinel (Heartbeat & Watchdog)
# VERSION: 12 (The "Ultimate" Sentinel - Consistent Variable Naming)
# This version restores the 'current_focus' name to align with the user's preferences.

PACKAGE_NAME="com.mustakim.bokbok"
SERVICE_NAME="${PACKAGE_NAME}.data.service.GameWatchdogService"
OVERLAY_COMPONENT="${PACKAGE_NAME}/${PACKAGE_NAME}.ui.overlay.GameBoosterOverlayActivity"
GAME_LIST_FILE="/data/local/tmp/bokbok_games.list"
HEARTBEAT_FILE="/data/local/tmp/heartbeat_last_run"
MONITOR_DAEMON="/data/local/tmp/monitor_daemon.sh"
WINDOW_FILE="/data/local/tmp/bokbok_current_window"

DEBUG_LOG="/data/local/tmp/bokbok_sentinel.log"

# 🚀 SYN-STYLE: Fix permissions for app-level reading
touch "$DEBUG_LOG" "$HEARTBEAT_FILE" "$WINDOW_FILE"
chmod 666 "$DEBUG_LOG" "$HEARTBEAT_FILE" "$WINDOW_FILE"

echo "$(date '+%H:%M:%S') BokBok Ultimate Sentinel v12 started" >> "$DEBUG_LOG"

last_foreground_app=""
last_game_pkg=""
overlay_active=false
loop_count=0

# 1. Startup Logic
if cmd usage-stats --help >/dev/null 2>&1; then
    HAS_USAGE_STATS=true
    # Set bucket once at startup instead of every loop to stop Shizuku spam
    cmd usage-stats set-standby-bucket "$PACKAGE_NAME" active > /dev/null 2>&1
fi

while true; do
    # A. Update heartbeat file
    date +%s > "$HEARTBEAT_FILE"
    
    # B. [REMOVED] Periodical standby bucket update (Moved to startup)

    # Log that we are alive every 5 loops (approx 10s)
    loop_count=$((loop_count + 1))
    if [ $((loop_count % 5)) -eq 0 ]; then
        echo "$(date '+%H:%M:%S') [Sentinel] Alive. App: [$last_foreground_app] Overlay: [$overlay_active]" >> "$DEBUG_LOG"
    fi

    # C. SELF-HEALING: Verify Partner Monitor Daemon
    if ! pgrep -f "monitor_daemon.sh" > /dev/null; then
        if [ -f "$MONITOR_DAEMON" ]; then
            echo "$(date '+%H:%M:%S') [Self-Healing] Monitor Daemon lost. Restarting..." >> "$DEBUG_LOG"
            nohup sh "$MONITOR_DAEMON" > /dev/null 2>&1 &
        fi
    fi

    # D. ROBUST Window & Package Detection (Android 16 BLAST Edition)
    # 🚀 SYN-STYLE: Multi-source Focus Detection (Optimized for Android 16)
    # 1. Primary: activity activities (Look for top resumed or visible processes)
    current_focus=$(dumpsys activity activities 2>/dev/null | grep -v "$PACKAGE_NAME" | grep -v "GameBoosterOverlayActivity" | grep -E 'topResumedActivity|mFocusedApp|VisibleActivityProcess|packageName=|TaskRecord')
    
    # 2. Fallback: SurfaceFlinger (The "Ground Truth" of what is on screen)
    if [ -z "$current_focus" ]; then
        current_focus=$(dumpsys SurfaceFlinger --list 2>/dev/null | grep -vE "Task=|ColorLayer|Cursor|Navigation" | head -n 5)
    fi
    
    # 3. Flexible Package Extraction (Mining for pkg names - Android 16 Refined)
    # Method A: Package assignment (packageName=com.example)
    current_pkg=$(echo "$current_focus" | grep -oE 'packageName=[a-zA-Z0-9._]+' | cut -d'=' -f2 | head -n 1)
    
    # Method B: pkg/activity format (com.example/com.example.MainActivity#0)
    if [ -z "$current_pkg" ]; then
        current_pkg=$(echo "$current_focus" | grep -oE '[a-zA-Z0-9._]+\/[a-zA-Z0-9._@$#]+' | cut -d'/' -f1 | head -n 1)
    fi
    
    # Method C: ProcessRecord patterns (1234:com.example/u0a123)
    if [ -z "$current_pkg" ]; then
        current_pkg=$(echo "$current_focus" | grep -oE '[0-9]+:[a-zA-Z0-9._]+' | cut -d':' -f2 | head -n 1)
    fi
    
    # Method D: Raw UID/PKG (u0 com.example or u0:com.example)
    if [ -z "$current_pkg" ]; then
        current_pkg=$(echo "$current_focus" | grep -oE 'u[0-9][: ]+[a-zA-Z0-9._]+' | awk '{print $NF}' | head -n 1)
    fi

    # E. State Change Management
    if [ -n "$current_pkg" ] && [ "$current_pkg" != "$last_foreground_app" ]; then
        echo "$(date '+%H:%M:%S') [Focus] Changed: $last_foreground_app -> $current_pkg" >> "$DEBUG_LOG"
        last_foreground_app="$current_pkg"
        
        is_game=false
        if [ -f "$GAME_LIST_FILE" ]; then
            if grep -Fxq "$current_pkg" "$GAME_LIST_FILE"; then
                is_game=true
                echo "$(date '+%H:%M:%S') [Match] Game detected: $current_pkg" >> "$DEBUG_LOG"
            fi
        fi
        
        if [ "$is_game" = true ]; then
             echo "$current_pkg" > "$WINDOW_FILE"
             if [ "$overlay_active" = false ]; then
                 echo "$(date '+%H:%M:%S') [Overlay] Opening for $current_pkg..." >> "$DEBUG_LOG"
                 launch_res=$(am start -n "$OVERLAY_COMPONENT" --es "GAME_PACKAGE" "$current_pkg" --display 0 2>&1)
                 echo "  -> Result: $launch_res" >> "$DEBUG_LOG"
                 overlay_active=true
                 last_game_pkg="$current_pkg"
             else
                 if [ "$current_pkg" != "$last_game_pkg" ]; then
                      echo "$(date '+%H:%M:%S') [Overlay] Switch: $last_game_pkg -> $current_pkg" >> "$DEBUG_LOG"
                      switch_res=$(am start -n "$OVERLAY_COMPONENT" --es "GAME_PACKAGE" "$current_pkg" --display 0 2>&1)
                      overlay_active=true
                      last_game_pkg="$current_pkg"
                 fi
             fi
        else
             rm -f "$WINDOW_FILE"
             if [ "$overlay_active" = true ]; then
                 echo "$(date '+%H:%M:%S') [Overlay] Closing (Exited $last_game_pkg)..." >> "$DEBUG_LOG"
                 # Use fully qualified name for the service call
                 dismiss_res=$(am startservice -n "com.mustakim.bokbok/com.mustakim.bokbok.ui.overlay.GameBoosterOverlayService" --es "ACTION" "CLOSE" 2>&1)
                 echo "  -> Dismiss Result: $dismiss_res" >> "$DEBUG_LOG"
                 overlay_active=false
             fi
        fi
    fi
    
    sleep 2
done
