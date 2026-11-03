/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style/QuoteSpan.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.ParcelableSpan;

/* Copyright (C) 2006 The Android Open Source Project: without the drawing, which ATL's text doesn't do */
public class QuoteSpan implements LeadingMarginSpan, ParcelableSpan {
	public static final int STANDARD_STRIPE_WIDTH_PX = 2;
	public static final int STANDARD_GAP_WIDTH_PX = 2;
	public static final int STANDARD_COLOR = 0xff0000ff;

	private final int mColor;
	private final int mStripeWidth;
	private final int mGapWidth;

	public QuoteSpan() {
		this(STANDARD_COLOR, STANDARD_STRIPE_WIDTH_PX, STANDARD_GAP_WIDTH_PX);
	}

	public QuoteSpan(int color) {
		this(color, STANDARD_STRIPE_WIDTH_PX, STANDARD_GAP_WIDTH_PX);
	}

	public QuoteSpan(int color, int stripeWidth, int gapWidth) {
		mColor = color;
		mStripeWidth = stripeWidth;
		mGapWidth = gapWidth;
	}

	public int getColor() {
		return mColor;
	}

	public int getStripeWidth() {
		return mStripeWidth;
	}

	public int getGapWidth() {
		return mGapWidth;
	}

	public int getLeadingMargin(boolean first) {
		return mStripeWidth + mGapWidth;
	}
}
