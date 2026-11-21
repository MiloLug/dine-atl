#ifndef SHARED_GRAVITY_H
#define SHARED_GRAVITY_H

/* Gravity.apply on one axis, left to right (START and END are LEFT and RIGHT), without AXIS_CLIP:
 * the window's position in container px, offset by adjust px from the edge it pulls to, or centred;
 * FILL stretches *size to the container. The y axis takes gravity >> 4. */
int atl_gravity_apply(int gravity, int *size, int container, int adjust);

#endif
