package android.view;

import android.R;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.transition.Transition;
import android.widget.FrameLayout;
import android.widget.Toolbar;
import java.util.ArrayList;
import java.util.Arrays;

public class Window {
	public static final int FEATURE_OPTIONS_PANEL = 0;
	public static final int FEATURE_NO_TITLE = 1;

	public ViewTreeObserver view_tree_observer = null;
	private WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0, 0, 0);

	public static interface Callback {
		public void onContentChanged();

		public abstract boolean onCreatePanelMenu(int featureId, Menu menu);

		public View onCreatePanelView(int featureId);

		public boolean onPreparePanel(int featureId, View view, Menu menu);

		public boolean onMenuItemSelected(int featureId, MenuItem item);

		public void onPanelClosed(int featureId, Menu menu);

		public boolean onMenuOpened(int featureId, Menu menu);
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
		decorView = new FrameLayout(context);
		decorView.setId(android.R.id.content);
		decorView.decor_window = this;
	}

	public void set_native_window(long native_window) {
		this.native_window = native_window;
	}

	public void addFlags(int flags) {
		setFlags(flags, flags);
	}

	public void setFlags(int flags, int mask) {
		forced_flags |= mask;
		params.flags = (params.flags & ~mask) | (flags & mask);
		frameChanged();
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

	public void setContentView(View view) {
		if (decorView.getBackground() == null) {
			TypedArray ta = context.obtainStyledAttributes(new int[] {R.attr.windowBackground});
			if (ta.hasValue(0))
				setBackgroundDrawable(ta.getDrawable(0));
			ta.recycle();
		}
		styleBars();
		decorView.removeAllViews();
		decorView.addView(view);
	}

	public View getDecorView() {
		styleBars();
		return decorView;
	}

	public void takeInputQueue(InputQueue.Callback callback) {
		take_input_queue(native_window, callback, new InputQueue());
	}

	public boolean requestFeature(int featureId) {
		return false;
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
		if (!"com.whatsapp".equals(context.getPackageName()))
			clear_background(native_window);
		decorView.setBackgroundDrawable(drawable);
	}

	public void setAttributes(WindowManager.LayoutParams params) {
		this.params = params;
		frameChanged();
		if (params.screenBrightness != -1)
			set_screen_brightness(params.screenBrightness);
		setLayout(params.width, params.height);
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
		set_layout(native_window, width, height);
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
		new Handler(Looper.getMainLooper()).post(new Runnable() {
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

	// a move without a layout (scroll, translation) keeps the old rects until the next layout
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
		a.recycle();
	}

	// activity.c calls it by name (handle_cache) when the activity is shown
	private void updateFrame() {
		styleBars();
		set_frame((params.flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0, status_bar_color, light_status_bar,
		          view_fullscreen || (params.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0);
		set_keep_screen_on(views_keeping_screen_on > 0 || (params.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
		set_exclusion_rects(exclusion_rects);
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
		return false;
	}

	public void setTitle(CharSequence title) {
		set_title(native_window, title != null ? title.toString() : context.getPackageName());
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

	public void setGravity(int gravity) {}

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

	public void setDimAmount(float dimAmount) {}

	private native void set_title(long native_window, String title);
	public native void take_input_queue(long native_window, InputQueue.Callback callback, InputQueue queue);
	public native void set_layout(long native_window, int width, int height);
	private native void clear_background(long native_window);
	private native void set_frame(boolean has_color, int color, boolean light, boolean fullscreen);
	private native void set_keep_screen_on(boolean keep);
	private native void set_exclusion_rects(int[] rects);
	private static native int[] get_insets();
	private native void set_screen_brightness(float brightness);
}
