/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/view/WindowManager.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.view;

import android.graphics.PixelFormat;
import android.os.IBinder;
import android.text.TextUtils;

public interface WindowManager {
	public android.view.Display getDefaultDisplay();

	public void addView(View view, ViewGroup.LayoutParams params);

	public void updateViewLayout(View view, ViewGroup.LayoutParams params);

	public void removeView(View view);

	public void removeViewImmediate(View view);

	/* Copyright (C) 2006 The Android Open Source Project: the public constants, fields and
	 * constructors, without parcelling and copyFrom */
	public static class LayoutParams extends ViewGroup.LayoutParams {
		public static final int FIRST_APPLICATION_WINDOW = 1;
		public static final int TYPE_BASE_APPLICATION = 1;
		public static final int TYPE_APPLICATION = 2;
		public static final int TYPE_APPLICATION_STARTING = 3;
		public static final int TYPE_DRAWN_APPLICATION = 4;
		public static final int LAST_APPLICATION_WINDOW = 99;
		public static final int FIRST_SUB_WINDOW = 1000;
		public static final int TYPE_APPLICATION_PANEL = FIRST_SUB_WINDOW;
		public static final int TYPE_APPLICATION_MEDIA = FIRST_SUB_WINDOW + 1;
		public static final int TYPE_APPLICATION_SUB_PANEL = FIRST_SUB_WINDOW + 2;
		public static final int TYPE_APPLICATION_ATTACHED_DIALOG = FIRST_SUB_WINDOW + 3;
		public static final int LAST_SUB_WINDOW = 1999;
		public static final int FIRST_SYSTEM_WINDOW = 2000;
		public static final int TYPE_STATUS_BAR = FIRST_SYSTEM_WINDOW;
		public static final int TYPE_SEARCH_BAR = FIRST_SYSTEM_WINDOW + 1;
		public static final int TYPE_PHONE = FIRST_SYSTEM_WINDOW + 2;
		public static final int TYPE_SYSTEM_ALERT = FIRST_SYSTEM_WINDOW + 3;
		public static final int TYPE_KEYGUARD = FIRST_SYSTEM_WINDOW + 4;
		public static final int TYPE_TOAST = FIRST_SYSTEM_WINDOW + 5;
		public static final int TYPE_SYSTEM_OVERLAY = FIRST_SYSTEM_WINDOW + 6;
		public static final int TYPE_PRIORITY_PHONE = FIRST_SYSTEM_WINDOW + 7;
		public static final int TYPE_SYSTEM_DIALOG = FIRST_SYSTEM_WINDOW + 8;
		public static final int TYPE_KEYGUARD_DIALOG = FIRST_SYSTEM_WINDOW + 9;
		public static final int TYPE_SYSTEM_ERROR = FIRST_SYSTEM_WINDOW + 10;
		public static final int TYPE_INPUT_METHOD = FIRST_SYSTEM_WINDOW + 11;
		public static final int TYPE_INPUT_METHOD_DIALOG = FIRST_SYSTEM_WINDOW + 12;
		public static final int TYPE_WALLPAPER = FIRST_SYSTEM_WINDOW + 13;
		public static final int TYPE_STATUS_BAR_PANEL = FIRST_SYSTEM_WINDOW + 14;
		public static final int TYPE_PRIVATE_PRESENTATION = FIRST_SYSTEM_WINDOW + 30;
		public static final int TYPE_ACCESSIBILITY_OVERLAY = FIRST_SYSTEM_WINDOW + 32;
		public static final int TYPE_APPLICATION_OVERLAY = FIRST_SYSTEM_WINDOW + 38;
		public static final int LAST_SYSTEM_WINDOW = 2999;

		@Deprecated
		public static final int MEMORY_TYPE_NORMAL = 0;
		@Deprecated
		public static final int MEMORY_TYPE_HARDWARE = 1;
		@Deprecated
		public static final int MEMORY_TYPE_GPU = 2;
		@Deprecated
		public static final int MEMORY_TYPE_PUSH_BUFFERS = 3;

		public static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 0x00000001;
		public static final int FLAG_DIM_BEHIND = 0x00000002;
		@Deprecated
		public static final int FLAG_BLUR_BEHIND = 0x00000004;
		public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
		public static final int FLAG_NOT_TOUCHABLE = 0x00000010;
		public static final int FLAG_NOT_TOUCH_MODAL = 0x00000020;
		@Deprecated
		public static final int FLAG_TOUCHABLE_WHEN_WAKING = 0x00000040;
		public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
		public static final int FLAG_LAYOUT_IN_SCREEN = 0x00000100;
		public static final int FLAG_LAYOUT_NO_LIMITS = 0x00000200;
		public static final int FLAG_FULLSCREEN = 0x00000400;
		public static final int FLAG_FORCE_NOT_FULLSCREEN = 0x00000800;
		@Deprecated
		public static final int FLAG_DITHER = 0x00001000;
		public static final int FLAG_SECURE = 0x00002000;
		public static final int FLAG_SCALED = 0x00004000;
		public static final int FLAG_IGNORE_CHEEK_PRESSES = 0x00008000;
		public static final int FLAG_LAYOUT_INSET_DECOR = 0x00010000;
		public static final int FLAG_ALT_FOCUSABLE_IM = 0x00020000;
		public static final int FLAG_WATCH_OUTSIDE_TOUCH = 0x00040000;
		@Deprecated
		public static final int FLAG_SHOW_WHEN_LOCKED = 0x00080000;
		public static final int FLAG_SHOW_WALLPAPER = 0x00100000;
		@Deprecated
		public static final int FLAG_TURN_SCREEN_ON = 0x00200000;
		@Deprecated
		public static final int FLAG_DISMISS_KEYGUARD = 0x00400000;
		public static final int FLAG_SPLIT_TOUCH = 0x00800000;
		public static final int FLAG_HARDWARE_ACCELERATED = 0x01000000;
		public static final int FLAG_LAYOUT_IN_OVERSCAN = 0x02000000;
		public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
		public static final int FLAG_TRANSLUCENT_NAVIGATION = 0x08000000;
		public static final int FLAG_LOCAL_FOCUS_MODE = 0x10000000;
		public static final int FLAG_LAYOUT_ATTACHED_IN_DECOR = 0x40000000;
		public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;

		public static final int SOFT_INPUT_MASK_STATE = 0x0f;
		public static final int SOFT_INPUT_STATE_UNSPECIFIED = 0;
		public static final int SOFT_INPUT_STATE_UNCHANGED = 1;
		public static final int SOFT_INPUT_STATE_HIDDEN = 2;
		public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3;
		public static final int SOFT_INPUT_STATE_VISIBLE = 4;
		public static final int SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5;
		public static final int SOFT_INPUT_MASK_ADJUST = 0xf0;
		public static final int SOFT_INPUT_ADJUST_UNSPECIFIED = 0x00;
		public static final int SOFT_INPUT_ADJUST_RESIZE = 0x10;
		public static final int SOFT_INPUT_ADJUST_PAN = 0x20;
		public static final int SOFT_INPUT_ADJUST_NOTHING = 0x30;
		public static final int SOFT_INPUT_IS_FORWARD_NAVIGATION = 0x100;

		public static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;
		public static final float BRIGHTNESS_OVERRIDE_OFF = 0.0f;
		public static final float BRIGHTNESS_OVERRIDE_FULL = 1.0f;

		public static final int ROTATION_ANIMATION_ROTATE = 0;
		public static final int ROTATION_ANIMATION_CROSSFADE = 1;
		public static final int ROTATION_ANIMATION_JUMPCUT = 2;
		public static final int ROTATION_ANIMATION_SEAMLESS = 3;

		public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT = 0;
		public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1;
		public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER = 2;

		public int x;
		public int y;
		public float horizontalWeight;
		public float verticalWeight;
		public int type;
		@Deprecated
		public int memoryType;
		public int flags;
		public int softInputMode;
		public int gravity;
		public float horizontalMargin;
		public float verticalMargin;
		public int format;
		public int windowAnimations;
		public float alpha = 1.0f;
		public float dimAmount = 1.0f;
		public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
		public float buttonBrightness = BRIGHTNESS_OVERRIDE_NONE;
		public int rotationAnimation = ROTATION_ANIMATION_ROTATE;
		public IBinder token = null;
		public String packageName = null;
		public int screenOrientation = -1; // ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		public float preferredRefreshRate;
		public int preferredDisplayModeId;
		public int systemUiVisibility;
		public int layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;

		private CharSequence mTitle = null;

		public LayoutParams() {
			super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			type = TYPE_APPLICATION;
			format = PixelFormat.OPAQUE;
		}

		public LayoutParams(int _type) {
			super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			type = _type;
			format = PixelFormat.OPAQUE;
		}

		public LayoutParams(int _type, int _flags) {
			super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			type = _type;
			flags = _flags;
			format = PixelFormat.OPAQUE;
		}

		public LayoutParams(int _type, int _flags, int _format) {
			super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			type = _type;
			flags = _flags;
			format = _format;
		}

		public LayoutParams(int w, int h, int _type, int _flags, int _format) {
			super(w, h);
			type = _type;
			flags = _flags;
			format = _format;
		}

		public LayoutParams(int w, int h, int xpos, int ypos, int _type, int _flags, int _format) {
			super(w, h);
			x = xpos;
			y = ypos;
			type = _type;
			flags = _flags;
			format = _format;
		}

		public final void setTitle(CharSequence title) {
			if (null == title)
				title = "";
			mTitle = TextUtils.stringOrSpannedString(title);
		}

		public final CharSequence getTitle() {
			return mTitle != null ? mTitle : "";
		}

		public static boolean mayUseInputMethod(int flags) {
			switch (flags & (FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM)) {
			case 0:
			case FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM:
				return true;
			}
			return false;
		}
	}
}
