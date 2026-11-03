/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style/BulletSpan.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.ParcelableSpan;

/* Copyright (C) 2006 The Android Open Source Project: without the drawing, which ATL's text doesn't do */
public class BulletSpan implements LeadingMarginSpan, ParcelableSpan {
	private static final int STANDARD_BULLET_RADIUS = 4;
	public static final int STANDARD_GAP_WIDTH = 2;
	private static final int STANDARD_COLOR = 0;

	private final int mGapWidth;
	private final int mBulletRadius;
	private final int mColor;
	private final boolean mWantColor;

	public BulletSpan() {
		this(STANDARD_GAP_WIDTH, STANDARD_COLOR, false, STANDARD_BULLET_RADIUS);
	}

	public BulletSpan(int gapWidth) {
		this(gapWidth, STANDARD_COLOR, false, STANDARD_BULLET_RADIUS);
	}

	public BulletSpan(int gapWidth, int color) {
		this(gapWidth, color, true, STANDARD_BULLET_RADIUS);
	}

	public BulletSpan(int gapWidth, int color, int bulletRadius) {
		this(gapWidth, color, true, bulletRadius);
	}

	private BulletSpan(int gapWidth, int color, boolean wantColor, int bulletRadius) {
		mGapWidth = gapWidth;
		mBulletRadius = bulletRadius;
		mColor = color;
		mWantColor = wantColor;
	}

	public int getLeadingMargin(boolean first) {
		return 2 * mBulletRadius + mGapWidth;
	}

	public int getGapWidth() {
		return mGapWidth;
	}

	public int getBulletRadius() {
		return mBulletRadius;
	}

	public int getColor() {
		return mColor;
	}
}
