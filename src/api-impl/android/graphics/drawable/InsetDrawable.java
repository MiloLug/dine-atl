/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * graphics/java/android/graphics/drawable/InsetDrawable.java at android-security-10.0.0_r75, without
 * its documentation, theme attributes, optical insets and density changes; modified for ATL.
 */

package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class InsetDrawable extends DrawableWrapper {
	private final Rect mTmpRect = new Rect();
	private final Rect mTmpInsetRect = new Rect();

	private InsetState mState;

	InsetDrawable() {
		this(new InsetState(null, null), null);
	}

	public InsetDrawable(Drawable drawable, int inset) {
		this(drawable, inset, inset, inset, inset);
	}

	public InsetDrawable(Drawable drawable, float inset) {
		this(drawable, inset, inset, inset, inset);
	}

	public InsetDrawable(Drawable drawable, int insetLeft, int insetTop, int insetRight, int insetBottom) {
		this(new InsetState(null, null), null);

		mState.mInsetLeft = new InsetValue(0f, insetLeft);
		mState.mInsetTop = new InsetValue(0f, insetTop);
		mState.mInsetRight = new InsetValue(0f, insetRight);
		mState.mInsetBottom = new InsetValue(0f, insetBottom);

		setDrawable(drawable);
	}

	public InsetDrawable(Drawable drawable, float insetLeftFraction, float insetTopFraction, float insetRightFraction, float insetBottomFraction) {
		this(new InsetState(null, null), null);

		mState.mInsetLeft = new InsetValue(insetLeftFraction, 0);
		mState.mInsetTop = new InsetValue(insetTopFraction, 0);
		mState.mInsetRight = new InsetValue(insetRightFraction, 0);
		mState.mInsetBottom = new InsetValue(insetBottomFraction, 0);

		setDrawable(drawable);
	}

	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
	    throws XmlPullParserException, IOException {
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.InsetDrawable);

		// Inflation will advance the XmlPullParser and AttributeSet.
		super.inflate(r, parser, attrs, theme);

		updateStateFromTypedArray(a);
		verifyRequiredAttributes(a);
		a.recycle();
	}

	private void verifyRequiredAttributes(TypedArray a) throws XmlPullParserException {
		if (getDrawable() == null) {
			throw new XmlPullParserException(a.getPositionDescription() + ": <inset> tag requires a 'drawable' attribute or "
			                                 + "child tag defining a drawable");
		}
	}

	private void updateStateFromTypedArray(TypedArray a) {
		final InsetState state = mState;
		if (state == null) {
			return;
		}

		// Inset attribute may be overridden by more specific attributes.
		if (a.hasValue(R.styleable.InsetDrawable_inset)) {
			final InsetValue inset = getInset(a, R.styleable.InsetDrawable_inset, new InsetValue());
			state.mInsetLeft = inset;
			state.mInsetTop = inset;
			state.mInsetRight = inset;
			state.mInsetBottom = inset;
		}
		state.mInsetLeft = getInset(a, R.styleable.InsetDrawable_insetLeft, state.mInsetLeft);
		state.mInsetTop = getInset(a, R.styleable.InsetDrawable_insetTop, state.mInsetTop);
		state.mInsetRight = getInset(a, R.styleable.InsetDrawable_insetRight, state.mInsetRight);
		state.mInsetBottom = getInset(a, R.styleable.InsetDrawable_insetBottom, state.mInsetBottom);
	}

	private InsetValue getInset(TypedArray a, int index, InsetValue defaultValue) {
		if (a.hasValue(index)) {
			TypedValue tv = a.peekValue(index);
			if (tv.type == TypedValue.TYPE_FRACTION) {
				float f = tv.getFraction(1.0f, 1.0f);
				if (f >= 1f) {
					throw new IllegalStateException("Fraction cannot be larger than 1");
				}
				return new InsetValue(f, 0);
			} else {
				int dimension = a.getDimensionPixelOffset(index, 0);
				if (dimension != 0) {
					return new InsetValue(0, dimension);
				}
			}
		}
		return defaultValue;
	}

	private void getInsets(Rect out) {
		final Rect b = getBounds();
		out.left = mState.mInsetLeft.getDimension(b.width());
		out.right = mState.mInsetRight.getDimension(b.width());
		out.top = mState.mInsetTop.getDimension(b.height());
		out.bottom = mState.mInsetBottom.getDimension(b.height());
	}

	@Override
	public boolean getPadding(Rect padding) {
		final boolean pad = super.getPadding(padding);
		getInsets(mTmpInsetRect);
		padding.left += mTmpInsetRect.left;
		padding.right += mTmpInsetRect.right;
		padding.top += mTmpInsetRect.top;
		padding.bottom += mTmpInsetRect.bottom;

		return pad || (mTmpInsetRect.left | mTmpInsetRect.right | mTmpInsetRect.top | mTmpInsetRect.bottom) != 0;
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		final Rect r = mTmpRect;
		r.set(bounds);

		r.left += mState.mInsetLeft.getDimension(bounds.width());
		r.top += mState.mInsetTop.getDimension(bounds.height());
		r.right -= mState.mInsetRight.getDimension(bounds.width());
		r.bottom -= mState.mInsetBottom.getDimension(bounds.height());

		// Apply inset bounds to the wrapped drawable.
		super.onBoundsChange(r);
	}

	@Override
	public int getIntrinsicWidth() {
		final int childWidth = getDrawable() != null ? getDrawable().getIntrinsicWidth() : -1;
		final float fraction = mState.mInsetLeft.mFraction + mState.mInsetRight.mFraction;
		if (childWidth < 0 || fraction >= 1) {
			return -1;
		}
		return (int)(childWidth / (1 - fraction)) + mState.mInsetLeft.mDimension + mState.mInsetRight.mDimension;
	}

	@Override
	public int getIntrinsicHeight() {
		final int childHeight = getDrawable() != null ? getDrawable().getIntrinsicHeight() : -1;
		final float fraction = mState.mInsetTop.mFraction + mState.mInsetBottom.mFraction;
		if (childHeight < 0 || fraction >= 1) {
			return -1;
		}
		return (int)(childHeight / (1 - fraction)) + mState.mInsetTop.mDimension + mState.mInsetBottom.mDimension;
	}

	@Override
	DrawableWrapperState mutateConstantState() {
		mState = new InsetState(mState, null);
		return mState;
	}

	static final class InsetState extends DrawableWrapper.DrawableWrapperState {
		InsetValue mInsetLeft;
		InsetValue mInsetTop;
		InsetValue mInsetRight;
		InsetValue mInsetBottom;

		InsetState(InsetState orig, Resources res) {
			super(orig, res);

			if (orig != null) {
				mInsetLeft = orig.mInsetLeft;
				mInsetTop = orig.mInsetTop;
				mInsetRight = orig.mInsetRight;
				mInsetBottom = orig.mInsetBottom;
			} else {
				mInsetLeft = new InsetValue();
				mInsetTop = new InsetValue();
				mInsetRight = new InsetValue();
				mInsetBottom = new InsetValue();
			}
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new InsetDrawable(this, res);
		}
	}

	// immutable here: the state copy shares them
	static final class InsetValue {
		final float mFraction;
		final int mDimension;

		InsetValue() {
			this(0f, 0);
		}

		InsetValue(float fraction, int dimension) {
			mFraction = fraction;
			mDimension = dimension;
		}

		int getDimension(int boundSize) {
			return (int)(boundSize * mFraction) + mDimension;
		}
	}

	private InsetDrawable(InsetState state, Resources res) {
		super(state, res);

		mState = state;
	}
}
