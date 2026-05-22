#!/system/bin/sh
# BokBok Sentinel v22 - The "Pro" Edition (BusyBox Integrated)
# Optimization: Uses custom BusyBox binary for high-speed, reliable parsing.

PACKAGE_NAME="com.mustakim.bokbok"
OVERLAY_COMPONENT="${PACKAGE_NAME}/${PACKAGE_NAME}.ui.overlay.GameBoosterOverlayActivity"
OVERLAY_SERVICE_COMPONENT="${PACKAGE_NAME}/${PACKAGE_NAME}.ui.overlay.GameBoosterOverlayService"
GAME_LIST_FILE="/data/local/tmp/bokbok_games.list"
HEARTBEAT_FILE="/data/local/tmp/heartbeat_last_run"
WINDOW_FILE="/data/local/tmp/bokbok_current_window"
DEBUG_LOG="/data/local/tmp/bokbok_sentinel.log"
BB="/data/local/tmp/busybox"

# Use busybox for speed if available, fallback to system toys
alias grep="${BB} grep"
alias awk="${BB} awk"
alias pkill="${BB} pkill"

# Setup files
touch "$DEBUG_LOG" "$HEARTBEAT_FILE" "$WINDOW_FILE" 2>/dev/null
chmod 666 "$DEBUG_LOG" "$HEARTBEAT_FILE" "$WINDOW_FILE" 2>/dev/null

echo "$(date '+%H:%M:%S') [Sentinel] v22 Started (BusyBox Pro)" >> "$DEBUG_LOG"

# Kill lingering listeners
[ -x "$BB" ] && "$BB" pkill -9 -f "logcat -b events" 2>/dev/null || pkill -9 -f "logcat -b events" 2>/dev/null
sleep 1

# Clear buffer
logcat -b events -c 2>/dev/null

last_pkg=""
last_game_pkg=""
overlay_active=false

# 🚀 PRO START: Robustly detect what is already in focus.
# Using BusyBox grep/awk if available makes this much faster.
if [ -x "$BB" ]; then
    current_focus=$(dumpsys window | "$BB" grep -E 'mCurrentFocus|mFocusedApp' | "$BB" grep -v "null" | "$BB" head -n 1 | "$BB" grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | "$BB" cut -d'/' -f1)
else
    current_focus=$(dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | grep -v "null" | head -n 1 | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | cut -d'/' -f1)
fi

if [ -n "$current_focus" ] && [ "$current_focus" != "$PACKAGE_NAME" ]; then
    echo "$(date '+%H:%M:%S') [Startup] Detected: $current_focus" >> "$DEBUG_LOG"
    pkg_init="$current_focus"
else
    pkg_init=""
fi

# 🚀 PURE INTERNAL LOOP
(
    if [ -n "$pkg_init" ]; then
        echo "INIT_EVENT $pkg_init"
    fi
    logcat -b events -v brief -s wm_set_resumed_activity am_resume_activity
) | while read -r line; do

    case "$line" in
        INIT_EVENT*)
            pkg="${line#INIT_EVENT }"
            ;;
        *wm_set_resumed_activity*)
            tmp="${line#*[}"
            tmp="${tmp#*,}"
            pkg="${tmp%%/*}"
            pkg="${pkg%%,*}"
            ;;
        *am_resume_activity*)
            tmp="${line#*[}"
            tmp="${tmp#*,}"
            tmp="${tmp#*,}"
            tmp="${tmp#*,}"
            pkg="${tmp%%,*}"
            ;;
        *) continue ;;
    esac

    [ -z "$pkg" ] && continue
    [ "$pkg" = "$last_pkg" ] && continue
    [ "$pkg" = "$PACKAGE_NAME" ] && continue
    last_pkg="$pkg"

    echo "$SECONDS" > "$HEARTBEAT_FILE"

    is_game=false
    if [ -f "$GAME_LIST_FILE" ]; then
        if [ -x "$BB" ]; then
            "$BB" grep -Fxq "$pkg" "$GAME_LIST_FILE" 2>/dev/null && is_game=true
        else
            grep -Fxq "$pkg" "$GAME_LIST_FILE" 2>/dev/null && is_game=true
        fi
    fi

    if [ "$is_game" = "true" ]; then
        echo "$pkg" > "$WINDOW_FILE"
        if [ "$overlay_active" = "false" ]; then
            echo "$(date '+%H:%M:%S') [Overlay] LAUNCH -> $pkg" >> "$DEBUG_LOG"
            am start -n "$OVERLAY_COMPONENT" --es "GAME_PACKAGE" "$pkg" --display 0 < /dev/null >> "$DEBUG_LOG" 2>&1
            overlay_active=true
            last_game_pkg="$pkg"
        elif [ "$pkg" != "$last_game_pkg" ]; then
            echo "$(date '+%H:%M:%S') [Overlay] SWITCH $last_game_pkg -> $pkg" >> "$DEBUG_LOG"
            am start -n "$OVERLAY_COMPONENT" --es "GAME_PACKAGE" "$pkg" --display 0 < /dev/null >> "$DEBUG_LOG" 2>&1
            last_game_pkg="$pkg"
        fi
    else
        rm -f "$WINDOW_FILE"
        if [ "$overlay_active" = "true" ]; then
            echo "$(date '+%H:%M:%S') [Overlay] CLOSE" >> "$DEBUG_LOG"
            am startservice -n "$OVERLAY_SERVICE_COMPONENT" --es "ACTION" "CLOSE" < /dev/null >> "$DEBUG_LOG" 2>&1
            overlay_active=false
            last_game_pkg=""
        fi
    fi
done
