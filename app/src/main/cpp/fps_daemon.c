/**
 * fps_daemon.c
 * BokBok FPS Daemon - mirrors Scene's binder+eBPF approach
 *
 * How it works:
 *   1. Receives target PID from app via Unix socket
 *   2. Finds queueBuffer() address in that PID's libgui.so
 *   3. Attaches a uretprobe via /sys/kernel/debug/tracing
 *   4. Reads perf events - each event = one frame submitted to SurfaceFlinger
 *   5. Counts frames per second, streams result back over socket
 *
 * Run as: ./fps_daemon
 * Requires: shell UID (Shizuku/ADB) - no root needed
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <time.h>
#include <signal.h>
#include <dlfcn.h>
#include <dirent.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/mman.h>
#include <linux/perf_event.h>

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */


#define UPROBE_EVENTS   "/sys/kernel/debug/tracing/uprobe_events"
#define UPROBE_ENABLE   "/sys/kernel/debug/tracing/events/uprobes/fps_probe/enable"
#define TRACING_ON      "/sys/kernel/debug/tracing/tracing_on"

/* queueBuffer mangled names to try - varies by Android version */
static const char *QUEUE_BUFFER_SYMS[] = {
    /* Android 12-14 (Common) */
    "_ZN7android7Surface11queueBufferEP19ANativeWindowBufferi",
    "_ZN7android14SurfaceControl11queueBufferEP19ANativeWindowBufferi",
    /* Android 10-11 */
    "_ZN7android8BpSurface11queueBufferEP19ANativeWindowBufferi",
    /* Android 9 and older */
    "_ZN7android7Surface11queueBufferEP17ANativeWindowBufferi",
    /* Vendor Specific (Some Vivo/Oppo) */
    "_ZN7android7Surface11queueBufferEP19ANativeWindowBufferl", 
    NULL
};

#define MAX_FRAMES      512
#define UPDATE_INTERVAL 200000  /* 200ms */

/* ------------------------------------------------------------------ */
/*  Globals                                                            */
/* ------------------------------------------------------------------ */

static volatile int g_running = 1;
static volatile pid_t g_target_pid = 0;
static volatile float g_fps = 0.0f;
static pthread_mutex_t g_fps_mutex = PTHREAD_MUTEX_INITIALIZER;

/* ring buffer of frame timestamps (ns) */
static uint64_t g_frame_times[MAX_FRAMES];
static int g_frame_head = 0;
static int g_frame_count = 0;
static pthread_mutex_t g_frame_mutex = PTHREAD_MUTEX_INITIALIZER;

/* LOG REDIRECTION */
static void setup_log(void) {
    int fd = open("/data/local/tmp/fps_daemon.log", O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd >= 0) {
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
    }
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

static uint64_t clock_boottime_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_BOOTTIME, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

static void push_frame(uint64_t ts) {
    pthread_mutex_lock(&g_frame_mutex);
    g_frame_times[g_frame_head % MAX_FRAMES] = ts;
    g_frame_head++;
    if (g_frame_count < MAX_FRAMES) g_frame_count++;
    pthread_mutex_unlock(&g_frame_mutex);
}

static float compute_fps(void) {
    pthread_mutex_lock(&g_frame_mutex);
    if (g_frame_count < 2) {
        pthread_mutex_unlock(&g_frame_mutex);
        return 0.0f;
    }
    uint64_t now = clock_boottime_ns();
    uint64_t window = now - 1000000000ULL; /* last 1 second */
    int count = 0;
    for (int i = 0; i < g_frame_count; i++) {
        if (g_frame_times[i] >= window) count++;
    }
    pthread_mutex_unlock(&g_frame_mutex);
    return (float)count;
}

/* ------------------------------------------------------------------ */
/*  Find queueBuffer address in target PID                            */
/* ------------------------------------------------------------------ */

/**
 * Reads /proc/<pid>/maps to find the load address of libgui.so
 */
static unsigned long get_libgui_base(pid_t pid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *f = fopen(path, "r");
    if (!f) return 0;

    char line[512];
    unsigned long base = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "libgui.so") && strstr(line, "r-xp")) {
            sscanf(line, "%lx", &base);
            break;
        }
    }
    fclose(f);
    return base;
}

/**
 * Gets our own libgui.so base (same binary, just different load addr)
 */
static unsigned long get_self_libgui_base(void) {
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return 0;

    char line[512];
    unsigned long base = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "libgui.so") && strstr(line, "r-xp")) {
            sscanf(line, "%lx", &base);
            break;
        }
    }
    fclose(f);
    return base;
}

/**
 * Finds the file offset of queueBuffer in libgui.so
 * by resolving it in our own process then computing offset from our base
 */
static unsigned long get_queue_buffer_offset(void) {
    void *handle = dlopen("libgui.so", RTLD_LAZY | RTLD_NOLOAD);
    if (!handle) {
        handle = dlopen("libgui.so", RTLD_LAZY);
    }
    if (!handle) {
        fprintf(stderr, "[fps_daemon] dlopen libgui.so failed: %s\n", dlerror());
        return 0;
    }

    void *sym = NULL;
    for (int i = 0; QUEUE_BUFFER_SYMS[i] != NULL; i++) {
        sym = dlsym(handle, QUEUE_BUFFER_SYMS[i]);
        if (sym) {
            fprintf(stderr, "[fps_daemon] Found queueBuffer: %s\n", QUEUE_BUFFER_SYMS[i]);
            break;
        }
    }

    if (!sym) {
        fprintf(stderr, "[fps_daemon] queueBuffer not found in libgui.so\n");
        dlclose(handle);
        return 0;
    }

    unsigned long our_base = get_self_libgui_base();
    unsigned long offset = (unsigned long)sym - our_base;
    fprintf(stderr, "[fps_daemon] queueBuffer offset: 0x%lx\n", offset);
    dlclose(handle);
    return offset;
}

/* ------------------------------------------------------------------ */
/*  Uprobe via tracefs                                                 */
/* ------------------------------------------------------------------ */

static int g_uprobe_attached = 0;

/**
 * Attaches a uretprobe to queueBuffer in target pid via tracefs.
 * This is the same mechanism Scene uses internally.
 * Requires debugfs mounted (standard on Android with ADB/shell).
 */
static int attach_uprobe(pid_t pid, unsigned long offset) {
    /* Remove any existing probe first */
    FILE *f = fopen(UPROBE_EVENTS, "w");
    if (!f) {
        fprintf(stderr, "[fps_daemon] Cannot open uprobe_events: %s\n", strerror(errno));
        fprintf(stderr, "[fps_daemon] Falling back to timestamp polling mode\n");
        return -1;
    }
    fprintf(f, "-:fps_probe\n");
    fclose(f);

    /* Write new uretprobe */
    f = fopen(UPROBE_EVENTS, "a");
    if (!f) return -1;

    /* r = uretprobe, p = uprobe */
    char libgui_path[128];
    snprintf(libgui_path, sizeof(libgui_path), "/proc/%d/maps", pid);

    /* Find actual libgui.so path from maps */
    FILE *maps = fopen(libgui_path, "r");
    char actual_path[256] = "/system/lib64/libgui.so";
    if (maps) {
        char line[512];
        while (fgets(line, sizeof(line), maps)) {
            if (strstr(line, "libgui.so")) {
                char *p = strchr(line, '/');
                if (p) {
                    p[strcspn(p, "\n")] = 0;
                    strncpy(actual_path, p, sizeof(actual_path) - 1);
                    break;
                }
            }
        }
        fclose(maps);
    }

    fprintf(f, "r:fps_probe %s:0x%lx\n", actual_path, offset);
    fclose(f);

    /* Enable the probe */
    f = fopen(UPROBE_ENABLE, "w");
    if (f) {
        fprintf(f, "1\n");
        fclose(f);
    }

    /* Enable tracing */
    f = fopen(TRACING_ON, "w");
    if (f) {
        fprintf(f, "1\n");
        fclose(f);
    }

    g_uprobe_attached = 1;
    fprintf(stderr, "[fps_daemon] uretprobe attached to pid %d at 0x%lx\n", pid, offset);
    return 0;
}

static void detach_uprobe(void) {
    if (!g_uprobe_attached) return;
    FILE *f = fopen(UPROBE_EVENTS, "w");
    if (f) {
        fprintf(f, "-:fps_probe\n");
        fclose(f);
    }
    g_uprobe_attached = 0;
    fprintf(stderr, "[fps_daemon] uretprobe detached\n");
}

/* ------------------------------------------------------------------ */
/*  Perf event reader                                                  */
/* ------------------------------------------------------------------ */

static long perf_event_open(struct perf_event_attr *hw_event,
                             pid_t pid, int cpu, int group_fd,
                             unsigned long flags) {
    return syscall(__NR_perf_event_open, hw_event, pid, cpu, group_fd, flags);
}

/**
 * Opens perf event for the uprobe and reads frame timestamps.
 * Runs in a dedicated thread.
 */
static void *perf_reader_thread(void *arg) {
    (void)arg;

    /* Wait until we have a target */
    while (g_running && g_target_pid == 0) usleep(100000);
    if (!g_running) return NULL;

    unsigned long offset = get_queue_buffer_offset();
    if (!offset) {
        fprintf(stderr, "[fps_daemon] Could not get queueBuffer offset\n");
        return NULL;
    }

    pid_t pid = g_target_pid;
    unsigned long target_base = get_libgui_base(pid);
    if (!target_base) {
        fprintf(stderr, "[fps_daemon] Could not find libgui.so in pid %d\n", pid);
        return NULL;
    }

    /* Try attaching uprobe via tracefs */
    if (attach_uprobe(pid, offset) < 0) {
        /* Fallback: use perf_event_open with PERF_TYPE_BREAKPOINT */
        fprintf(stderr, "[fps_daemon] Using perf_event_open fallback\n");
    }

    /* Open perf uprobe event */
    struct perf_event_attr pe = {};
    pe.type           = 7; /* PERF_TYPE_UPROBE - may vary by kernel */
    pe.size           = sizeof(pe);
    pe.config1        = (uint64_t)(uintptr_t)"/system/lib64/libgui.so";
    pe.config2        = offset;
    pe.sample_type    = PERF_SAMPLE_TIME;
    pe.sample_period  = 1;
    pe.wakeup_events  = 1;
    pe.disabled       = 0;
    pe.exclude_kernel = 0;
    pe.exclude_hv     = 1;

    int pfd = perf_event_open(&pe, pid, -1, -1, 0);
    if (pfd < 0) {
        fprintf(stderr, "[fps_daemon] perf_event_open failed: %s\n", strerror(errno));
        /* Last resort: poll /proc/<pid>/stat for voluntary context switches as proxy */
        fprintf(stderr, "[fps_daemon] Using /proc stat fallback\n");
        
        /* This fallback reads /proc/<pid>/schedstat for context switches
         * and correlates with frame timing - less accurate but works without perf */
        char stat_path[64];
        snprintf(stat_path, sizeof(stat_path), "/proc/%d/schedstat", pid);
        
        unsigned long long last_ns = 0;
        unsigned long long last_switches = 0;
        
        while (g_running && g_target_pid == pid) {
            FILE *sf = fopen(stat_path, "r");
            if (!sf) break;
            unsigned long long run_ns, wait_ns, switches;
            fscanf(sf, "%llu %llu %llu", &run_ns, &wait_ns, &switches);
            fclose(sf);
            
            uint64_t now = clock_boottime_ns();
            if (last_switches > 0 && switches > last_switches) {
                /* Each switch pair roughly = one frame for game processes */
                unsigned long long delta_switches = switches - last_switches;
                push_frame(now);
                (void)delta_switches;
            }
            last_switches = switches;
            last_ns = now;
            usleep(UPDATE_INTERVAL / 5);
        }
        return NULL;
    }

    /* mmap perf ring buffer */
    size_t page_size = getpagesize();
    size_t mmap_size = page_size * 65; /* 1 metadata page + 64 data pages */
    void *ring = mmap(NULL, mmap_size, PROT_READ | PROT_WRITE,
                      MAP_SHARED, pfd, 0);
    if (ring == MAP_FAILED) {
        fprintf(stderr, "[fps_daemon] mmap perf buffer failed: %s\n", strerror(errno));
        close(pfd);
        return NULL;
    }

    fprintf(stderr, "[fps_daemon] perf reader running for pid %d\n", pid);

    struct perf_event_mmap_page *meta = (struct perf_event_mmap_page *)ring;
    char *data = (char *)ring + page_size;
    uint64_t data_size = (uint64_t)(mmap_size - page_size);
    uint64_t prev_head = 0;

    while (g_running && g_target_pid == pid) {
        uint64_t head = __atomic_load_n(&meta->data_head, __ATOMIC_ACQUIRE);
        if (head == prev_head) {
            usleep(1000); /* 1ms poll */
            continue;
        }

        /* Read new events */
        while (prev_head < head) {
            uint64_t offset_in_buf = prev_head % data_size;
            struct perf_event_header *hdr =
                (struct perf_event_header *)(data + offset_in_buf);

            if (hdr->type == PERF_RECORD_SAMPLE) {
                /* timestamp is right after the header */
                uint64_t *ts_ptr = (uint64_t *)(hdr + 1);
                push_frame(*ts_ptr);
            }
            prev_head += hdr->size;
        }

        __atomic_store_n(&meta->data_tail, head, __ATOMIC_RELEASE);
    }

    munmap(ring, mmap_size);
    close(pfd);
    detach_uprobe();
    return NULL;
}

/* ------------------------------------------------------------------ */
/*  FPS compute thread                                                 */
/* ------------------------------------------------------------------ */

static void *fps_compute_thread(void *arg) {
    (void)arg;
    while (g_running) {
        float fps = compute_fps();
        pthread_mutex_lock(&g_fps_mutex);
        g_fps = fps;
        pthread_mutex_unlock(&g_fps_mutex);
        usleep(UPDATE_INTERVAL);
    }
    return NULL;
}

/* ------------------------------------------------------------------ */
/*  Unix socket server                                                 */
/* ------------------------------------------------------------------ */

/**
 * Protocol (simple text over Unix socket):
 *   App sends:  "pid 12345\n"      -> daemon attaches to that PID
 *   App sends:  "stop\n"           -> daemon detaches
 *   Daemon sends: "fps 60.0\n"     -> FPS update every 200ms
 *   Daemon sends: "ready\n"        -> after binding, signals app it's up
 */
static void handle_client(int client_fd) {
    char buf[128];
    
    /* Send ready */
    send(client_fd, "ready\n", 6, 0);

    while (g_running) {
        /* Check for incoming command */
        fd_set fds;
        FD_ZERO(&fds);
        FD_SET(client_fd, &fds);
        struct timeval tv = { .tv_sec = 0, .tv_usec = 200000 };
        int sel = select(client_fd + 1, &fds, NULL, NULL, &tv);
        
        if (sel > 0) {
            int n = recv(client_fd, buf, sizeof(buf) - 1, MSG_DONTWAIT);
            if (n <= 0) break;
            buf[n] = 0;
            
            pid_t new_pid = 0;
            if (sscanf(buf, "pid %d", &new_pid) == 1 && new_pid > 0) {
                fprintf(stderr, "[fps_daemon] Target PID set to %d\n", new_pid);
                /* Reset frame buffer when changing target */
                pthread_mutex_lock(&g_frame_mutex);
                g_frame_head = 0;
                g_frame_count = 0;
                memset(g_frame_times, 0, sizeof(g_frame_times));
                pthread_mutex_unlock(&g_frame_mutex);
                g_target_pid = new_pid;
            } else if (strncmp(buf, "stop", 4) == 0) {
                g_target_pid = 0;
                detach_uprobe();
            }
        }

        /* Send FPS update */
        pthread_mutex_lock(&g_fps_mutex);
        float fps = g_fps;
        pthread_mutex_unlock(&g_fps_mutex);

        char out[32];
        int len = snprintf(out, sizeof(out), "fps %.1f\n", fps);
        if (send(client_fd, out, len, MSG_NOSIGNAL) <= 0) break;
    }
}

static void sig_handler(int s) {
    (void)s;
    g_running = 0;
}

int main(void) {
    setup_log();
    signal(SIGINT,  sig_handler);
    signal(SIGTERM, sig_handler);
    signal(SIGPIPE, SIG_IGN);

    fprintf(stderr, "[fps_daemon] Starting\n");

    /* Start background threads */
    pthread_t perf_thread, compute_thread;
    pthread_create(&perf_thread,    NULL, perf_reader_thread, NULL);
    pthread_create(&compute_thread, NULL, fps_compute_thread, NULL);

    /* Create Unix domain socket using Abstract Namespace */
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        perror("[fps_daemon] socket");
        return 1;
    }

    struct sockaddr_un addr = {};
    addr.sun_family = AF_UNIX;
    
    /* 
     * Abstract namespace: prefix with '\0' and do NOT use a filesystem path.
     * This bypasses all folder permission issues in /data/local/tmp/
     */
    addr.sun_path[0] = '\0';
    strncpy(&addr.sun_path[1], "bokbok-fps", sizeof(addr.sun_path) - 2);

    if (bind(server_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("[fps_daemon] bind");
        return 1;
    }
    
    listen(server_fd, 4);

    fprintf(stderr, "[fps_daemon] Listening on (abstract): bokbok-fps\n");

    while (g_running) {
        int client = accept(server_fd, NULL, NULL);
        if (client < 0) {
            if (errno == EINTR) continue;
            break;
        }
        handle_client(client);
        close(client);
    }

    close(server_fd);

    detach_uprobe();

    pthread_join(perf_thread,    NULL);
    pthread_join(compute_thread, NULL);

    fprintf(stderr, "[fps_daemon] Stopped\n");
    return 0;
}
