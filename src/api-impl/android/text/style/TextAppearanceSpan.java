/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextPaint;

public class TextAppearanceSpan extends MetricAffectingSpan {
	private final String mFamilyName;
	private final int mStyle;
	private final int mTextSize;
	private final ColorStateList mTextColor;
	private final ColorStateList mTextColorLink;

	public TextAppearanceSpan(Context context, int appearance) {
		this(context, appearance, -1);
	}

	public TextAppearanceSpan(Context context, int appearance, int colorList) {
		TypedArray a = context.obtainStyledAttributes(appearance, com.android.internal.R.styleable.TextAppearance);
		ColorStateList textColor = a.getColorStateList(com.android.internal.R.styleable.TextAppearance_textColor);
		mTextColorLink = a.getColorStateList(com.android.internal.R.styleable.TextAppearance_textColorLink);
		mTextSize = a.getDimensionPixelSize(com.android.internal.R.styleable.TextAppearance_textSize, -1);
		mStyle = a.getInt(com.android.internal.R.styleable.TextAppearance_textStyle, 0);
		String family = a.getString(com.android.internal.R.styleable.TextAppearance_fontFamily);
		if (family == null) {
			switch (a.getInt(com.android.internal.R.styleable.TextAppearance_typeface, 0)) {
				case 1: family = "sans"; break;
				case 2: family = "serif"; break;
				case 3: family = "monospace"; break;
			}
		}
		mFamilyName = family;
		a.recycle();
		if (colorList >= 0) {
			a = context.obtainStyledAttributes(com.android.internal.R.style.Theme, com.android.internal.R.styleable.Theme);
			textColor = a.getColorStateList(colorList);
			a.recycle();
		}
		mTextColor = textColor;
	}

	public TextAppearanceSpan(String family, int style, int size, ColorStateList color, ColorStateList linkColor) {
		mFamilyName = family;
		mStyle = style;
		mTextSize = size;
		mTextColor = color;
		mTextColorLink = linkColor;
	}

	public String getFamily() {
		return mFamilyName;
	}

	public ColorStateList getTextColor() {
		return mTextColor;
	}

	public ColorStateList getLinkTextColor() {
		return mTextColorLink;
	}

	public int getTextSize() {
		return mTextSize;
	}

	public int getTextStyle() {
		return mStyle;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		updateMeasureState(ds);
		if (mTextColor != null) {
			ds.setColor(ds.drawableState != null ? mTextColor.getColorForState(ds.drawableState, 0) : mTextColor.getDefaultColor());
		}
		if (mTextColorLink != null) {
			ds.linkColor = ds.drawableState != null ? mTextColorLink.getColorForState(ds.drawableState, 0) : mTextColorLink.getDefaultColor();
		}
	}

	@Override
	public void updateMeasureState(TextPaint ds) {
		if (mFamilyName != null || mStyle != 0) {
			Typeface tf = ds.getTypeface();
			int style = 0;
			if (tf != null) {
				style = tf.getStyle();
			}
			style |= mStyle;
			if (mFamilyName != null) {
				tf = Typeface.create(mFamilyName, style);
			} else if (tf == null) {
				tf = Typeface.defaultFromStyle(style);
			} else {
				tf = Typeface.create(tf, style);
			}
			int fake = style & ~tf.getStyle();
			if ((fake & Typeface.BOLD) != 0) {
				ds.setFakeBoldText(true);
			}
			if ((fake & Typeface.ITALIC) != 0) {
				ds.setTextSkewX(-0.25f);
			}
			ds.setTypeface(tf);
		}
		if (mTextSize > 0) {
			ds.setTextSize(mTextSize);
		}
	}
}
