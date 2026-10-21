package android.view;

import android.content.Context;

/**
 * default values are mainly based on AOSPs defaults; the scaled ones are dp, scaled like AOSP's.
 */
public class ViewConfiguration {
	private final float density;

	private ViewConfiguration(Context context) {
		density = context != null ? context.getResources().getDisplayMetrics().density : 1;
	}

	public static ViewConfiguration get(Context context) {
		return new ViewConfiguration(context);
	}

	private int scaled(int dp) {
		return (int)(density * dp + 0.5f);
	}

	public int getScaledTouchSlop() {
		return scaled(8);
	}

	public int getScaledMaximumFlingVelocity() {
		return scaled(8000);
	}

	public int getScaledMinimumFlingVelocity() {
		return scaled(50);
	}

	public static int getTapTimeout() {
		return 100;
	}

	public static int getPressedStateDuration() {
		return 64;
	}

	public static int getLongPressTimeout() {
		return 400;
	}

	public int getScaledPagingTouchSlop() {
		return scaled(16);
	}

	public boolean hasPermanentMenuKey() {
		return false;
	}

	public static int getDoubleTapTimeout() {
		return 300;
	}

	public int getScaledDoubleTapSlop() {
		return scaled(100);
	}

	public static float getScrollFriction() {
		return 0.015f;
	}

	public int getScaledEdgeSlop() {
		return scaled(12);
	}

	public int getScaledWindowTouchSlop() {
		return scaled(8);
	}

	public float getScaledHorizontalScrollFactor() {
		return 1;
	}

	public float getScaledVerticalScrollFactor() {
		return 1;
	}

	public static int getScrollBarFadeDuration() {
		return 100;
	}

	public static int getScrollDefaultDelay() {
		return 16;
	}

	public boolean shouldShowMenuShortcutsWhenKeyboardPresent() {
		return true;
	}

	public static int getKeyRepeatTimeout() {
		return 1000;
	}

	public static int getKeyRepeatDelay() {
		return 100;
	}
}
