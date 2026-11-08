/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/app/Dialog.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.app;

import android.R;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

public class Dialog implements Window.Callback, KeyEvent.Callback, DialogInterface {
	// atlDialogResult's cancel, UI_DIALOG_CANCEL in the ui slot
	static final int RESULT_CANCEL = Integer.MIN_VALUE;

	// an alert's ui-slot dialog (atlNativeAlert), else 0
	protected long nativePtr;

	protected native long nativeInit();
	private native void nativeSetTitle(long ptr, String title);
	private native void nativeClose(long ptr);
	private native void nativeDestroy(long ptr);
	private native void nativeSetCanceledOnTouchOutside(long ptr, boolean cancel);

	private Context context;
	private Window window;
	CharSequence title;
	boolean cancelable = true;
	private boolean created; // onCreate ran: once per Dialog, as AOSP's dispatchOnCreate
	private boolean showing;
	private boolean hidden; // by hide(): still showing to Java, its window hidden
	private boolean canceled;
	private OnDismissListener onDismissListener;
	private OnShowListener onShowListener;
	private OnCancelListener onCancelListener;
	private OnKeyListener onKeyListener;

	/* Copyright (C) 2006 The Android Open Source Project: the theme and the gravity */
	public Dialog(Context context, int themeResId) {
		if (themeResId == 0) {
			TypedValue outValue = new TypedValue();
			context.getTheme().resolveAttribute(R.attr.dialogTheme, outValue, true);
			themeResId = outValue.resourceId;
		}
		this.context = themeResId != 0 ? new ContextThemeWrapper(context, themeResId) : context;
		window = new Window(this.context, this);
		window.setGravity(Gravity.CENTER);
		window.getDecorView();
		if (atlNativeAlert())
			nativePtr = nativeInit();
		window.atlAddSubwindow(ownerActivity(context));
	}

	// the window's token: the Activity under the context's wrappers (AOSP's WindowManager of the context)
	private static Activity ownerActivity(Context context) {
		while (!(context instanceof Activity) && context instanceof ContextWrapper)
			context = ((ContextWrapper)context).getBaseContext();
		return context instanceof Activity ? (Activity)context : null;
	}

	// AlertDialog: the ui slot shows the alert itself, not the decor view
	protected boolean atlNativeAlert() {
		return false;
	}

	// at a show: the decor view is drawn, inside the alert's panel when there is one
	protected boolean atlHasRoot() {
		return !atlNativeAlert();
	}

	// AlertDialog's ui-slot alert, which the registry orders with the windows over the activities
	protected long atlChrome() {
		return 0;
	}

	public Dialog(Context context) {
		this(context, 0);
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			if (nativePtr != 0)
				destroyNative();
			if (window != null) // null: the constructor threw first
				window.atlFreeSubwindow();
		} finally {
			super.finalize();
		}
	}

	public final boolean requestWindowFeature(int featureId) {
		return getWindow().requestFeature(featureId);
	}

	public Context getContext() {
		return context;
	}

	public void setContentView(View view) {
		getWindow().setContentView(view);
	}

	public void setContentView(int layoutResId) {
		setContentView(LayoutInflater.from(context).inflate(layoutResId, null));
	}

	public void setContentView(View view, ViewGroup.LayoutParams lp) {
		view.setLayoutParams(lp);
		setContentView(view);
	}

	public void setTitle(CharSequence title) {
		this.title = title;
		if (nativePtr != 0)
			nativeSetTitle(nativePtr, String.valueOf(title));
	}

	public void setTitle(int titleId) {
		setTitle(context.getString(titleId));
	}

	public void setOwnerActivity(Activity activity) {}

	public void setCancelable(boolean cancelable) {
		this.cancelable = cancelable;
	}

	public void setOnCancelListener(OnCancelListener onCancelListener) {
		this.onCancelListener = onCancelListener;
	}

	public void setOnDismissListener(OnDismissListener onDismissListener) {
		this.onDismissListener = onDismissListener;
	}

	public View findViewById(int id) {
		return window.findViewById(id);
	}

	/* Copyright (C) 2006 The Android Open Source Project: show, dismiss and hide */
	public void show() {
		System.out.println("showing the Dialog " + this);
		Runnable action = new Runnable() {
			@Override
			public void run() {
				if (showing) {
					if (hidden) {
						hidden = false;
						window.getDecorView().setVisibility(View.VISIBLE);
						window.atlSetSubwindowRoot(atlHasRoot(), atlChrome());
						window.atlShowSubwindow();
					}
					return;
				}
				if (!created) {
					created = true;
					onCreate(null);
				}
				TypedArray a = context.obtainStyledAttributes(R.styleable.Window);
				if (a.getBoolean(R.styleable.Window_windowCloseOnTouchOutside, false))
					window.setCloseOnTouchOutsideIfNotSet(true);
				a.recycle();

				canceled = false;
				getWindow().getDecorView().dispatchAttachedToWindow();
				window.atlSetSubwindowRoot(atlHasRoot(), atlChrome());
				if (!window.atlShowSubwindow()) {
					getWindow().getDecorView().dispatchDetachedFromWindow();
					throw new WindowManager.BadTokenException("Unable to add window -- token null is not valid; is your activity running?");
				}
				showing = true;
				if (onShowListener != null)
					onShowListener.onShow(Dialog.this);
			}
		};
		if (Looper.myLooper() == context.getMainLooper()) {
			action.run();
		} else {
			new Handler(context.getMainLooper()).post(action);
		}
	}

	protected void showNative() {}

	protected void closeNative() {
		nativeClose(nativePtr);
	}

	// from finalize: any thread
	protected void destroyNative() {
		nativeDestroy(nativePtr);
	}

	/** @hide windows.c shows or hides the window: with its token, or its own show and dismiss */
	public void atlWindowVisible(boolean visible) {
		if (nativePtr == 0)
			return;
		if (visible)
			showNative();
		else
			closeNative();
	}

	// on screen; results from the ui backend count only then
	boolean drawn() {
		return showing && !hidden && window.atlVisible();
	}

	// from the ui backend: a button, an item or RESULT_CANCEL; returns whether it is still drawn
	boolean atlDialogResult(int which) {
		if (drawn() && which == RESULT_CANCEL && cancelable)
			cancel();
		return drawn();
	}

	public boolean isShowing() {
		return showing;
	}

	public void dismiss() {
		System.out.println("dismissing the Dialog " + Dialog.this);
		if (!showing)
			return;
		if (!window.atlRemoveSubwindow())
			throw new IllegalArgumentException("View=" + window.getDecorView() + " not attached to window manager");
		showing = false;
		hidden = false;
		// HACK: dismissing the Dialog takes some time in AOSP, as the request goes back and forth between the application
		// and the system server. We replicate this behavior by adding 10 ms delay.
		// This Hack is required for NewPipe RouterActivity which has a race condition. It subscribes an rxJava observable
		// and immediately calls Dialog.dismiss(). The OnDismissListener would unsubscribes the observable again.
		new Handler(context.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				if (!showing) // not shown again meanwhile
					getWindow().getDecorView().dispatchDetachedFromWindow();
				if (onDismissListener != null)
					onDismissListener.onDismiss(Dialog.this);
			}
		}, 10);
	}

	public Window getWindow() {
		return window;
	}

	public void setCanceledOnTouchOutside(boolean cancel) {
		if (cancel)
			cancelable = true;
		window.setCloseOnTouchOutside(cancel);
		if (nativePtr != 0)
			nativeSetCanceledOnTouchOutside(nativePtr, cancel);
	}

	public class Builder {
		public Builder(Context context) {
			System.out.println("making a Dialog$Builder");
		}
	}

	@Override
	public void onContentChanged() {
	}
	@Override
	public boolean onCreatePanelMenu(int featureId, Menu menu) {
		throw new UnsupportedOperationException("Unimplemented method 'onCreatePanelMenu'");
	}
	@Override
	public View onCreatePanelView(int featureId) {
		throw new UnsupportedOperationException("Unimplemented method 'onCreatePanelView'");
	}
	@Override
	public boolean onPreparePanel(int featureId, View view, Menu menu) {
		throw new UnsupportedOperationException("Unimplemented method 'onPreparePanel'");
	}
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		throw new UnsupportedOperationException("Unimplemented method 'onMenuItemSelected'");
	}
	@Override
	public void onPanelClosed(int featureId, Menu menu) {
		throw new UnsupportedOperationException("Unimplemented method 'onPanelClosed'");
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		throw new UnsupportedOperationException("Unimplemented method 'onMenuOpened'");
	}

	protected void onCreate(Bundle savedInstanceState) {
		System.out.println("- onCreate - Dialog!");
	}

	public void hide() {
		System.out.println("hiding the Dialog " + this);
		hidden = showing;
		if (showing) {
			window.getDecorView().setVisibility(View.INVISIBLE);
			window.atlHideSubwindow();
		}
	}

	@Override
	public void cancel() {
		if (!canceled) {
			canceled = true;
			if (onCancelListener != null)
				onCancelListener.onCancel(this);
		}
		dismiss();
	}

	public void setOnShowListener(OnShowListener onShowListener) {
		this.onShowListener = onShowListener;
	}

	public void setCancelMessage(Message msg) {}

	public void setDismissMessage(Message msg) {}

	/* Copyright (C) 2006 The Android Open Source Project: dispatchTouchEvent to onBackPressed */
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (window.superDispatchTouchEvent(ev)) {
			return true;
		}
		return onTouchEvent(ev);
	}

	public boolean onTouchEvent(MotionEvent event) {
		if (cancelable && showing && window.shouldCloseOnTouch(context, event)) {
			cancel();
			return true;
		}
		return false;
	}

	public boolean dispatchKeyEvent(KeyEvent event) {
		if ((onKeyListener != null) && (onKeyListener.onKey(this, event.getKeyCode(), event))) {
			return true;
		}
		if (window.superDispatchKeyEvent(event)) {
			return true;
		}
		return event.dispatch(this, window.getDecorView().getKeyDispatcherState(), this);
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
			event.startTracking();
			return true;
		}

		return false;
	}

	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
		    && event.isTracking()
		    && !event.isCanceled()) {
			onBackPressed();
			return true;
		}
		return false;
	}

	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		return false;
	}

	public void onBackPressed() {
		if (cancelable) {
			cancel();
		}
	}

	public void setOnKeyListener(OnKeyListener onKeyListener) {
		this.onKeyListener = onKeyListener;
	}

	public View getCurrentFocus() {
		return window != null ? window.getCurrentFocus() : null;
	}
}
