/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.TextPaint;

public class SuperscriptSpan extends MetricAffectingSpan {
	@Override
	public void updateDrawState(TextPaint textPaint) {
		textPaint.baselineShift += (int)(textPaint.ascent() / 2);
	}

	@Override
	public void updateMeasureState(TextPaint textPaint) {
		textPaint.baselineShift += (int)(textPaint.ascent() / 2);
	}
}
