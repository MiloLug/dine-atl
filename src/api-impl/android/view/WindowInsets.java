package android.view;

import android.graphics.Insets;
import android.graphics.Rect;
import android.view.DisplayCutout;
import java.util.List;

public class WindowInsets {

	public static final WindowInsets CONSUMED = new WindowInsets(null, null);

	// the system window insets of Android before API 30: these bars
	private static final int SYSTEM_WINDOW = Type.STATUS_BARS | Type.NAVIGATION_BARS | Type.CAPTION_BAR;

	/* by Type.indexOf, null entries are Insets.NONE; as AOSP 10: a null map is consumed, and
	 * consuming the system window insets drops every type's */
	private final Insets[] typeInsets;
	private final Insets[] maxInsets; // the stable insets' source

	private WindowInsets(Insets[] typeInsets, Insets[] maxInsets) {
		this.typeInsets = typeInsets;
		this.maxInsets = maxInsets;
	}

	public WindowInsets() {
		this(new Insets[Type.SIZE], new Insets[Type.SIZE]);
	}

	public WindowInsets(WindowInsets windowInsets) {
		this(windowInsets.typeInsets, windowInsets.maxInsets);
	}

	public WindowInsets consumeStableInsets() {
		return new WindowInsets(typeInsets, null);
	}

	public WindowInsets consumeSystemWindowInsets() {
		return new WindowInsets(null, maxInsets);
	}

	// consumed insets stay consumed, as on AOSP 10
	public WindowInsets replaceSystemWindowInsets(int left, int top, int right, int bottom) {
		if (typeInsets == null)
			return this;
		return new Builder(this).setSystemWindowInsets(Insets.of(left, top, right, bottom)).build();
	}

	public WindowInsets replaceSystemWindowInsets(Rect systemWindowInsets) {
		return replaceSystemWindowInsets(systemWindowInsets.left, systemWindowInsets.top, systemWindowInsets.right, systemWindowInsets.bottom);
	}

	public Insets getSystemWindowInsets() {
		return getInsets(SYSTEM_WINDOW);
	}

	public int getSystemWindowInsetLeft() {
		return getSystemWindowInsets().left;
	}

	public int getSystemWindowInsetTop() {
		return getSystemWindowInsets().top;
	}

	public int getSystemWindowInsetRight() {
		return getSystemWindowInsets().right;
	}

	public int getSystemWindowInsetBottom() {
		return getSystemWindowInsets().bottom;
	}

	public boolean hasSystemWindowInsets() {
		return !getSystemWindowInsets().equals(Insets.NONE);
	}

	public Insets getStableInsets() {
		return max(maxInsets, SYSTEM_WINDOW);
	}

	public int getStableInsetLeft() {
		return getStableInsets().left;
	}

	public int getStableInsetTop() {
		return getStableInsets().top;
	}

	public int getStableInsetRight() {
		return getStableInsets().right;
	}

	public int getStableInsetBottom() {
		return getStableInsets().bottom;
	}

	public boolean hasStableInsets() {
		return !getStableInsets().equals(Insets.NONE);
	}

	public Insets getSystemGestureInsets() {
		return getInsets(Type.SYSTEM_GESTURES);
	}

	public Insets getMandatorySystemGestureInsets() {
		return getInsets(Type.MANDATORY_SYSTEM_GESTURES);
	}

	public Insets getTappableElementInsets() {
		return getInsets(Type.TAPPABLE_ELEMENT);
	}

	public boolean hasInsets() {
		return !getInsets(~0).equals(Insets.NONE) || !getStableInsets().equals(Insets.NONE);
	}

	private static Insets max(Insets[] map, int typeMask) {
		Insets result = Insets.NONE;
		for (int i = 0; map != null && i < Type.SIZE; i++) {
			if ((typeMask & (1 << i)) != 0 && map[i] != null)
				result = Insets.max(result, map[i]);
		}
		return result;
	}

	public Insets getInsets(int typeMask) {
		return max(typeInsets, typeMask);
	}

	public Insets getInsetsIgnoringVisibility(int typeMask) {
		if ((typeMask & Type.IME) != 0)
			throw new IllegalArgumentException("Unable to query the maximum insets for IME");
		return max(maxInsets, typeMask);
	}

	public DisplayCutout getDisplayCutout() {
		return null;
	}

	public boolean isRound() {
		return false;
	}

	public boolean isConsumed() {
		return typeInsets == null && maxInsets == null;
	}

	// every type but the IME (there is none) is shown
	public boolean isVisible(int typeMask) {
		return (typeMask & Type.IME) == 0;
	}

	public WindowInsets consumeDisplayCutout() {
		return this;
	}

	// a Builder's maps stay consumed (null) until something is set, as on AOSP 10
	public static final class Builder {
		private Insets[] typeInsets;
		private Insets[] maxInsets;

		public Builder() {}

		public Builder(WindowInsets insets) {
			typeInsets = insets.typeInsets != null ? insets.typeInsets.clone() : null;
			maxInsets = insets.maxInsets != null ? insets.maxInsets.clone() : null;
		}

		private static Insets[] set(Insets[] map, int typeMask, Insets insets) {
			if (map == null)
				map = new Insets[Type.SIZE];
			for (int i = 0; i < Type.SIZE; i++) {
				if ((typeMask & (1 << i)) != 0)
					map[i] = insets;
			}
			return map;
		}

		/* Copyright (C) 2019 The Android Open Source Project */
		public Builder setSystemWindowInsets(Insets systemWindowInsets) {
			typeInsets = set(typeInsets, Type.STATUS_BARS, Insets.of(0, systemWindowInsets.top, 0, 0));
			typeInsets = set(typeInsets, Type.NAVIGATION_BARS, Insets.of(systemWindowInsets.left, 0, systemWindowInsets.right, systemWindowInsets.bottom));
			typeInsets = set(typeInsets, Type.CAPTION_BAR, null);
			return this;
		}

		public Builder setSystemGestureInsets(Insets insets) {
			return setInsets(Type.SYSTEM_GESTURES, insets);
		}

		public Builder setMandatorySystemGestureInsets(Insets insets) {
			return setInsets(Type.MANDATORY_SYSTEM_GESTURES, insets);
		}

		public Builder setTappableElementInsets(Insets insets) {
			return setInsets(Type.TAPPABLE_ELEMENT, insets);
		}

		// the bars never hide: an inset is also its maximum
		public Builder setInsets(int typeMask, Insets insets) {
			typeInsets = set(typeInsets, typeMask, insets);
			maxInsets = set(maxInsets, typeMask, insets);
			return this;
		}

		public Builder setInsetsIgnoringVisibility(int typeMask, Insets insets)
		    throws IllegalArgumentException {
			if ((typeMask & Type.IME) != 0)
				throw new IllegalArgumentException("Maximum inset not available for IME");
			maxInsets = set(maxInsets, typeMask, insets);
			return this;
		}

		public Builder setVisible(int typeMask, boolean visible) {
			return this;
		}

		public Builder setStableInsets(Insets stableInsets) {
			maxInsets = set(maxInsets, Type.STATUS_BARS, Insets.of(0, stableInsets.top, 0, 0));
			maxInsets = set(maxInsets, Type.NAVIGATION_BARS, Insets.of(stableInsets.left, 0, stableInsets.right, stableInsets.bottom));
			return this;
		}

		public Builder setDisplayCutout(DisplayCutout displayCutout) {
			return this;
		}

		public Builder setPrivacyIndicatorBounds(Rect bounds) {
			return this;
		}

		public Builder setRound(boolean round) {
			return this;
		}

		public Builder setAlwaysConsumeSystemBars(boolean alwaysConsumeSystemBars) {
			return this;
		}

		public Builder setForceConsumingTypes(int forceConsumingTypes) {
			return this;
		}

		public Builder setBoundingRects(int typeMask, List<Rect> rects) {
			return this;
		}

		public Builder setBoundingRectsIgnoringVisibility(int typeMask, List<Rect> rects) {
			return this;
		}

		public Builder setFrame(int width, int height) {
			return this;
		}

		public WindowInsets build() {
			return new WindowInsets(typeInsets != null ? typeInsets.clone() : null, maxInsets != null ? maxInsets.clone() : null);
		}
	}

	/* Copyright (C) 2014 The Android Open Source Project */
	public static final class Type {

		static final int FIRST = 1 << 0;
		static final int STATUS_BARS = FIRST;
		static final int NAVIGATION_BARS = 1 << 1;
		static final int CAPTION_BAR = 1 << 2;

		static final int IME = 1 << 3;

		static final int SYSTEM_GESTURES = 1 << 4;
		static final int MANDATORY_SYSTEM_GESTURES = 1 << 5;
		static final int TAPPABLE_ELEMENT = 1 << 6;

		static final int DISPLAY_CUTOUT = 1 << 7;

		static final int WINDOW_DECOR = 1 << 8;

		static final int SYSTEM_OVERLAYS = 1 << 9;
		static final int LAST = SYSTEM_OVERLAYS;
		static final int SIZE = 10;

		static final int DEFAULT_VISIBLE = ~IME;

		static int indexOf(@InsetsType int type) {
			switch (type) {
				case STATUS_BARS:
					return 0;
				case NAVIGATION_BARS:
					return 1;
				case CAPTION_BAR:
					return 2;
				case IME:
					return 3;
				case SYSTEM_GESTURES:
					return 4;
				case MANDATORY_SYSTEM_GESTURES:
					return 5;
				case TAPPABLE_ELEMENT:
					return 6;
				case DISPLAY_CUTOUT:
					return 7;
				case WINDOW_DECOR:
					return 8;
				case SYSTEM_OVERLAYS:
					return 9;
				default:
					throw new IllegalArgumentException("type needs to be >= FIRST and <= LAST,"
					                                   + " type=" + type);
			}
		}

		/**
		 * @hide
		 */
		public static String toString(@InsetsType int types) {
			StringBuilder result = new StringBuilder();
			if ((types & STATUS_BARS) != 0) {
				result.append("statusBars ");
			}
			if ((types & NAVIGATION_BARS) != 0) {
				result.append("navigationBars ");
			}
			if ((types & CAPTION_BAR) != 0) {
				result.append("captionBar ");
			}
			if ((types & IME) != 0) {
				result.append("ime ");
			}
			if ((types & SYSTEM_GESTURES) != 0) {
				result.append("systemGestures ");
			}
			if ((types & MANDATORY_SYSTEM_GESTURES) != 0) {
				result.append("mandatorySystemGestures ");
			}
			if ((types & TAPPABLE_ELEMENT) != 0) {
				result.append("tappableElement ");
			}
			if ((types & DISPLAY_CUTOUT) != 0) {
				result.append("displayCutout ");
			}
			if ((types & WINDOW_DECOR) != 0) {
				result.append("windowDecor ");
			}
			if ((types & SYSTEM_OVERLAYS) != 0) {
				result.append("systemOverlays ");
			}
			if (result.length() > 0) {
				result.delete(result.length() - 1, result.length());
			}
			return result.toString();
		}

		private Type() {
		}

		/**
		 * @hide
		 */
		public @interface InsetsType {
		}

		/**
		 * @return An insets type representing any system bars for displaying status.
		 */
		public static @InsetsType int statusBars() {
			return STATUS_BARS;
		}

		/**
		 * @return An insets type representing any system bars for navigation.
		 */
		public static @InsetsType int navigationBars() {
			return NAVIGATION_BARS;
		}

		/**
		 * @return An insets type representing the window of a caption bar.
		 */
		public static @InsetsType int captionBar() {
			return CAPTION_BAR;
		}

		/**
		 * @return An insets type representing the window of an {@link InputMethod}.
		 */
		public static @InsetsType int ime() {
			return IME;
		}

		/**
		 * Returns an insets type representing the system gesture insets.
		 *
		 * <p>The system gesture insets represent the area of a window where system gestures have
		 * priority and may consume some or all touch input, e.g. due to the a system bar
		 * occupying it, or it being reserved for touch-only gestures.
		 *
		 * <p>Simple taps are guaranteed to reach the window even within the system gesture insets,
		 * as long as they are outside the {@link #getSystemWindowInsets() system window insets}.
		 *
		 * <p>When {@link View#SYSTEM_UI_FLAG_LAYOUT_STABLE} is requested, an inset will be returned
		 * even when the system gestures are inactive due to
		 * {@link View#SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN} or
		 * {@link View#SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION}.
		 *
		 * @see #getSystemGestureInsets()
		 */
		public static @InsetsType int systemGestures() {
			return SYSTEM_GESTURES;
		}

		/**
		 * @see #getMandatorySystemGestureInsets
		 */
		public static @InsetsType int mandatorySystemGestures() {
			return MANDATORY_SYSTEM_GESTURES;
		}

		/**
		 * @see #getTappableElementInsets
		 */
		public static @InsetsType int tappableElement() {
			return TAPPABLE_ELEMENT;
		}

		/**
		 * Returns an insets type representing the area that used by {@link DisplayCutout}.
		 *
		 * <p>This is equivalent to the safe insets on {@link #getDisplayCutout()}.
		 *
		 * <p>Note: During dispatch to {@link View#onApplyWindowInsets}, if the window is using
		 * the {@link WindowManager.LayoutParams#LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT default}
		 * {@link WindowManager.LayoutParams#layoutInDisplayCutoutMode}, {@link #getDisplayCutout()}
		 * will return {@code null} even if the window overlaps a display cutout area, in which case
		 * the {@link #displayCutout() displayCutout() inset} will still report the accurate value.
		 *
		 * @see DisplayCutout#getSafeInsetLeft()
		 * @see DisplayCutout#getSafeInsetTop()
		 * @see DisplayCutout#getSafeInsetRight()
		 * @see DisplayCutout#getSafeInsetBottom()
		 */
		public static @InsetsType int displayCutout() {
			return DISPLAY_CUTOUT;
		}

		/**
		 * System overlays represent the insets caused by the system visible elements. Unlike
		 * {@link #navigationBars()} or {@link #statusBars()}, system overlays might not be
		 * hidden by the client.
		 *
		 * For compatibility reasons, this type is included in {@link #systemBars()}. In this
		 * way, views which fit {@link #systemBars()} fit {@link #systemOverlays()}.
		 *
		 * Examples include climate controls, multi-tasking affordances, etc.
		 *
		 * @return An insets type representing the system overlays.
		 */
		public static @InsetsType int systemOverlays() {
			return SYSTEM_OVERLAYS;
		}

		/**
		 * @return All system bars. Includes {@link #statusBars()}, {@link #captionBar()} as well as
		 *         {@link #navigationBars()}, {@link #systemOverlays()}, but not {@link #ime()}.
		 */
		public static @InsetsType int systemBars() {
			return STATUS_BARS | NAVIGATION_BARS | CAPTION_BAR | SYSTEM_OVERLAYS;
		}

		/**
		 * @return Default visible types.
		 *
		 * @hide
		 */
		public static @InsetsType int defaultVisible() {
			return DEFAULT_VISIBLE;
		}

		/**
		 * @return All inset types combined.
		 *
		 * @hide
		 */
		public static @InsetsType int all() {
			return 0xFFFFFFFF;
		}

		/**
		 * @return System bars which can be controlled by {@link View.SystemUiVisibility}.
		 *
		 * @hide
		 */
		public static boolean hasCompatSystemBars(@InsetsType int types) {
			return (types & (STATUS_BARS | NAVIGATION_BARS)) != 0;
		}
	}
}
