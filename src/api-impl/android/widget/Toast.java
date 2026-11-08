/*
 * Portions Copyright (C) 2007 The Android Open Source Project, from
 * core/java/android/widget/Toast.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.widget;

import android.atl.ATLLoadedApp;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/* AOSP 10's Toast API. Text toasts go to the ui slot (ui_toast_show), which shows them natively;
 * a setView toast is a TYPE_TOAST window. Each app shows one at a time from its queue, on its main
 * thread, as NotificationManagerService does for the device. */
public class Toast {
	public static final int LENGTH_SHORT = 0;
	public static final int LENGTH_LONG = 1;

	/* Copyright (C) 2007 The Android Open Source Project: NotificationManagerService's
	 * SHORT_DELAY and LONG_DELAY */
	private static final int SHORT_DELAY = 2000;
	private static final int LONG_DELAY = 3500;

	final Context mContext;
	private int duration;
	private View next_view;
	private View text_view; // makeText's: shown natively while it is the view
	private int gravity, x_offset, y_offset;
	private float horizontal_margin, vertical_margin;
	private View shown_view; // the setView view added while this toast shows

	public Toast(Context context) {
		this(context, null);
	}

	/* Copyright (C) 2007 The Android Open Source Project: TN's looper and the default place */
	/** @hide */
	public Toast(Context context, Looper looper) {
		mContext = context;
		if (looper == null) {
			looper = Looper.myLooper();
			if (looper == null)
				throw new RuntimeException("Can't toast on a thread that has not called Looper.prepare()");
		}
		y_offset = context.getResources().getDimensionPixelSize(com.android.internal.R.dimen.toast_y_offset);
		gravity = context.getResources().getInteger(com.android.internal.R.integer.config_toastDefaultGravity);
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	public static Toast makeText(Context context, CharSequence text, int duration) {
		return makeText(context, null, text, duration);
	}

	/** @hide */
	public static Toast makeText(Context context, Looper looper, CharSequence text, int duration) {
		Toast result = new Toast(context, looper);
		// framework-res has no transient_notification: a stand-in with its message view, never drawn
		LinearLayout layout = new LinearLayout(context);
		TextView message = new TextView(context);
		message.setId(com.android.internal.R.id.message);
		message.setText(text);
		layout.addView(message);
		result.next_view = result.text_view = layout;
		result.duration = duration;
		return result;
	}

	public static Toast makeText(Context context, int resId, int duration) {
		return makeText(context, context.getResources().getText(resId), duration);
	}

	public void show() {
		if (next_view == null)
			throw new RuntimeException("setView must have been called");
		queue(mContext).enqueue(this);
	}

	public void cancel() {
		queue(mContext).cancel(this);
	}

	public void setView(View view) {
		next_view = view;
	}

	public View getView() {
		return next_view;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	public int getDuration() {
		return duration;
	}

	public void setMargin(float horizontalMargin, float verticalMargin) {
		horizontal_margin = horizontalMargin;
		vertical_margin = verticalMargin;
	}

	public float getHorizontalMargin() {
		return horizontal_margin;
	}

	public float getVerticalMargin() {
		return vertical_margin;
	}

	public void setGravity(int gravity, int xOffset, int yOffset) {
		this.gravity = gravity;
		x_offset = xOffset;
		y_offset = yOffset;
	}

	public int getGravity() {
		return gravity;
	}

	public int getXOffset() {
		return x_offset;
	}

	public int getYOffset() {
		return y_offset;
	}

	public void setText(int resId) {
		setText(mContext.getText(resId));
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	public void setText(CharSequence s) {
		if (next_view == null)
			throw new RuntimeException("This Toast was not created with Toast.makeText()");
		TextView tv = (TextView)next_view.findViewById(com.android.internal.R.id.message);
		if (tv == null)
			throw new RuntimeException("This Toast was not created with Toast.makeText()");
		tv.setText(s);
	}

	private static Queue queue(Context context) {
		ATLLoadedApp app = context.atl_process_app();
		synchronized (app) {
			if (app.toasts == null)
				app.toasts = new Queue(app.main_looper);
			return app.toasts;
		}
	}

	// WindowManager's margins, as fractions of the window, on top of the offsets
	private int x() {
		DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
		return x_offset + (int)(horizontal_margin * metrics.widthPixels);
	}

	private int y() {
		DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
		return y_offset + (int)(vertical_margin * metrics.heightPixels);
	}

	// the queue's, on the app's main thread
	private void atlShow() {
		if (next_view == text_view) {
			TextView message = (TextView)next_view.findViewById(com.android.internal.R.id.message);
			nativeShow(String.valueOf(message.getText()), Gravity.getAbsoluteGravity(gravity, View.LAYOUT_DIRECTION_LTR), x(), y());
			return;
		}
		/* Copyright (C) 2007 The Android Open Source Project: TN's window */
		WindowManager.LayoutParams params = new WindowManager.LayoutParams();
		params.width = WindowManager.LayoutParams.WRAP_CONTENT;
		params.height = WindowManager.LayoutParams.WRAP_CONTENT;
		params.format = PixelFormat.TRANSLUCENT;
		params.type = WindowManager.LayoutParams.TYPE_TOAST;
		params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
		params.gravity = Gravity.getAbsoluteGravity(gravity, View.LAYOUT_DIRECTION_LTR);
		params.x = x();
		params.y = y();
		shown_view = next_view;
		if (shown_view.getParent() != null)
			((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).removeView(shown_view);
		((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).addView(shown_view, params);
	}

	private void atlHide() {
		if (shown_view == null) {
			nativeHide();
			return;
		}
		if (shown_view.getParent() != null)
			((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).removeViewImmediate(shown_view);
		shown_view = null;
	}

	private static native void nativeShow(String text, int gravity, int x, int y);
	private static native void nativeHide();

	/** @hide An app's toasts, one shown at a time for its duration, on its main thread. */
	public static final class Queue {
		private final Handler handler;
		private final java.util.ArrayList<Toast> pending = new java.util.ArrayList<>();
		private Toast current;
		private final Runnable timeout = new Runnable() {
			@Override
			public void run() {
				Toast done = current;
				current = null;
				done.atlHide();
				next();
			}
		};

		Queue(Looper looper) {
			handler = new Handler(looper);
		}

		void enqueue(final Toast toast) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					if (toast == current || pending.contains(toast))
						return;
					pending.add(toast);
					if (current == null)
						next();
				}
			});
		}

		void cancel(final Toast toast) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					if (toast != current) {
						pending.remove(toast);
						return;
					}
					handler.removeCallbacks(timeout);
					timeout.run();
				}
			});
		}

		private void next() {
			if (pending.isEmpty())
				return;
			current = pending.remove(0);
			handler.postDelayed(timeout, current.duration == LENGTH_LONG ? LONG_DELAY : SHORT_DELAY);
			/* Copyright (C) 2007 The Android Open Source Project: TN.handleShow ignores it */
			try {
				current.atlShow();
			} catch (WindowManager.BadTokenException e) {
			}
		}
	}
}
