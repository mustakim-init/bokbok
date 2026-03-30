#!/system/bin/sh
# BokBok Monitor Daemon (The "Diamond")
# VERSION: 3 (CPU Load + FPS + GPU)
# Corrected for the "BokBok" performance engine.

MONITOR_FILE="/data/local/tmp/bokbok_monitor.now"
WINDOW_FILE="/data/local/tmp/bokbok_current_window"

touch "$MONITOR_FILE"
chmod 666 "$MONITOR_FILE"

# GPU Paths (Check if readable to avoid SELinux avc: denied log spam)
GPU_LOAD_PATH=""
GPU_FREQ_PATH=""
if timeout 0.1 cat "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" >/dev/null 2>&1; then
    GPU_LOAD_PATH="/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
fi
if timeout 0.1 cat "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" >/dev/null 2>&1; then
    GPU_FREQ_PATH="/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
fi

# CPU Usage Snapshot State
last_total=0
last_idle=0

while true; do
    # 1. CPU Usage Calculation (Robust Sync)
    # Reads the first line of /proc/stat which represents the combined CPU usage
    read_procs=$(head -n 1 /proc/stat)
    if [ -n "$read_procs" ]; then
        # Format: cpu  user nice system idle iowait irq softirq steal guest guest_nice
        # We sum all columns to get the total time
        cpu_stats=$(echo "$read_procs" | cut -d' ' -f2-)
        current_total=0
        for val in $cpu_stats; do
            current_total=$((current_total + val))
        done
        # The 4th column (index 5 in set -- $read_procs) is idle
        set -- $read_procs
        current_idle=$5
        
        diff_total=$((current_total - last_total))
        diff_idle=$((current_idle - last_idle))
        
        if [ $diff_total -gt 0 ]; then
            # (Total - Idle) / Total * 100
            cpu_usage=$(((diff_total - diff_idle) * 100 / diff_total))
        else
            cpu_usage=0
        fi
        
        # Guard against 0% if the jump was very small but non-zero
        if [ $cpu_usage -eq 0 ] && [ $diff_total -gt 0 ] && [ $diff_idle -lt $diff_total ]; then
            cpu_usage=1
        fi

        last_total=$current_total
        last_idle=$current_idle
    fi

    # 2. FPS Calculation (Android 16 BLAST Edition - Stripped)
    current_fps=0
    if [ -f "$WINDOW_FILE" ]; then
        window_pkg=$(cat "$WINDOW_FILE" 2>/dev/null | tr -d '\r\n')
        if [ -n "$window_pkg" ]; then
            # 🚀 SYN-STYLE: Advanced Layer Search & CLEANUP (Android 16+)
            # 1. Fetch the raw layer line from dumpsys
            raw_layer=$(dumpsys SurfaceFlinger --list 2>/dev/null | grep "$window_pkg" | grep -E "SurfaceView|BLAST|BufferStateLayer|#0|Native" | grep -vE "Task=|ColorLayer|Cursor|Navigation|Snapshot" | head -n 1)
            
            # 2. STRIP Android 16 Metadata (RequestedLayerState{...})
            # This extracts the actual name between digits/spaces and timestamps
            sf_layer=$(echo "$raw_layer" | sed -E 's/^RequestedLayerState\{//; s/ [0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}.*$//; s/\}$//; s/ parentId=.*$//')
            
            # Fallback: Just the package but prioritize the one with "#0"
            if [ -z "$sf_layer" ]; then
                raw_layer=$(dumpsys SurfaceFlinger --list 2>/dev/null | grep "$window_pkg" | sort -r | head -n 1)
                sf_layer=$(echo "$raw_layer" | sed -E 's/^RequestedLayerState\{//; s/ [0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}.*$//; s/\}$//')
            fi
            
            if [ -n "$sf_layer" ]; then
                # Fetch raw SurfaceFlinger latencies
                timestamps=$(dumpsys SurfaceFlinger --latency "$sf_layer" 2>/dev/null | tail -n +2 | grep -vE "^0[[:space:]]+0[[:space:]]+0")
                
                # awk logic for FPS
                awk_script='
                BEGIN { count=0; first=0; last=0 }
                {
                    val = $2; 
                    if (val > 0 && val != 9223372036854775807) {
                        if (count == 0) first = val;
                        last = val;
                        count++;
                    }
                }
                END {
                    if (count > 2 && last > first) {
                        fps = (count * 1000000000.0) / (last - first);
                        if (fps > 144) fps=144;
                        if (fps < 0) fps=0;
                        printf "%d", fps;
                    } else {
                        printf "0";
                    }
                }'
                
                calculated_fps=$(echo "$timestamps" | awk "$awk_script")
                
                if [ -n "$calculated_fps" ]; then
                    current_fps=$calculated_fps
                fi
            fi
        fi
    fi

    # 3. GPU Stats (Normalization for high values)
    gpu_load=0
    gpu_freq=0
    if [ -n "$GPU_LOAD_PATH" ]; then
        # Read raw value
        raw_gpu=$(cat "$GPU_LOAD_PATH" 2>/dev/null || echo 0)
        # 🚀 SYN-STYLE: Normalize. If device uses ticks (0-100000) or scaled (0-10000)
        # Most systems are 0-100 or 0-1000.
        if [ "$raw_gpu" -gt 100 ]; then
            if [ "$raw_gpu" -gt 1000 ]; then
                gpu_load=$((raw_gpu / 1000))
            else
                gpu_load=$((raw_gpu / 10))
            fi
        else
            gpu_load=$raw_gpu
        fi
        
        # Hard cap at 100%
        if [ "$gpu_load" -gt 100 ]; then gpu_load=100; fi
    fi
    
    if [ -n "$GPU_FREQ_PATH" ]; then
        gpu_freq=$(cat "$GPU_FREQ_PATH" 2>/dev/null || echo 0)
    fi
    
    # 4. Atomic Write for App Observation
    echo "cpu_usage=$cpu_usage
fps=$current_fps
gpu_load=$gpu_load
gpu_freq=$gpu_freq
timestamp=$(date +%s)" > "${MONITOR_FILE}.tmp"
    mv "${MONITOR_FILE}.tmp" "$MONITOR_FILE"

    sleep 1
done
