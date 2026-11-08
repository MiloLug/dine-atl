package android.view;

import android.content.Context;

/* AOSP's WindowManagerImpl over windows.c's registry: an added view is the root of a window of its
 * own (Window.atlAddView). One per Context; an Activity's is its Window's, which gives the windows
 * added through it the activity's token as AOSP's Window.adjustLayoutParamsForSubWindow. */
public final class WindowManagerImpl implements WindowManager, ViewManager {
	private final Context context;
	private final Window parent_window;

	public WindowManagerImpl(Context context, Window parentWindow) {
		this.context = context;
		this.parent_window = parentWindow;
	}

	public android.view.Display getDefaultDisplay() {
		return new android.view.Display();
	}

	private static WindowManager.LayoutParams windowParams(ViewGroup.LayoutParams params) {
		if (!(params instanceof WindowManager.LayoutParams))
			throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
		return (WindowManager.LayoutParams)params;
	}

	private static Window added(View view) {
		Window window = view != null ? Window.atlAddedWindow(view) : null;
		if (window == null)
			throw new IllegalArgumentException("View=" + view + " not attached to window manager");
		return window;
	}

	@Override
	public void addView(View view, ViewGroup.LayoutParams params) {
		if (view == null)
			throw new IllegalArgumentException("view must not be null");
		WindowManager.LayoutParams wparams = windowParams(params);
		if (Window.atlAddedWindow(view) != null)
			throw new IllegalStateException("View " + view + " has already been added to the window manager.");
		if (parent_window != null)
			parent_window.adjustLayoutParamsForSubWindow(wparams);
		Window.atlAddView(context, view, wparams);
	}

	@Override
	public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
		if (view == null)
			throw new IllegalArgumentException("view must not be null");
		added(view).atlUpdateLayout(windowParams(params));
	}

	@Override
	public void removeView(View view) {
		added(view).atlRemoveView();
	}

	@Override
	public void removeViewImmediate(View view) {
		removeView(view);
	}
}
