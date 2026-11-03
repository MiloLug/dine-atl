/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.TextPaint;

public class RelativeSizeSpan extends MetricAffectingSpan {
	private final float mProportion;

	public RelativeSizeSpan(float proportion) {
		mProportion = proportion;
	}

	public float getSizeChange() {
		return mProportion;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		ds.setTextSize(ds.getTextSize() * mProportion);
	}

	@Override
	public void updateMeasureState(TextPaint ds) {
		ds.setTextSize(ds.getTextSize() * mProportion);
	}
}
