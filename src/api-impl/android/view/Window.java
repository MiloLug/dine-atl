/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/com/android/internal/policy/PhoneWindow.java, core/java/android/view/Window.java,
 * core/java/com/android/internal/policy/DecorView.java and core/java/android/view/ViewRootImpl.java
 * at android-security-10.0.0_r75, Apache License 2.0; see doc/AOSP.md.
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
	private View root; // the view the window shows: its decor, or a WindowManager.addView window's view
	private boolean added; // a WindowManager.addView window

	/** @hide AOSP's window token (View.getWindowToken): it stands for this Window */
	public static final class Token extends android.os.Binder {
		final Window window;

		Token(Window window) {
			this.window = window;
		}
	}
	final Token token = new Token(this);
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
		root = decorView;
	}

	// WindowManagerImpl.addView's: the view is the root, the theme sets nothing, the params all
	private Window(Context context, View view, WindowManager.LayoutParams params) {
		this(context, (Callback)null);
		root = view;
		view.decor_window = this;
		added = true;
		bars_styled = true;
		this.params = params;
	}

	public void set_native_window(long native_window) {
		this.native_window = native_window;
	}

	/* A window over the activities (a Dialog's, or WindowManager.addView's), in windows.c's
	 * registry: it shows while its owner, an activity (AOSP's token), shows and the window is shown;
	 * owner null: the app's own (TYPE_APPLICATION_OVERLAY, TYPE_TOAST). A sub-panel (PopupWindow)
	 * is attached to a parent window and goes with it. Its root view is what the ui slot lays out
	 * and draws; an alert has none, the ui slot shows it itself. */
	private long subwindow;
	private boolean window_visible;
	private Object owner;
	private Window panel_parent; // a sub-panel's window: a Dialog's, a popup's or an activity's

	private native long nativeAddSubwindow(Object owner, long parent, View root);
	private native void nativeSetSubwindowRoot(long subwindow, long decor, long chrome);
	private native boolean nativeShowSubwindow(long subwindow);
	private native boolean nativeHideSubwindow(long subwindow, boolean remove);
	private static native void nativeFreeSubwindow(long subwindow);
	private static native Window nativeKeyWindow(Context context);

	/** @hide its root and alert come with each show (atlSetSubwindowRoot) */
	public void atlAddSubwindow(Object owner) {
		this.owner = owner;
		subwindow = nativeAddSubwindow(owner, panel_parent != null ? panel_parent.subwindow : 0, root); // an activity's: 0
		if (view_tree_observer != null)
			view_tree_observer.atlWindowAdded(native_window);
		layoutChanged();
		frameChanged(); // what the theme asked before, kept for the show
	}

	/** @hide before a show: whether the decor is drawn, and an AlertDialog's alert (its ui-slot
	 * dialog, else 0) drawn around it or alone */
	public void atlSetSubwindowRoot(boolean root, long chrome) {
		nativeSetSubwindowRoot(subwindow, root ? this.root.widget : 0, chrome);
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

	// its tree's passes count: an activity's always, one over the activities while shown
	boolean atlDrawn() {
		return subwindow == 0 || window_visible;
	}

	// windows.c (sub-windows) and activity.c (an activity's): shown or hidden with its token
	private void atlSetVisible(boolean visible) {
		if (window_visible == visible)
			return;
		window_visible = visible;
		if (visible && subwindow != 0)
			requestInsets();
		if (root.isAttachedToWindow())
			root.dispatchWindowVisibilityChanged(visible ? View.VISIBLE : View.GONE);
		if (window_visible != visible) // a listener changed it meanwhile
			return;
		if (callback instanceof android.app.Dialog)
			((android.app.Dialog)callback).atlWindowVisible(visible);
	}

	// windows.c: its activity finished while it showed
	private void atlLeaked(Object owner) {
		android.util.Log.e("WindowManager", "android.view.WindowLeaked: Activity " + owner.getClass().getName() + " has leaked window " + root + " that was originally added here");
	}

	// a sub-window's place in its app's window, from windows_place; none for an activity's
	private int frame_left, frame_top, frame_width, frame_height;

	// inside a layout pass: insets clipped by the frame follow in a post
	private void atlSetFrame(int left, int top, int width, int height) {
		if (frame_left == left && frame_top == top && frame_width == width && frame_height == height)
			return;
		frame_left = left;
		frame_top = top;
		frame_width = width;
		frame_height = height;
		if (subwindow != 0)
			requestInsets(false);
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
		return (x < -slop) || (y < -slop) || (x > (root.getWidth() + slop)) || (y > (root.getHeight() + slop));
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

	View atlDecor() {
		return root;
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
		frameChanged();
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

	private WindowManagerImpl window_manager;

	// an Activity's: windows it adds take its token (AOSP's Window.setWindowManager)
	public WindowManager getWindowManager() {
		if (window_manager == null)
			window_manager = new WindowManagerImpl(context, this);
		return window_manager;
	}

	/* Copyright (C) 2006 The Android Open Source Project: Window.adjustLayoutParamsForSubWindow,
	 * the token a window added through this one's WindowManager takes */
	void adjustLayoutParamsForSubWindow(WindowManager.LayoutParams wp) {
		if (wp.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW && wp.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
			if (wp.token == null)
				wp.token = token;
		} else if (wp.type >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW && wp.type <= WindowManager.LayoutParams.LAST_SYSTEM_WINDOW) {
			// system windows take no token
		} else if (wp.token == null) {
			wp.token = token;
		}
	}

	/** @hide WindowManagerImpl.addView: view becomes the root of a window, with AOSP's token rules
	 * (WindowManagerService.addWindow) */
	static void atlAddView(Context context, View view, WindowManager.LayoutParams params) {
		Window token_window = params.token instanceof Token ? ((Token)params.token).window : null;
		Object owner = null;
		Window parent = null;
		if (params.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW && params.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
			if (token_window == null) // a parent that went: nativeShowSubwindow's
				throw new WindowManager.BadTokenException("Unable to add window -- token " + params.token + " is not valid; is your activity running?");
			parent = token_window;
			owner = token_window.subwindow != 0 ? token_window.owner : token_window.callback;
		} else if (params.type < WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) {
			if (token_window == null || !(token_window.callback instanceof android.app.Activity))
				throw new WindowManager.BadTokenException("Unable to add window -- token " + params.token + " is not valid; is your activity running?");
			owner = token_window.callback;
		}
		Window window = new Added(context, view, params);
		window.panel_parent = parent;
		window.atlAddSubwindow(owner);
		view.parent = new WindowViewParent();
		view.setLayoutParams(params);
		view.dispatchAttachedToWindow();
		window.nativeSetSubwindowRoot(window.subwindow, view.widget, 0);
		if (!window.atlShowSubwindow()) {
			view.dispatchDetachedFromWindow();
			window.atlRemoveView();
			throw new WindowManager.BadTokenException("Unable to add window -- token " + params.token + " is not valid; is your activity running?");
		}
	}

	// only an added window has a finalizer: a Dialog frees its own
	private static final class Added extends Window {
		Added(Context context, View view, WindowManager.LayoutParams params) {
			super(context, view, params);
		}

		@Override
		protected void finalize() throws Throwable {
			try {
				atlFreeSubwindow();
			} finally {
				super.finalize();
			}
		}
	}

	/** @hide the Window of a view added through WindowManagerImpl, else null */
	static Window atlAddedWindow(View view) {
		Window window = view.decor_window;
		return window != null && window.added && window.root == view ? window : null;
	}

	/** @hide WindowManagerImpl.updateViewLayout */
	void atlUpdateLayout(WindowManager.LayoutParams params) {
		root.setLayoutParams(params);
		setAttributes(params);
	}

	/** @hide WindowManagerImpl.removeView */
	void atlRemoveView() {
		if (root.isAttachedToWindow())
			root.dispatchDetachedFromWindow();
		if (view_tree_observer != null) // this window shows no more: its passes' observations go
			view_tree_observer.atlWindowRemoved();
		nativeHideSubwindow(subwindow, true);
		root.decor_window = null;
		root.parent = null;
	}

	/* An added view's parent, AOSP's ViewRootImpl in this: added views have one (PopupWindow removes
	 * only those), the windows.c pass lays them out, focus stays in their tree. */
	private static final class WindowViewParent implements ViewParent {
		public ViewParent getParent() {
			return null;
		}

		public boolean isLayoutRequested() {
			return false;
		}

		public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

		public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
			return false;
		}

		public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
			return false;
		}

		public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
			return false;
		}

		public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {}

		public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {}

		public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {}

		public void onStopNestedScroll(View target) {}

		public void onDescendantInvalidated(View child, View target) {}

		public void requestLayout() {}

		public void requestChildFocus(View child, View focused) {}

		public void clearChildFocus(View child) {}

		public View focusSearch(View v, int direction) {
			return null;
		}

		public void focusableViewAvailable(View v) {}

		public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
			return false;
		}
	}

	/** @hide the window a sub-panel is attached to, else null: View.getApplicationWindowToken */
	Window atlPanelParent() {
		return panel_parent;
	}

	/* Copyright (C) 2006 The Android Open Source Project: Window.setSoftInputMode */
	private boolean has_soft_input_mode;

	public void setSoftInputMode(int mode) {
		if (mode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
			params.softInputMode = mode;
			has_soft_input_mode = true;
		} else {
			has_soft_input_mode = false;
		}
		requestInsets();
	}

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
	private boolean insets_requested, insets_forced;
	private int[] insets_px;

	// activity.c and windows.c call it when the window is shown and when the host's insets change
	private void dispatchInsets() {
		dispatchInsets(true);
	}

	// unforced: only when they changed, as a frame move would loop a layout pass otherwise
	private void dispatchInsets(boolean forced) {
		insets_requested = false;
		insets_forced = false;
		sendSoftInput(resolvedAdjust(), soft_pan); // the activity's insets are clipped to its resized room
		// system gestures, mandatory system gestures, navigation bars, status bars, IME: l t r b
		int[] px = get_insets(subwindow, frame_left, frame_top, frame_width, frame_height);
		if (!forced && Arrays.equals(px, insets_px)) {
			updatePan();
			return;
		}
		insets_px = px;
		Insets ime = Insets.of(px[16], px[17], px[18], px[19]);
		WindowInsets.Builder insets = new WindowInsets.Builder()
		                                  .setInsets(WindowInsets.Type.systemGestures(), Insets.of(px[0], px[1], px[2], px[3]))
		                                  .setInsets(WindowInsets.Type.mandatorySystemGestures(), Insets.of(px[4], px[5], px[6], px[7]))
		                                  .setInsets(WindowInsets.Type.ime(), ime)
		                                  .setVisible(WindowInsets.Type.ime(), !ime.equals(Insets.NONE));
		bars_hidden = 0;
		bar(insets, WindowInsets.Type.navigationBars(), Insets.of(px[8], px[9], px[10], px[11]), barsHideNavigation());
		bar(insets, WindowInsets.Type.statusBars(), Insets.of(px[12], px[13], px[14], px[15]), barsFullscreen());
		rootInsets = insets.build();
		root.dispatchApplyWindowInsets(rootInsets);
		root.requestLayout();
		updatePan();
	}

	private int bars_hidden; // as the last insets took them: 1 the status bar, 2 the navigation bar

	// a hidden bar takes no room, but keeps its maximum (getInsetsIgnoringVisibility)
	private void bar(WindowInsets.Builder insets, int type, Insets value, boolean hidden) {
		insets.setInsets(type, hidden ? Insets.NONE : value).setInsetsIgnoringVisibility(type, value).setVisible(type, !hidden);
		if (hidden)
			bars_hidden |= type == WindowInsets.Type.statusBars() ? 1 : 2;
	}

	private int soft_adjust, soft_pan; // as last sent

	/* Copyright (C) 2006 The Android Open Source Project: ViewRootImpl.performTraversals' resize
	 * mode; unspecified resizes while a scroll container shows */
	private int resolvedAdjust() {
		int adjust = params.softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
		if (adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
			return adjust;
		return hasShownScrollContainer(root) ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
	}

	private static boolean hasShownScrollContainer(View view) {
		if (view.getVisibility() != View.VISIBLE)
			return false;
		if (view.isScrollContainer())
			return true;
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup)view;
			for (int i = 0; i < group.getChildCount(); i++) {
				if (hasShownScrollContainer(group.getChildAt(i)))
					return true;
			}
		}
		return false;
	}

	// a change lays the window out again in its new room, or moved
	private void sendSoftInput(int adjust, int pan) {
		if (adjust == soft_adjust && pan == soft_pan)
			return;
		soft_adjust = adjust;
		soft_pan = pan;
		set_soft_input(subwindow, adjust, pan);
		root.requestLayout();
	}

	/* Copyright (C) 2006 The Android Open Source Project: ViewRootImpl.scrollToRectOrFocus, for the
	 * focused view only: the pan that shows it above the keyboard, none when it can't fit */
	private void updatePan() {
		int ime = insets_px != null ? insets_px[19] : 0;
		int pan = 0;
		if (ime > 0 && soft_adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) {
			pan = soft_pan;
			View focus = root.findFocus();
			if (focus != null) {
				int[] location = new int[2];
				focus.atlLocationInRoot(location);
				int top = location[1], bottom = top + focus.getHeight();
				int visible = root.getHeight() - ime;
				if (bottom - top <= visible)
					pan = top < 0 ? top : bottom > visible ? bottom - visible : 0;
			}
		}
		sendSoftInput(soft_adjust, pan);
	}

	/** @hide AOSP's mCurScrollY: how far the keyboard's pan moved the root up */
	int atlPan() {
		return soft_pan;
	}

	// the root's layout pass ended: its views have their frames
	void atlLaidOut() {
		updateExclusion();
		updatePan();
		if (subwindow == 0 && soft_adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
			requestInsets(false); // clipped to the room it now has
	}

	// a view in it took the focus
	void atlFocusMoved() {
		updatePan();
	}

	void requestInsets() {
		requestInsets(true);
	}

	private void requestInsets(boolean forced) {
		insets_forced |= forced;
		if (insets_requested)
			return;
		insets_requested = true;
		new Handler(context.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				dispatchInsets(insets_forced);
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
			set_exclusion_rects(subwindow, rects);
	}

	private int views_keeping_screen_on;
	void viewKeepsScreenOn(boolean keeps) {
		views_keeping_screen_on += keeps ? 1 : -1;
		frameChanged();
	}

	// a view's setSystemUiVisibility; its views hear of a change, in a post as AOSP's
	private int view_system_ui;
	void setViewSystemUi(int flags) {
		if (view_system_ui == flags)
			return;
		view_system_ui = flags;
		frameChanged();
		new Handler(context.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				root.dispatchSystemUiVisibilityChanged(view_system_ui);
			}
		});
	}

	/** @hide View.getWindowSystemUiVisibility */
	int atlSystemUi() {
		return view_system_ui;
	}

	// InsetsController's: the bar types it hides, and how they come back
	private int controller_hidden;
	private int bars_behavior;
	void setControllerHidden(int types, boolean hidden) {
		controller_hidden = hidden ? controller_hidden | types : controller_hidden & ~types;
		frameChanged();
	}

	void setBarsBehavior(int behavior) {
		bars_behavior = behavior;
		frameChanged();
	}

	int getBarsBehavior() {
		return bars_behavior;
	}

	private boolean decor_fits = true;

	public void setDecorFitsSystemWindows(boolean fits) {
		decor_fits = fits;
		frameChanged();
	}

	private boolean barsFullscreen() {
		return (view_system_ui & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0 || (params.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
		    || (controller_hidden & WindowInsets.Type.statusBars()) != 0;
	}

	private boolean barsHideNavigation() {
		return (view_system_ui & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0 || (controller_hidden & WindowInsets.Type.navigationBars()) != 0;
	}

	/* The window runs under the status bar: a layout-fullscreen window, unless its cutout mode keeps
	 * it off the notch; or one without the status bar whose cutout mode takes the notch (no cutout
	 * inset yet: general-todo ATL-69). FLAG_LAYOUT_IN_SCREEN doesn't count: AOSP sets it on every
	 * non-floating window, whose decor insets its content. */
	private boolean coversTop() {
		boolean layout_fullscreen = (view_system_ui & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0
		                            || (params.flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0 || !decor_fits;
		int cutout = params.layoutInDisplayCutoutMode;
		boolean takes_notch = cutout == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES || cutout == 3 /*ALWAYS*/;
		return layout_fullscreen && cutout != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER || barsFullscreen() && takes_notch;
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
		if (!has_soft_input_mode) // PhoneWindow.generateLayout's
			params.softInputMode = a.getInt(com.android.internal.R.styleable.Window_windowSoftInputMode, params.softInputMode);
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
		boolean immersive = (view_system_ui & (View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)) != 0
		                    || controller_hidden != 0 && bars_behavior != WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_TOUCH;
		set_frame(subwindow, (params.flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0, status_bar_color, light_status_bar,
		          barsFullscreen(), coversTop(), barsHideNavigation(), immersive, backgroundColor());
		if (bars_hidden != (barsFullscreen() ? 1 : 0) + (barsHideNavigation() ? 2 : 0))
			requestInsets(); // a hidden bar's insets are its maximum only
		set_keep_screen_on(subwindow, views_keeping_screen_on > 0 || (params.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
		set_screen_brightness(subwindow, params.screenBrightness);
		set_exclusion_rects(subwindow, exclusion_rects);
		set_soft_input(subwindow, soft_adjust, soft_pan);
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
	private native void set_frame(long subwindow, boolean has_color, int color, boolean light, boolean fullscreen, boolean cover_top, boolean hide_navigation, boolean immersive, int background);

	// what shows where the root doesn't reach (a keyboard's rounded corners): its background's color, else the theme's
	private int backgroundColor() {
		Drawable background = root.getBackground();
		if (background instanceof android.graphics.drawable.ColorDrawable)
			return ((android.graphics.drawable.ColorDrawable)background).getColor();
		TypedArray a = context.obtainStyledAttributes(new int[] {android.R.attr.colorBackground});
		int color = a.getColor(0, 0);
		a.recycle();
		return color;
	}
	private native void set_keep_screen_on(long subwindow, boolean keep);
	private native void set_exclusion_rects(long subwindow, int[] rects);
	private native int[] get_insets(long subwindow, int left, int top, int width, int height);
	private native void set_soft_input(long subwindow, int adjust, int pan);
	private native void set_screen_brightness(long subwindow, float brightness);
}
