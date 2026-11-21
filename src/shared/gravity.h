#ifndef SHARED_GRAVITY_H
#define SHARED_GRAVITY_H

struct atl_window_frame {
	int left, top, width, height; // px in the app's window
};

/* Gravity.apply on one axis, left to right (START and END are LEFT and RIGHT), without AXIS_CLIP:
 * the window's position in container px, offset by adjust px from the edge it pulls to, or centred;
 * FILL stretches *size to the container. The y axis takes gravity >> 4. */
int atl_gravity_apply(int gravity, int *size, int container, int adjust);
/* Gravity.apply and Gravity.applyDisplay on both axes: where a window width by height goes in
 * container at the gravity's x, y; then moved inside display, or filling it when bigger, or cut to it
 * on an axis with DISPLAY_CLIP. */
struct atl_window_frame atl_gravity_place(int gravity, int width, int height, int x, int y, const struct atl_window_frame *container,
                                          const struct atl_window_frame *display);

#endif
