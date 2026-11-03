/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.TextPaint;

public class AbsoluteSizeSpan extends MetricAffectingSpan {
	private final int mSize;
	private final boolean mDip;

	public AbsoluteSizeSpan(int size) {
		this(size, false);
	}

	public AbsoluteSizeSpan(int size, boolean dip) {
		mSize = size;
		mDip = dip;
	}

	public int getSize() {
		return mSize;
	}

	public boolean getDip() {
		return mDip;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		ds.setTextSize(mDip ? mSize * ds.density : mSize);
	}

	@Override
	public void updateMeasureState(TextPaint ds) {
		ds.setTextSize(mDip ? mSize * ds.density : mSize);
	}
}
