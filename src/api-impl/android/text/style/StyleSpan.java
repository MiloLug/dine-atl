/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.ParcelableSpan;
import android.text.TextPaint;

public class StyleSpan extends MetricAffectingSpan implements ParcelableSpan {
	private final int mStyle;

	public StyleSpan(int style) {
		mStyle = style;
	}

	public int getStyle() {
		return mStyle;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		apply(ds, mStyle);
	}

	@Override
	public void updateMeasureState(TextPaint paint) {
		apply(paint, mStyle);
	}

	private static void apply(Paint paint, int style) {
		int oldStyle;

		Typeface old = paint.getTypeface();
		if (old == null) {
			oldStyle = 0;
		} else {
			oldStyle = old.getStyle();
		}

		int want = oldStyle | style;

		Typeface tf;
		if (old == null) {
			tf = Typeface.defaultFromStyle(want);
		} else {
			tf = Typeface.create(old, want);
		}

		int fake = want & ~tf.getStyle();

		if ((fake & Typeface.BOLD) != 0) {
			paint.setFakeBoldText(true);
		}

		if ((fake & Typeface.ITALIC) != 0) {
			paint.setTextSkewX(-0.25f);
		}

		paint.setTypeface(tf);
	}
}
