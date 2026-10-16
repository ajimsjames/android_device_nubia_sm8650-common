#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/input.h>
#include <poll.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <time.h>

#define EVENT_SAR0 "/dev/input/event7"
#define EVENT_SAR1 "/dev/input/event6"
#define TOUCH_DEV  "/dev/input/event8"
#define VIBE_DEV   "/dev/input/event2"

#define RAW_MAX_X 17856
#define RAW_MAX_Y 39680

#define SLOT_L1 8
#define SLOT_R1 9
#define TRK_L1  9008
#define TRK_R1  9009

static int touch_fd = -1;
static int vibe_fd = -1;
static struct ff_effect rumble_effect;

// Shared configuration parameters (updated by settings polling thread)
static volatile int g_enabled = 1;
static volatile int g_l1_pos_x = 24;
static volatile int g_l1_pos_y = 20;
static volatile int g_r1_pos_x = 80;
static volatile int g_r1_pos_y = 20;
static volatile int g_rapid_l1 = 0;
static volatile int g_rapid_r1 = 0;
static volatile int g_rapid_rate = 10;
static volatile int g_haptic = 1;
static volatile int g_rotation = 0; // 0=PORTRAIT, 1=ROT_90 (landscape), 2=ROT_180, 3=ROT_270

// Tracking active slots and physical screen fingers
static int s_l1_down = 0;
static int s_r1_down = 0;
static int s_screen_fingers = 0;
static int s_current_physical_slot = 0;

// Transform on-screen percentages into raw digitizer coordinates according to display rotation
static void pct_to_raw(int pct_x, int pct_y, int rot, int* out_raw_x, int* out_raw_y) {
    if (rot == 1) {
        // Landscape ROTATION_90 (display: 2480x1116)
        // Android maps: disp_x = raw_y / 16, disp_y = (RAW_MAX_X - raw_x) / 16
        // So: raw_x = RAW_MAX_X - (disp_y * 16), raw_y = disp_x * 16
        *out_raw_x = RAW_MAX_X - ((pct_y * RAW_MAX_X) / 100);
        *out_raw_y = (pct_x * RAW_MAX_Y) / 100;
    } else if (rot == 3) {
        // Reverse Landscape ROTATION_270
        // Android maps: disp_x = (RAW_MAX_Y - raw_y) / 16, disp_y = raw_x / 16
        *out_raw_x = (pct_y * RAW_MAX_X) / 100;
        *out_raw_y = RAW_MAX_Y - ((pct_x * RAW_MAX_Y) / 100);
    } else if (rot == 2) {
        // Reverse Portrait ROTATION_180
        *out_raw_x = RAW_MAX_X - ((pct_x * RAW_MAX_X) / 100);
        *out_raw_y = RAW_MAX_Y - ((pct_y * RAW_MAX_Y) / 100);
    } else {
        // Standard Portrait ROTATION_0 (display: 1116x2480)
        *out_raw_x = (pct_x * RAW_MAX_X) / 100;
        *out_raw_y = (pct_y * RAW_MAX_Y) / 100;
    }
}

static void write_ev(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    write(fd, &ev, sizeof(ev));
}

static void trigger_vibrate() {
    if (vibe_fd >= 0) {
        struct input_event play;
        memset(&play, 0, sizeof(play));
        play.type = EV_FF;
        play.code = rumble_effect.id;
        play.value = 1;
        write(vibe_fd, &play, sizeof(play));
    }
}

static void init_vibrator() {
    vibe_fd = open(VIBE_DEV, O_RDWR);
    if (vibe_fd >= 0) {
        memset(&rumble_effect, 0, sizeof(rumble_effect));
        rumble_effect.type = FF_RUMBLE;
        rumble_effect.id = -1;
        rumble_effect.u.rumble.strong_magnitude = 0xFFFF;
        rumble_effect.u.rumble.weak_magnitude = 0xFFFF;
        rumble_effect.replay.length = 35;
        rumble_effect.replay.delay = 0;
        if (ioctl(vibe_fd, EVIOCSFF, &rumble_effect) < 0) {
            close(vibe_fd);
            vibe_fd = -1;
        }
    }
}

static void slot_touch_down(int slot, int trk, int x, int y) {
    if (touch_fd < 0) return;
    if (slot == SLOT_L1) s_l1_down = 1;
    if (slot == SLOT_R1) s_r1_down = 1;

    write_ev(touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    write_ev(touch_fd, EV_ABS, ABS_MT_TRACKING_ID, trk);
    write_ev(touch_fd, EV_ABS, ABS_MT_TOUCH_MAJOR, 50);
    write_ev(touch_fd, EV_ABS, ABS_MT_POSITION_X, x);
    write_ev(touch_fd, EV_ABS, ABS_MT_POSITION_Y, y);
    // If no physical fingers on screen, ensure BTN_TOUCH is 1 so Android dispatches the touch
    write_ev(touch_fd, EV_KEY, BTN_TOUCH, 1);
    // Crucial: Restore previous physical slot so subsequent driver events don't get routed to slot 8/9!
    if (s_screen_fingers != 0) {
        write_ev(touch_fd, EV_ABS, ABS_MT_SLOT, s_current_physical_slot);
    }
    write_ev(touch_fd, EV_SYN, SYN_REPORT, 0);
}

static void slot_touch_up(int slot) {
    if (touch_fd < 0) return;
    if (slot == SLOT_L1) s_l1_down = 0;
    if (slot == SLOT_R1) s_r1_down = 0;

    write_ev(touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    write_ev(touch_fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
    // Only drop BTN_TOUCH if NEITHER triggers NOR screen fingers are active
    if (!s_l1_down && !s_r1_down && s_screen_fingers == 0) {
        write_ev(touch_fd, EV_KEY, BTN_TOUCH, 0);
    }
    // Crucial: Restore previous physical slot
    if (s_screen_fingers != 0) {
        write_ev(touch_fd, EV_ABS, ABS_MT_SLOT, s_current_physical_slot);
    }
    write_ev(touch_fd, EV_SYN, SYN_REPORT, 0);
}

static int read_setting(const char* key, int default_val) {
    char cmd[128];
    snprintf(cmd, sizeof(cmd), "settings get global %s 2>/dev/null", key);
    FILE* fp = popen(cmd, "r");
    if (!fp) return default_val;
    char buf[64];
    if (fgets(buf, sizeof(buf), fp)) {
        pclose(fp);
        if (strncmp(buf, "null", 4) == 0) return default_val;
        return atoi(buf);
    }
    pclose(fp);
    return default_val;
}

// Background thread for settings
static void* settings_watcher_thread(void* arg) {
    (void)arg;
    while (1) {
        g_enabled = read_setting("nubia_parts_trigger_enable", 1);
        g_l1_pos_x = read_setting("nubia_parts_trigger_l1_pos_x", 24);
        g_l1_pos_y = read_setting("nubia_parts_trigger_l1_pos_y", 20);
        g_r1_pos_x = read_setting("nubia_parts_trigger_r1_pos_x", 80);
        g_r1_pos_y = read_setting("nubia_parts_trigger_r1_pos_y", 20);

        g_rapid_l1 = read_setting("nubia_parts_trigger_rapid_l1", 0);
        g_rapid_r1 = read_setting("nubia_parts_trigger_rapid_r1", 0);
        int rate = read_setting("nubia_parts_trigger_rapid_rate", 10);
        if (rate < 5) rate = 10;
        g_rapid_rate = rate;
        g_haptic = read_setting("nubia_parts_trigger_haptic", 1);
        g_rotation = read_setting("nubia_parts_trigger_rotation", 0);

        sleep(1);
    }
    return NULL;
}

int main() {
    touch_fd = open(TOUCH_DEV, O_RDWR);
    if (touch_fd < 0) {
        perror("open touch_dev");
        return 1;
    }

    init_vibrator();

    int fd_sar0 = open(EVENT_SAR0, O_RDONLY | O_NONBLOCK);
    int fd_sar1 = open(EVENT_SAR1, O_RDONLY | O_NONBLOCK);

    if (fd_sar0 < 0 || fd_sar1 < 0) {
        perror("open sar event fds");
        return 1;
    }

    pthread_t th;
    pthread_create(&th, NULL, settings_watcher_thread, NULL);
    pthread_detach(th);

    struct pollfd fds[3];
    fds[0].fd = fd_sar0;
    fds[0].events = POLLIN;
    fds[1].fd = fd_sar1;
    fds[1].events = POLLIN;
    fds[2].fd = touch_fd;
    fds[2].events = POLLIN;

    int l1_active = 0, r1_active = 0;
    int l1_pulse = 0, r1_pulse = 0;
    uint64_t l1_last_pulse_ms = 0;
    uint64_t r1_last_pulse_ms = 0;

    int current_touch_slot = 0;
    struct timespec ts;

    while (1) {
        // Poll sar0, sar1, and touchscreen events with 2ms timeout
        int ret = poll(fds, 3, 2);

        clock_gettime(CLOCK_MONOTONIC, &ts);
        uint64_t now_ms = (uint64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;

        if (!g_enabled) {
            if (l1_active) { slot_touch_up(SLOT_L1); l1_active = 0; }
            if (r1_active) { slot_touch_up(SLOT_R1); r1_active = 0; }
            usleep(50000);
            continue;
        }

        // Monitor physical touchscreen slot state (slots 0..7)
        if (ret > 0 && (fds[2].revents & POLLIN)) {
            struct input_event tev;
            while (read(touch_fd, &tev, sizeof(tev)) == sizeof(tev)) {
                if (tev.type == EV_ABS) {
                    if (tev.code == ABS_MT_SLOT) {
                        current_touch_slot = tev.value;
                        if (current_touch_slot < 8) {
                            s_current_physical_slot = current_touch_slot;
                        }
                    } else if (tev.code == ABS_MT_TRACKING_ID && current_touch_slot < 8) {
                        if (tev.value >= 0) {
                            s_screen_fingers |= (1 << current_touch_slot);
                            s_current_physical_slot = current_touch_slot;
                        } else {
                            s_screen_fingers &= ~(1 << current_touch_slot);
                        }
                    }
                }
            }
        }

        int raw_l1_x, raw_l1_y, raw_r1_x, raw_r1_y;
        pct_to_raw(g_l1_pos_x, g_l1_pos_y, g_rotation, &raw_l1_x, &raw_l1_y);
        pct_to_raw(g_r1_pos_x, g_r1_pos_y, g_rotation, &raw_r1_x, &raw_r1_y);

        // Sar0 (L1)
        if (ret > 0 && (fds[0].revents & POLLIN)) {
            struct input_event ev;
            while (read(fd_sar0, &ev, sizeof(ev)) == sizeof(ev)) {
                if (ev.type == EV_KEY) {
                    if (ev.value == 1) { // Pressed
                        l1_active = 1;
                        l1_pulse = 1;
                        l1_last_pulse_ms = now_ms;
                        if (g_haptic) trigger_vibrate();
                        slot_touch_down(SLOT_L1, TRK_L1, raw_l1_x, raw_l1_y);
                    } else if (ev.value == 0) { // Released
                        l1_active = 0;
                        l1_pulse = 0;
                        slot_touch_up(SLOT_L1);
                    }
                }
            }
        }

        // Sar1 (R1)
        if (ret > 0 && (fds[1].revents & POLLIN)) {
            struct input_event ev;
            while (read(fd_sar1, &ev, sizeof(ev)) == sizeof(ev)) {
                if (ev.type == EV_KEY) {
                    if (ev.value == 1) { // Pressed
                        r1_active = 1;
                        r1_pulse = 1;
                        r1_last_pulse_ms = now_ms;
                        if (g_haptic) trigger_vibrate();
                        slot_touch_down(SLOT_R1, TRK_R1, raw_r1_x, raw_r1_y);
                    } else if (ev.value == 0) { // Released
                        r1_active = 0;
                        r1_pulse = 0;
                        slot_touch_up(SLOT_R1);
                    }
                }
            }
        }

        int pulse_interval_ms = 1000 / (g_rapid_rate * 2);
        if (pulse_interval_ms < 10) pulse_interval_ms = 10;

        // Rapid fire pulsation during L1 trigger hold
        if (l1_active && g_rapid_l1) {
            if (now_ms - l1_last_pulse_ms >= (uint64_t)pulse_interval_ms) {
                l1_last_pulse_ms = now_ms;
                l1_pulse = !l1_pulse;
                if (l1_pulse) {
                    slot_touch_down(SLOT_L1, TRK_L1, raw_l1_x, raw_l1_y);
                    if (g_haptic) trigger_vibrate();
                } else {
                    slot_touch_up(SLOT_L1);
                }
            }
        }

        // Rapid fire pulsation during R1 trigger hold
        if (r1_active && g_rapid_r1) {
            if (now_ms - r1_last_pulse_ms >= (uint64_t)pulse_interval_ms) {
                r1_last_pulse_ms = now_ms;
                r1_pulse = !r1_pulse;
                if (r1_pulse) {
                    slot_touch_down(SLOT_R1, TRK_R1, raw_r1_x, raw_r1_y);
                    if (g_haptic) trigger_vibrate();
                } else {
                    slot_touch_up(SLOT_R1);
                }
            }
        }
    }

    close(fd_sar0);
    close(fd_sar1);
    close(touch_fd);
    if (vibe_fd >= 0) close(vibe_fd);
    return 0;
}
