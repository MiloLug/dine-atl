/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.TextPaint;

public class ForegroundColorSpan extends CharacterStyle implements UpdateAppearance {
	private final int mColor;

	public ForegroundColorSpan(int color) {
		mColor = color;
	}

	public int getForegroundColor() {
		return mColor;
	}

	@Override
	public void updateDrawState(TextPaint textPaint) {
		textPaint.setColor(mColor);
	}
}
