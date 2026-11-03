/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

public class TypefaceSpan extends MetricAffectingSpan {
	private final String mFamily;
	private final Typeface mTypeface;

	public TypefaceSpan(String family) {
		mFamily = family;
		mTypeface = null;
	}

	public TypefaceSpan(Typeface typeface) {
		mFamily = null;
		mTypeface = typeface;
	}

	public String getFamily() {
		return mFamily;
	}

	public Typeface getTypeface() {
		return mTypeface;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		updateTypeface(ds);
	}

	@Override
	public void updateMeasureState(TextPaint paint) {
		updateTypeface(paint);
	}

	private void updateTypeface(Paint paint) {
		if (mTypeface != null) {
			paint.setTypeface(mTypeface);
		} else if (mFamily != null) {
			Typeface old = paint.getTypeface();
			int style = old == null ? Typeface.NORMAL : old.getStyle();
			paint.setTypeface(Typeface.create(mFamily, style));
		}
	}
}
