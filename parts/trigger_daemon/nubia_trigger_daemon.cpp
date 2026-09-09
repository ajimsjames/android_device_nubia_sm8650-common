/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <sched.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <android/log.h>

#define LOG_TAG "NubiaTriggerDaemon"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); printf(__VA_ARGS__); printf("\n"); fflush(stdout)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr)

#define SLOT_LEFT   8
#define SLOT_RIGHT  9
#define TRACK_LEFT  1008
#define TRACK_RIGHT 1009

static int touch_fd = -1;
static int sar0_fd = -1;
static int sar1_fd = -1;
static int slider_fd = -1;

static volatile int left_x = 400, left_y = 300, left_mode = 0, left_cps = 12;
static volatile int right_x = 2000, right_y = 300, right_mode = 0, right_cps = 12;
static volatile int trigger_enabled = 1;
static volatile int app_filter_enabled = 0;
static volatile int is_foreground_allowed = 1;
static volatile int slider_state = 0;
static char selected_apps_buf[512] = "";

static volatile int is_left_down = 0;
static volatile int is_right_down = 0;

static inline void write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    if (fd < 0) return;
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    write(fd, &ev, sizeof(ev));
}

static void *haptic_worker_thread(void *arg) {
    (void)arg;
    system("cmd vibrator_manager synced oneshot 25 220 2>/dev/null");
    return NULL;
}

static inline void trigger_haptic_feedback_async() {
    pthread_t tid;
    pthread_create(&tid, NULL, haptic_worker_thread, NULL);
    pthread_detach(tid);
}

static inline void map_coords(int in_x, int in_y, int *raw_x, int *raw_y) {
    if (in_x > 1116) {
        // Landscape input (in_x is along 2480 width, in_y is along 1116 height)
        *raw_x = (1116 - in_y) * 16;
        *raw_y = in_x * 16;
    } else {
        // Portrait input (in_x is along 1116 width, in_y is along 2480 height)
        *raw_x = in_x * 16;
        *raw_y = in_y * 16;
    }

    if (*raw_x < 0) *raw_x = 0;
    if (*raw_x > 17856) *raw_x = 17856;
    if (*raw_y < 0) *raw_y = 0;
    if (*raw_y > 39680) *raw_y = 39680;
}

static int g_track_seq = 1000;

static inline void inject_touch_down(int is_left) {
    if (touch_fd < 0) return;
    int slot = is_left ? SLOT_LEFT : SLOT_RIGHT;
    int x = is_left ? left_x : right_x;
    int y = is_left ? left_y : right_y;
    int rx = 0, ry = 0;
    map_coords(x, y, &rx, &ry);

    int trk = ++g_track_seq;
    if (g_track_seq > 60000) g_track_seq = 1000;

    write_event(touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    write_event(touch_fd, EV_ABS, ABS_MT_TRACKING_ID, trk);
    write_event(touch_fd, EV_ABS, ABS_MT_POSITION_X, rx);
    write_event(touch_fd, EV_ABS, ABS_MT_POSITION_Y, ry);
    write_event(touch_fd, EV_ABS, ABS_MT_TOUCH_MAJOR, 120);
    write_event(touch_fd, EV_SYN, SYN_REPORT, 0);

    LOGI("Touch DOWN on Slot %d (trk %d) at stored (%d, %d) -> raw (%d, %d)", slot, trk, x, y, rx, ry);
}

static inline void inject_touch_up(int is_left) {
    if (touch_fd < 0) return;
    int slot = is_left ? SLOT_LEFT : SLOT_RIGHT;

    write_event(touch_fd, EV_ABS, ABS_MT_SLOT, slot);
    write_event(touch_fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
    write_event(touch_fd, EV_SYN, SYN_REPORT, 0);

    LOGI("Touch UP on Slot %d", slot);
}

static void *rapid_fire_left_thread(void *arg) {
    (void)arg;
    while (is_left_down && trigger_enabled && is_foreground_allowed) {
        int cps = left_cps > 0 ? left_cps : 12;
        int sleep_us = (1000000 / cps) / 2;
        inject_touch_down(1);
        trigger_haptic_feedback_async();
        usleep(sleep_us);
        inject_touch_up(1);
        usleep(sleep_us);
    }
    return NULL;
}

static void *rapid_fire_right_thread(void *arg) {
    (void)arg;
    while (is_right_down && trigger_enabled && is_foreground_allowed) {
        int cps = right_cps > 0 ? right_cps : 12;
        int sleep_us = (1000000 / cps) / 2;
        inject_touch_down(0);
        trigger_haptic_feedback_async();
        usleep(sleep_us);
        inject_touch_up(0);
        usleep(sleep_us);
    }
    return NULL;
}

static inline void handle_trigger_event(int is_left, int pressed) {
    if (!trigger_enabled || !is_foreground_allowed) return;

    if (is_left) {
        if (is_left_down == pressed) return;
        is_left_down = pressed;
    } else {
        if (is_right_down == pressed) return;
        is_right_down = pressed;
    }

    int mode = is_left ? left_mode : right_mode;

    if (mode == 0 || mode == 1) { // Normal or Hold
        if (pressed) {
            inject_touch_down(is_left);
            trigger_haptic_feedback_async();
        } else {
            inject_touch_up(is_left);
        }
    } else if (mode == 2) { // Rapid Fire
        if (pressed) {
            pthread_t tid;
            pthread_create(&tid, NULL, is_left ? rapid_fire_left_thread : rapid_fire_right_thread, NULL);
            pthread_detach(tid);
        }
    } else if (mode == 3) { // Dual action
        inject_touch_down(is_left);
        trigger_haptic_feedback_async();
        usleep(25000);
        inject_touch_up(is_left);
    }
}

static void update_diablo_notification() {
    FILE *fp = popen("cat /sys/kernel/fan/fan_speed_count 2>/dev/null", "r");
    char rpm_buf[32] = "20200";
    if (fp) { if (fgets(rpm_buf, sizeof(rpm_buf), fp)) { rpm_buf[strcspn(rpm_buf, "\r\n")] = 0; } pclose(fp); }

    char cmd[512];
    snprintf(cmd, sizeof(cmd),
        "cmd notification post -S bigtext -t \"Diablo Mode Active 🔥\" diablo_tag \"CPU: 3.40 GHz Max Turbo | GPU: 1.0 GHz Turbo | Fan: Level 5 (%s RPM) | Triggers: ON (L1/R1 Active)\" 2>/dev/null &",
        rpm_buf);
    system(cmd);
}

static void apply_diablo_hardware(int enable) {
    if (enable) {
        LOGI("🔥 DIABLO / COMPETITIVE MODE ENGAGED via Red Switch!");
        system("echo 3398400 > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq 2>/dev/null");
        system("echo 3148800 > /sys/devices/system/cpu/cpufreq/policy5/scaling_min_freq 2>/dev/null");
        system("echo 3148800 > /sys/devices/system/cpu/cpufreq/policy2/scaling_min_freq 2>/dev/null");
        system("echo 1804800 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null");
        system("echo 3398400 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq 2>/dev/null");
        system("echo 3148800 > /sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq 2>/dev/null");
        system("echo 3148800 > /sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq 2>/dev/null");
        system("echo 2265600 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null");
        system("echo 2 > /proc/sys/walt/sched_boost 2>/dev/null");
        system("echo 50 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel 2>/dev/null");
        system("echo 1000 > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null");
        system("echo 1 > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null");
        system("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null");
        system("echo 1 > /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null");
        system("echo 1 > /sys/class/kgsl/kgsl-3d0/force_no_nap 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null");
        system("echo 1 > /sys/kernel/fan/fan_enable 2>/dev/null");
        system("echo 5 > /sys/kernel/fan/fan_speed_level 2>/dev/null");
        trigger_enabled = 1;
        system("settings put global nubia_parts_trigger_enable 1 2>/dev/null");
        system("settings put global nubia_parts_nubia_power_profile 3 2>/dev/null");
        system("settings put system peak_refresh_rate 120.0 2>/dev/null");
        system("settings put system min_refresh_rate 120.0 2>/dev/null");
        update_diablo_notification();
    } else {
        LOGI("⚖️ COMPETITIVE MODE RESTORED to Balanced via Green Switch.");
        system("echo 960000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq 2>/dev/null");
        system("echo 960000 > /sys/devices/system/cpu/cpufreq/policy5/scaling_min_freq 2>/dev/null");
        system("echo 960000 > /sys/devices/system/cpu/cpufreq/policy2/scaling_min_freq 2>/dev/null");
        system("echo 691200 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null");
        system("echo 0 > /proc/sys/walt/sched_boost 2>/dev/null");
        system("echo 0 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null");
        system("echo 13 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null");
        system("echo 80 > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null");
        system("echo 0 > /sys/class/kgsl/kgsl-3d0/force_no_nap 2>/dev/null");
        system("echo 1 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null");
        system("echo 1 > /sys/kernel/fan/fan_speed_level 2>/dev/null");
        system("settings put global nubia_parts_nubia_power_profile 1 2>/dev/null");
        system("cmd notification cancel diablo_tag 2>/dev/null &");
    }
}

static void handle_slider_event(uint16_t code, int32_t value) {
    if (code == KEY_RED) {
        if (value == 1 && slider_state != 1) {
            slider_state = 1;
            LOGI("🔴 Slider Switch -> RED (Competitive / Diablo Mode ON)");
            apply_diablo_hardware(1);
        } else if (value == 0 && slider_state != 0) {
            slider_state = 0;
            LOGI("🟢 Slider Switch -> GREEN (Normal / Balanced Mode)");
            apply_diablo_hardware(0);
        }
    } else if (code == KEY_GREEN) {
        if (value == 1 && slider_state != 0) {
            slider_state = 0;
            LOGI("🟢 Slider Switch -> GREEN (Normal / Balanced Mode)");
            apply_diablo_hardware(0);
        } else if (value == 0 && slider_state != 1) {
            slider_state = 1;
            LOGI("🔴 Slider Switch -> RED (Competitive / Diablo Mode ON)");
            apply_diablo_hardware(1);
        }
    }
}

static void *config_updater_thread(void *arg) {
    (void)arg;
    char buf[512];
    while (1) {
        FILE *fp = popen("settings get global nubia_parts_trigger_enable 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) trigger_enabled = atoi(buf); pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_app_filter_mode 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) app_filter_enabled = atoi(buf); pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_selected_apps 2>/dev/null", "r");
        if (fp) {
            if (fgets(buf, sizeof(buf), fp)) {
                buf[strcspn(buf, "\r\n")] = 0;
                strncpy(selected_apps_buf, buf, sizeof(selected_apps_buf) - 1);
            }
            pclose(fp);
        }

        if (app_filter_enabled && strlen(selected_apps_buf) > 0) {
            // Query current focused window package
            fp = popen("dumpsys window 2>/dev/null | grep -E \"mCurrentFocus\"", "r");
            if (fp) {
                if (fgets(buf, sizeof(buf), fp)) {
                    buf[strcspn(buf, "\r\n")] = 0;
                    // Check if current focused window contains any selected app
                    int match = 0;
                    char temp_apps[512];
                    strncpy(temp_apps, selected_apps_buf, sizeof(temp_apps) - 1);
                    char *token = strtok(temp_apps, ",");
                    while (token != NULL) {
                        if (strlen(token) > 0 && strstr(buf, token) != NULL) {
                            match = 1;
                            break;
                        }
                        token = strtok(NULL, ",");
                    }
                    is_foreground_allowed = match;
                } else {
                    is_foreground_allowed = 0;
                }
                pclose(fp);
            }
        } else {
            is_foreground_allowed = 1;
        }

        fp = popen("settings get global nubia_parts_trigger_left_x 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) { int val = atoi(buf); if (val > 0) left_x = val; } pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_left_y 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) { int val = atoi(buf); if (val > 0) left_y = val; } pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_right_x 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) { int val = atoi(buf); if (val > 0) right_x = val; } pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_right_y 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) { int val = atoi(buf); if (val > 0) right_y = val; } pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_left_mode 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) left_mode = atoi(buf); pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_right_mode 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) right_mode = atoi(buf); pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_left_cps 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) { int val = atoi(buf); if (val > 0) left_cps = val; } pclose(fp); }

        fp = popen("settings get global nubia_parts_trigger_right_cps 2>/dev/null", "r");
        if (fp) { if (fgets(buf, sizeof(buf), fp)) { int val = atoi(buf); if (val > 0) right_cps = val; } pclose(fp); }

        sleep(1);
    }
    return NULL;
}

int main(int argc, char **argv) {
    (void)argc;
    (void)argv;
    LOGI("=== Nubia Hardware Trigger & Slider Daemon Started ===");

    struct sched_param param;
    param.sched_priority = 98;
    sched_setscheduler(0, SCHED_FIFO, &param);

    system("echo 1 > /proc/nubia_key/sar0/mode_operation 2>/dev/null");
    system("echo 1 > /proc/nubia_key/sar1/mode_operation 2>/dev/null");

    touch_fd = open("/dev/input/event8", O_RDWR);
    if (touch_fd < 0) {
        LOGE("Failed to open touch screen /dev/input/event8: %s", strerror(errno));
    } else {
        LOGI("Opened touchscreen /dev/input/event8 successfully");
    }

    sar0_fd = open("/dev/input/event7", O_RDONLY | O_NONBLOCK);
    if (sar0_fd < 0) {
        LOGE("Failed to open SAR0 /dev/input/event7: %s", strerror(errno));
    } else {
        LOGI("Opened SAR0 (Left Trigger) /dev/input/event7 successfully");
    }

    sar1_fd = open("/dev/input/event6", O_RDONLY | O_NONBLOCK);
    if (sar1_fd < 0) {
        LOGE("Failed to open SAR1 /dev/input/event6: %s", strerror(errno));
    } else {
        LOGI("Opened SAR1 (Right Trigger) /dev/input/event6 successfully");
    }

    slider_fd = open("/dev/input/event0", O_RDONLY | O_NONBLOCK);
    if (slider_fd < 0) {
        LOGE("Failed to open Slider Switch /dev/input/event0: %s", strerror(errno));
    } else {
        LOGI("Opened Slider Switch /dev/input/event0 successfully");
        uint8_t key_b[(KEY_MAX + 7) / 8];
        memset(key_b, 0, sizeof(key_b));
        if (ioctl(slider_fd, EVIOCGKEY(sizeof(key_b)), key_b) >= 0) {
            int is_red = (key_b[KEY_RED / 8] & (1 << (KEY_RED % 8))) != 0;
            LOGI("Initial Slider Switch Hardware State: %s", is_red ? "RED (Diablo Mode ON)" : "GREEN (Normal Mode)");
            if (is_red) {
                slider_state = 1;
                apply_diablo_hardware(1);
            }
        }
    }

    pthread_t config_tid;
    pthread_create(&config_tid, NULL, config_updater_thread, NULL);
    pthread_detach(config_tid);

    struct pollfd fds[3];
    fds[0].fd = sar0_fd;
    fds[0].events = POLLIN;
    fds[1].fd = sar1_fd;
    fds[1].events = POLLIN;
    fds[2].fd = slider_fd;
    fds[2].events = POLLIN;

    while (1) {
        int ret = poll(fds, 3, -1);
        if (ret > 0) {
            struct input_event ev;
            if (fds[0].revents & POLLIN) {
                while (read(sar0_fd, &ev, sizeof(ev)) > 0) {
                    if (ev.type == EV_KEY || ev.type == EV_ABS) {
                        handle_trigger_event(1, ev.value > 0);
                    }
                }
            }
            if (fds[1].revents & POLLIN) {
                while (read(sar1_fd, &ev, sizeof(ev)) > 0) {
                    if (ev.type == EV_KEY || ev.type == EV_ABS) {
                        handle_trigger_event(0, ev.value > 0);
                    }
                }
            }
            if (fds[2].revents & POLLIN) {
                while (read(slider_fd, &ev, sizeof(ev)) > 0) {
                    if (ev.type == EV_KEY) {
                        handle_slider_event(ev.code, ev.value);
                    }
                }
            }
        }
    }

    if (touch_fd >= 0) close(touch_fd);
    if (sar0_fd >= 0) close(sar0_fd);
    if (sar1_fd >= 0) close(sar1_fd);
    if (slider_fd >= 0) close(slider_fd);
    return 0;
}
