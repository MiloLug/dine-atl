/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/com/android/internal/policy/PhoneWindow.java, core/java/android/view/Window.java and
 * core/java/com/android/internal/policy/DecorView.java at android-security-10.0.0_r75, Apache
 * License 2.0; see doc/AOSP.md.
 */
package android.view;

import android.R;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.transition.Transition;
import android.util.AndroidRuntimeException;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.Toolbar;
import java.util.ArrayList;
import java.util.Arrays;

public class Window {
	public static final int FEATURE_OPTIONS_PANEL = 0;
	public static final int FEATURE_NO_TITLE = 1;
	public static final int FEATURE_PROGRESS = 2;
	public static final int FEATURE_LEFT_ICON = 3;
	public static final int FEATURE_RIGHT_ICON = 4;
	public static final int FEATURE_INDETERMINATE_PROGRESS = 5;
	public static final int FEATURE_CONTEXT_MENU = 6;
	public static final int FEATURE_CUSTOM_TITLE = 7;
	public static final int FEATURE_ACTION_BAR = 8;
	public static final int FEATURE_ACTION_BAR_OVERLAY = 9;
	public static final int FEATURE_ACTION_MODE_OVERLAY = 10;
	public static final int FEATURE_SWIPE_TO_DISMISS = 11;
	public static final int FEATURE_CONTENT_TRANSITIONS = 12;
	public static final int FEATURE_ACTIVITY_TRANSITIONS = 13;
	public static final int FEATURE_MAX = FEATURE_ACTIVITY_TRANSITIONS;
	protected static final int DEFAULT_FEATURES = (1 << FEATURE_OPTIONS_PANEL) | (1 << FEATURE_CONTEXT_MENU);
	private static final int CUSTOM_TITLE_COMPATIBLE_FEATURES = DEFAULT_FEATURES | (1 << FEATURE_CUSTOM_TITLE)
	        | (1 << FEATURE_CONTENT_TRANSITIONS) | (1 << FEATURE_ACTIVITY_TRANSITIONS) | (1 << FEATURE_ACTION_MODE_OVERLAY);
	private int features;
	private boolean content_set; // PhoneWindow's mContentParentExplicitlySet
	private CharSequence title;

	public ViewTreeObserver view_tree_observer = null;
	// AOSP keeps one per ViewRootImpl: key tracking (Back on its up) and long presses
	final KeyEvent.DispatcherState key_dispatcher_state = new KeyEvent.DispatcherState();
	private WindowManager.LayoutParams params = new WindowManager.LayoutParams();

	public static interface Callback {
		public void onContentChanged();

		public abstract boolean onCreatePanelMenu(int featureId, Menu menu);

		public View onCreatePanelView(int featureId);

		public boolean onPreparePanel(int featureId, View view, Menu menu);

		public boolean onMenuItemSelected(int featureId, MenuItem item);

		public void onPanelClosed(int featureId, Menu menu);

		public boolean onMenuOpened(int featureId, Menu menu);

		public boolean dispatchKeyEvent(KeyEvent event);

		public boolean dispatchTouchEvent(MotionEvent event);
	}

	public static interface OnFrameMetricsAvailableListener {}

	public long native_window;
	private ViewGroup decorView;
	private final InsetsController insets_controller = new InsetsController(this);

	// the status bar as PhoneWindow.generateLayout sets it up: the theme's values, read once by
	// getDecorView, setContentView or the show, unless the app set them first
	private boolean bars_styled;
	private int forced_flags;
	private int status_bar_color = 0xFF000000;
	private boolean status_bar_color_forced;
	private boolean light_status_bar;
	private boolean light_status_bar_forced;
	private boolean light_by_controller;

	private Window.Callback callback;
	private Context context;

	public Window(Context context, Window.Callback callback) {
		this.callback = callback;
		this.context = context;
		features = getDefaultFeatures(context);
		decorView = new FrameLayout(context);
		decorView.setId(android.R.id.content);
		decorView.decor_window = this;
	}

	public void set_native_window(long native_window) {
		this.native_window = native_window;
	}

	/* A window over the activities (a Dialog's), in windows.c's registry: it shows while its
	 * owner, an activity (AOSP's token), shows and the window is shown; owner null: the app's own
	 * (TYPE_APPLICATION_OVERLAY). Its decor view is the root the ui slot lays out and draws; an
	 * alert has none, the ui slot shows it itself. */
	private long subwindow;
	private boolean window_visible;

	private native long nativeAddSubwindow(Object owner);
	private native void nativeSetSubwindowRoot(long subwindow, long decor, long chrome);
	private native boolean nativeShowSubwindow(long subwindow);
	private native boolean nativeHideSubwindow(long subwindow, boolean remove);
	private static native void nativeFreeSubwindow(long subwindow);
	private static native Window nativeKeyWindow(Context context);

	/** @hide its root and alert come with each show (atlSetSubwindowRoot) */
	public void atlAddSubwindow(Object owner) {
		subwindow = nativeAddSubwindow(owner);
		layoutChanged();
	}

	/** @hide before a show: whether the decor is drawn, and an AlertDialog's alert (its ui-slot
	 * dialog, else 0) drawn around it or alone */
	public void atlSetSubwindowRoot(boolean root, long chrome) {
		nativeSetSubwindowRoot(subwindow, root ? decorView.widget : 0, chrome);
	}

	/** @hide false: no live token (BadTokenException) */
	public boolean atlShowSubwindow() {
		return nativeShowSubwindow(subwindow);
	}

	/** @hide a dismiss; false: the window went with its activity (WindowLeaked) */
	public boolean atlRemoveSubwindow() {
		return nativeHideSubwindow(subwindow, true);
	}

	/** @hide Dialog.hide: the window stays added */
	public void atlHideSubwindow() {
		nativeHideSubwindow(subwindow, false);
	}

	/** @hide from finalize: any thread */
	public void atlFreeSubwindow() {
		if (subwindow != 0)
			nativeFreeSubwindow(subwindow);
		subwindow = 0;
	}

	/** @hide the window keys go to: the topmost shown focusable window over the activities, or null */
	public static Window atlKeyWindow(Context context) {
		return nativeKeyWindow(context);
	}

	/** @hide whether windows.c shows it */
	public boolean atlVisible() {
		return window_visible;
	}

	// windows.c (sub-windows) and activity.c (an activity's): shown or hidden with its token
	private void atlSetVisible(boolean visible) {
		if (window_visible == visible)
			return;
		window_visible = visible;
		if (decorView.isAttachedToWindow())
			decorView.dispatchWindowVisibilityChanged(visible ? View.VISIBLE : View.GONE);
		if (window_visible != visible) // a listener changed it meanwhile
			return;
		if (callback instanceof android.app.Dialog)
			((android.app.Dialog)callback).atlWindowVisible(visible);
	}

	// windows.c: its activity finished while it showed
	private void atlLeaked(Object owner) {
		android.util.Log.e("WindowManager", "android.view.WindowLeaked: Activity " + owner.getClass().getName() + " has leaked window " + decorView + " that was originally added here");
	}

	// a sub-window's place in its app's window, from windows_place; 0, 0 for an activity's
	private int frame_left, frame_top;

	private void atlSetFrame(int left, int top) {
		frame_left = left;
		frame_top = top;
	}

	int atlFrameLeft() {
		return frame_left;
	}

	int atlFrameTop() {
		return frame_top;
	}

	/* Copyright (C) 2006 The Android Open Source Project: closing on a touch outside */
	private boolean mCloseOnTouchOutside = false;
	private boolean mSetCloseOnTouchOutside = false;

	public void setCloseOnTouchOutside(boolean close) {
		mCloseOnTouchOutside = close;
		mSetCloseOnTouchOutside = true;
	}

	public void setCloseOnTouchOutsideIfNotSet(boolean close) {
		if (!mSetCloseOnTouchOutside) {
			mCloseOnTouchOutside = close;
			mSetCloseOnTouchOutside = true;
		}
	}

	public boolean shouldCloseOnTouch(Context context, MotionEvent event) {
		final boolean isOutside = event.getAction() == MotionEvent.ACTION_UP && isOutOfBounds(context, event)
		                          || event.getAction() == MotionEvent.ACTION_OUTSIDE;
		return mCloseOnTouchOutside && isOutside;
	}

	private boolean isOutOfBounds(Context context, MotionEvent event) {
		final int x = (int)event.getX();
		final int y = (int)event.getY();
		final int slop = ViewConfiguration.get(context).getScaledWindowTouchSlop();
		return (x < -slop) || (y < -slop) || (x > (decorView.getWidth() + slop)) || (y > (decorView.getHeight() + slop));
	}

	public void addFlags(int flags) {
		setFlags(flags, flags);
	}

	public void setFlags(int flags, int mask) {
		forced_flags |= mask;
		params.flags = (params.flags & ~mask) | (flags & mask);
		frameChanged();
		layoutChanged();
	}

	public void clearFlags(int flags) {
		setFlags(0, flags);
	}

	public final Callback getCallback() {
		return this.callback;
	}
	public void setCallback(Window.Callback callback) {
		this.callback = callback;
	}

	// the theme's background goes on once, as PhoneWindow's installDecor: a background the app or
	// AlertDialog took off later stays off
	public void setContentView(View view) {
		if (!content_set && decorView.getBackground() == null) {
			TypedArray ta = context.obtainStyledAttributes(new int[] {R.attr.windowBackground});
			if (ta.hasValue(0))
				setBackgroundDrawable(ta.getDrawable(0));
			ta.recycle();
		}
		styleBars();
		decorView.removeAllViews();
		decorView.addView(view);
		content_set = true;
	}

	public View getDecorView() {
		styleBars();
		return decorView;
	}

	ViewGroup atlDecor() {
		return decorView;
	}

	public boolean superDispatchKeyEvent(KeyEvent event) {
		return decorView.dispatchKeyEvent(event);
	}

	public boolean superDispatchTouchEvent(MotionEvent event) {
		return decorView.dispatchTouchEvent(event);
	}

	public View getCurrentFocus() {
		return decorView.findFocus();
	}

	public void takeInputQueue(InputQueue.Callback callback) {
		take_input_queue(native_window, callback, new InputQueue());
	}

	/* Copyright (C) 2006 The Android Open Source Project: Window's and PhoneWindow's feature bits */
	public boolean requestFeature(int featureId) {
		if (content_set)
			throw new AndroidRuntimeException("requestFeature() must be called before adding content");
		final int newFeatures = features | (1 << featureId);
		if ((newFeatures & (1 << FEATURE_CUSTOM_TITLE)) != 0 && (newFeatures & ~CUSTOM_TITLE_COMPATIBLE_FEATURES) != 0)
			throw new AndroidRuntimeException("You cannot combine custom titles with other title features");
		if ((features & (1 << FEATURE_NO_TITLE)) != 0 && featureId == FEATURE_ACTION_BAR)
			return false; // Ignore. No title dominates.
		if ((features & (1 << FEATURE_ACTION_BAR)) != 0 && featureId == FEATURE_NO_TITLE)
			removeFeature(FEATURE_ACTION_BAR); // No title dominates.
		if ((features & (1 << FEATURE_ACTION_BAR)) != 0 && featureId == FEATURE_SWIPE_TO_DISMISS)
			throw new AndroidRuntimeException("You cannot combine swipe dismissal and the action bar.");
		if ((features & (1 << FEATURE_SWIPE_TO_DISMISS)) != 0 && featureId == FEATURE_ACTION_BAR)
			throw new AndroidRuntimeException("You cannot combine swipe dismissal and the action bar.");
		features |= 1 << featureId;
		return (features & (1 << featureId)) != 0;
	}

	protected void removeFeature(int featureId) {
		features &= ~(1 << featureId);
	}

	protected final int getFeatures() {
		return features;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public static int getDefaultFeatures(Context context) {
		int features = 0;
		final android.content.res.Resources res = context.getResources();
		if (res.getBoolean(com.android.internal.R.bool.config_defaultWindowFeatureOptionsPanel))
			features |= 1 << FEATURE_OPTIONS_PANEL;
		if (res.getBoolean(com.android.internal.R.bool.config_defaultWindowFeatureContextMenu))
			features |= 1 << FEATURE_CONTEXT_MENU;
		return features;
	}

	public View findViewById(int id) {
		if (id == com.android.internal.R.id.action_bar)
			return new Toolbar(context);
		return decorView.findViewById(id);
	}

	public View peekDecorView() {
		return null;
	}

	public WindowManager.LayoutParams getAttributes() {
		return params;
	}

	public void setBackgroundDrawable(Drawable drawable) {
		// HACK: disable transparent background for WhatsApp dialogs
		// For some unknown reason, the language picker in WhatsApp doesn't render the BottomSheet background currently.
		// a dialog's background is its own: only an activity's clears the app window's
		if (subwindow == 0 && !"com.whatsapp".equals(context.getPackageName()))
			clear_background(native_window);
		decorView.setBackgroundDrawable(drawable);
		// DecorView's: the window's padding is its background's, none without one
		if (drawable == null || !drawable.getPadding(new Rect()))
			decorView.setPadding(0, 0, 0, 0);
	}

	public void setAttributes(WindowManager.LayoutParams params) {
		this.params = params;
		frameChanged();
		layoutChanged();
	}

	public void takeSurface(SurfaceHolder.Callback2 callback) {}

	public void setStatusBarColor(int color) {
		status_bar_color_forced = true;
		status_bar_color = color;
		frameChanged();
	}

	public void setNavigationBarColor(int color) {}

	public void setFormat(int format) {}

	public void setLayout(int width, int height) {
		params.width = width;
		params.height = height;
		layoutChanged();
	}

	// a sub-window's, once it is one; an activity's window fills the app's
	private void layoutChanged() {
		if (subwindow != 0)
			set_layout(subwindow, params.type, params.flags, params.gravity, params.x, params.y, params.width, params.height, minWidth(), params.dimAmount);
	}

	private final TypedValue min_width_major = new TypedValue(), min_width_minor = new TypedValue();

	/* Copyright (C) 2006 The Android Open Source Project: DecorView.onMeasure's least width of a
	 * floating window, which the window's layout applies to WRAP_CONTENT */
	private int minWidth() {
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		TypedValue tv = metrics.widthPixels < metrics.heightPixels ? min_width_minor : min_width_major;
		if (tv.type == TypedValue.TYPE_DIMENSION)
			return (int)tv.getDimension(metrics);
		if (tv.type == TypedValue.TYPE_FRACTION)
			return (int)tv.getFraction(metrics.widthPixels, metrics.widthPixels);
		return 0;
	}

	public WindowManager getWindowManager() {
		return new WindowManagerImpl();
	}

	public void setSoftInputMode(int dummy) {}

	public int getNavigationBarColor() {
		return 0xFF888888; // gray
	}

	public void setBackgroundDrawableResource(int resId) {
		setBackgroundDrawable(context.getDrawable(resId));
	}

	public int getStatusBarColor() {
		return status_bar_color;
	}

	// by_controller: setSystemBarsAppearance, which takes over from the decor's system UI flags
	void setLightStatusBar(boolean light, boolean by_controller) {
		if (light_by_controller && !by_controller)
			return;
		light_by_controller |= by_controller;
		light_status_bar_forced = true;
		light_status_bar = light;
		frameChanged();
	}

	boolean isLightStatusBar() {
		return light_status_bar;
	}

	WindowInsets rootInsets = new WindowInsets();
	private boolean insets_requested;

	// activity.c calls it when the activity is shown and when the host's insets change
	private void dispatchInsets() {
		insets_requested = false;
		int[] px = get_insets(); // system gestures, mandatory system gestures, navigation bars: l t r b
		rootInsets = new WindowInsets.Builder()
		                 .setInsets(WindowInsets.Type.systemGestures(), Insets.of(px[0], px[1], px[2], px[3]))
		                 .setInsets(WindowInsets.Type.mandatorySystemGestures(), Insets.of(px[4], px[5], px[6], px[7]))
		                 .setInsets(WindowInsets.Type.navigationBars(), Insets.of(px[8], px[9], px[10], px[11]))
		                 .build();
		decorView.dispatchApplyWindowInsets(rootInsets);
		decorView.requestLayout();
	}

	void requestInsets() {
		if (insets_requested)
			return;
		insets_requested = true;
		new Handler(context.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				dispatchInsets();
			}
		});
	}

	private final ArrayList<View> exclusion_views = new ArrayList<View>();
	private int[] exclusion_rects = new int[0]; // px in the window, left top right bottom per rect

	// an attached view in this window has gesture exclusion rects, or no longer
	void exclusionViewChanged(View view, boolean excludes) {
		exclusion_views.remove(view);
		if (excludes)
			exclusion_views.add(view);
		updateExclusion();
	}

	private static boolean shown(View view) {
		for (View v = view; v != null; v = v.parent instanceof View ? (View)v.parent : null) {
			if (v.getVisibility() != View.VISIBLE)
				return false;
		}
		return true;
	}

	// after each layout pass and each scroll; a translation moves views at the next layout pass
	void updateExclusion() {
		ArrayList<View> views = new ArrayList<View>();
		int count = 0;
		for (View view : exclusion_views) {
			if (shown(view)) {
				views.add(view);
				count += view.getSystemGestureExclusionRects().size();
			}
		}
		int[] rects = new int[count * 4];
		int i = 0;
		int[] location = new int[2];
		for (View view : views) {
			view.getLocationInWindow(location);
			for (Rect rect : view.getSystemGestureExclusionRects()) {
				rects[i++] = location[0] + rect.left;
				rects[i++] = location[1] + rect.top;
				rects[i++] = location[0] + rect.right;
				rects[i++] = location[1] + rect.bottom;
			}
		}
		if (Arrays.equals(rects, exclusion_rects))
			return;
		exclusion_rects = rects;
		if (bars_styled) // else the show sends them, with the frame
			set_exclusion_rects(rects);
	}

	private int views_keeping_screen_on;
	void viewKeepsScreenOn(boolean keeps) {
		views_keeping_screen_on += keeps ? 1 : -1;
		frameChanged();
	}

	private boolean view_fullscreen;
	void setViewFullscreen(boolean fullscreen) {
		if (view_fullscreen == fullscreen)
			return;
		view_fullscreen = fullscreen;
		frameChanged();
	}

	private void styleBars() {
		if (bars_styled)
			return;
		bars_styled = true;
		TypedArray a = context.obtainStyledAttributes(com.android.internal.R.styleable.Window);
		if (!status_bar_color_forced)
			status_bar_color = a.getColor(com.android.internal.R.styleable.Window_statusBarColor, 0xFF000000);
		if (!a.getBoolean(com.android.internal.R.styleable.Window_windowIsFloating, false)
		    && context.get_atl_loaded_app().effective_sdk_compat >= 21
		    && a.getBoolean(com.android.internal.R.styleable.Window_windowDrawsSystemBarBackgrounds, false))
			params.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS & ~forced_flags;
		if (!light_status_bar_forced)
			light_status_bar = a.getBoolean(com.android.internal.R.styleable.Window_windowLightStatusBar, false);
		if (a.getBoolean(com.android.internal.R.styleable.Window_windowFullscreen, false))
			params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN & ~forced_flags;
		/* Copyright (C) 2006 The Android Open Source Project: PhoneWindow.generateLayout's floating
		 * window, dim and minimum width */
		boolean floating = a.getBoolean(com.android.internal.R.styleable.Window_windowIsFloating, false);
		if (floating) {
			params.width = WindowManager.LayoutParams.WRAP_CONTENT;
			params.height = WindowManager.LayoutParams.WRAP_CONTENT;
			params.flags &= ~(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR) | forced_flags;
		}
		if (a.getBoolean(com.android.internal.R.styleable.Window_backgroundDimEnabled, floating)) {
			if ((forced_flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0)
				params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			if (!dim_amount_set)
				params.dimAmount = a.getFloat(com.android.internal.R.styleable.Window_backgroundDimAmount, 0.5f);
		}
		/* Copyright (C) 2006 The Android Open Source Project: PhoneWindow.generateLayout's features */
		if (a.getBoolean(com.android.internal.R.styleable.Window_windowNoTitle, false))
			requestFeature(FEATURE_NO_TITLE);
		else if (a.getBoolean(com.android.internal.R.styleable.Window_windowActionBar, false))
			requestFeature(FEATURE_ACTION_BAR); // Don't allow an action bar if there is no title.
		if (a.getBoolean(com.android.internal.R.styleable.Window_windowActionBarOverlay, false))
			requestFeature(FEATURE_ACTION_BAR_OVERLAY);
		if (a.getBoolean(com.android.internal.R.styleable.Window_windowActionModeOverlay, false))
			requestFeature(FEATURE_ACTION_MODE_OVERLAY);
		a.getValue(com.android.internal.R.styleable.Window_windowMinWidthMajor, min_width_major);
		a.getValue(com.android.internal.R.styleable.Window_windowMinWidthMinor, min_width_minor);
		for (TypedValue min : new TypedValue[] {min_width_major, min_width_minor}) {
			if (min.type == TypedValue.TYPE_REFERENCE && min.resourceId != 0) // @dimen/dialog_min_width_*
				context.getResources().getValue(min.resourceId, min, true);
		}
		a.recycle();
		layoutChanged();
	}

	// activity.c calls it by name (handle_cache) when the activity is shown
	private void updateFrame() {
		styleBars();
		set_frame((params.flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0, status_bar_color, light_status_bar,
		          view_fullscreen || (params.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0);
		set_keep_screen_on(views_keeping_screen_on > 0 || (params.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
		set_screen_brightness(params.screenBrightness);
		set_exclusion_rects(exclusion_rects);
		set_title(title != null ? title.toString() : context.getPackageName());
	}

	// before styleBars the activity's show publishes the frame
	private void frameChanged() {
		if (bars_styled)
			updateFrame();
	}

	public Context getContext() {
		return context;
	}

	public boolean hasFeature(int featureId) {
		return (features & (1 << featureId)) != 0;
	}

	public void setTitle(CharSequence title) {
		this.title = title;
		frameChanged();
	}

	public Transition getSharedElementEnterTransition() {
		return new Transition();
	}

	public void setSharedElementExitTransition(Transition transition) {}

	public void setSharedElementReenterTransition(Transition transition) {}

	public void setSharedElementReturnTransition(Transition transition) {}

	public Transition getSharedElementExitTransition() {
		return new Transition();
	}

	public Transition getSharedElementReenterTransition() {
		return new Transition();
	}

	public void setReturnTransition(Transition transition) {}

	public void setEnterTransition(Transition transition) {}

	public void setGravity(int gravity) {
		params.gravity = gravity;
		layoutChanged();
	}

	public void setDecorFitsSystemWindows(boolean fits) {}

	public WindowInsetsController getInsetsController() {
		return insets_controller;
	}

	public void setStatusBarContrastEnforced(boolean enforced) {}

	public void setNavigationBarContrastEnforced(boolean enforced) {}

	public void setSharedElementEnterTransition(Transition transition) {}

	public Transition getEnterTransition() {
		return null;
	}

	public void setWindowAnimations(int resId) {}

	private boolean dim_amount_set;

	public void setType(int type) {
		params.type = type;
		layoutChanged();
	}

	public void setDimAmount(float dimAmount) {
		params.dimAmount = dimAmount;
		dim_amount_set = true;
		layoutChanged();
	}

	private native void set_title(String title);
	public native void take_input_queue(long native_window, InputQueue.Callback callback, InputQueue queue);
	private native void set_layout(long subwindow, int type, int flags, int gravity, int x, int y, int width, int height, int min_width, float dim_amount);
	private native void clear_background(long native_window);
	private native void set_frame(boolean has_color, int color, boolean light, boolean fullscreen);
	private native void set_keep_screen_on(boolean keep);
	private native void set_exclusion_rects(int[] rects);
	private native int[] get_insets();
	private native void set_screen_brightness(float brightness);
}
