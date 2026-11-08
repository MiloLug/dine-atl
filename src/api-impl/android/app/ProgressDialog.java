/*
 * Portions Copyright (C) 2007 The Android Open Source Project, from
 * core/java/android/app/ProgressDialog.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import java.text.NumberFormat;

// the alert's own spinner or bar, next to its message
public class ProgressDialog extends AlertDialog {
	public static final int STYLE_SPINNER = 0;
	public static final int STYLE_HORIZONTAL = 1;

	private int style = STYLE_SPINNER;
	private int max = 100;
	private int progress;
	private int secondaryProgress;
	private boolean indeterminate;

	public ProgressDialog(Context context) {
		super(context, 0);
	}

	public ProgressDialog(Context context, int themeResId) {
		super(context, themeResId);
	}

	public static ProgressDialog show(Context context, CharSequence title, CharSequence message) {
		return show(context, title, message, false);
	}

	public static ProgressDialog show(Context context, CharSequence title, CharSequence message, boolean indeterminate) {
		return show(context, title, message, indeterminate, false, null);
	}

	public static ProgressDialog show(Context context, CharSequence title, CharSequence message, boolean indeterminate, boolean cancelable) {
		return show(context, title, message, indeterminate, cancelable, null);
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	public static ProgressDialog show(Context context, CharSequence title, CharSequence message, boolean indeterminate, boolean cancelable, OnCancelListener cancelListener) {
		ProgressDialog dialog = new ProgressDialog(context);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setIndeterminate(indeterminate);
		dialog.setCancelable(cancelable);
		dialog.setOnCancelListener(cancelListener);
		dialog.show();
		return dialog;
	}

	public void setProgressStyle(int style) {
		this.style = style;
	}

	// AOSP's ProgressBar clamps once the dialog shows; before, the value waits for setMax
	public void setProgress(int value) {
		progress = isShowing() ? clamped(value) : value;
		progressChanged();
	}

	public int getProgress() {
		return isShowing() ? clamped(progress) : progress;
	}

	private int clamped(int value) {
		return Math.max(0, Math.min(value, max));
	}

	public void setSecondaryProgress(int secondaryProgress) {
		this.secondaryProgress = secondaryProgress;
	}

	public int getSecondaryProgress() {
		return secondaryProgress;
	}

	public void setMax(int max) {
		this.max = max;
		progressChanged();
	}

	public int getMax() {
		return max;
	}

	public void incrementProgressBy(int diff) {
		setProgress(progress + diff);
	}

	public void incrementSecondaryProgressBy(int diff) {
		secondaryProgress += diff;
	}

	public void setIndeterminate(boolean indeterminate) {
		this.indeterminate = indeterminate;
		progressChanged();
	}

	public boolean isIndeterminate() {
		return indeterminate;
	}

	public void setProgressDrawable(Drawable d) {}

	public void setIndeterminateDrawable(Drawable d) {}

	public void setProgressNumberFormat(String format) {}

	public void setProgressPercentFormat(NumberFormat format) {}

	private boolean shownIndeterminate; // as the alert last got it

	// any thread, as AOSP's ProgressBar takes it: the alert changes on the main one
	private void progressChanged() {
		if (Looper.myLooper() != getContext().getMainLooper()) {
			new Handler(getContext().getMainLooper()).post(new Runnable() {
				public void run() {
					progressChanged();
				}
			});
			return;
		}
		if (!drawn())
			return;
		boolean spins = atlIndeterminate();
		nativeSetProgress(nativePtr, clamped(progress), max, spins, spins != shownIndeterminate);
		shownIndeterminate = spins;
	}

	@Override
	int atlProgressStyle() {
		return style == STYLE_HORIZONTAL ? PROGRESS_BAR : PROGRESS_SPINNER;
	}

	// AOSP's spinner style always spins
	@Override
	boolean atlIndeterminate() {
		return indeterminate || style == STYLE_SPINNER;
	}

	@Override
	protected void showNative() {
		shownIndeterminate = atlIndeterminate();
		super.showNative();
	}

	@Override
	int atlProgressValue() {
		return clamped(progress);
	}

	@Override
	int atlProgressMax() {
		return max;
	}
}
