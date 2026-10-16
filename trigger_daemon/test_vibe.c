#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <linux/input.h>

int main() {
    int fd = open("/dev/input/event2", O_RDWR);
    if (fd < 0) { perror("open event2"); return 1; }
    
    struct ff_effect effect;
    effect.type = FF_RUMBLE;
    effect.id = -1;
    effect.u.rumble.strong_magnitude = 0xFFFF;
    effect.u.rumble.weak_magnitude = 0xFFFF;
    effect.replay.length = 50;
    effect.replay.delay = 0;

    if (ioctl(fd, EVIOCSFF, &effect) < 0) {
        perror("ioctl EVIOCSFF");
        close(fd);
        return 1;
    }

    struct input_event play;
    play.type = EV_FF;
    play.code = effect.id;
    play.value = 1;
    write(fd, &play, sizeof(play));

    usleep(60000);
    close(fd);
    printf("Haptic pulse sent via awinic_haptic\n");
    return 0;
}
