/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * graphics/java/android/graphics/drawable/ScaleDrawable.java at android-security-10.0.0_r75, without
 * its documentation, theme attributes and getOpacity (ATL's Drawable has none); modified for ATL.
 */

package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ScaleDrawable extends DrawableWrapper {
	private static final int MAX_LEVEL = 10000;

	private final Rect mTmpRect = new Rect();

	private ScaleState mState;

	ScaleDrawable() {
		this(new ScaleState(null, null), null);
	}

	public ScaleDrawable(Drawable drawable, int gravity, float scaleWidth, float scaleHeight) {
		this(new ScaleState(null, null), null);

		mState.mGravity = gravity;
		mState.mScaleWidth = scaleWidth;
		mState.mScaleHeight = scaleHeight;

		setDrawable(drawable);
	}

	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
	    throws XmlPullParserException, IOException {
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.ScaleDrawable);

		// Inflation will advance the XmlPullParser and AttributeSet.
		super.inflate(r, parser, attrs, theme);

		updateStateFromTypedArray(a);
		verifyRequiredAttributes(a);
		a.recycle();

		updateLocalState();
	}

	private void verifyRequiredAttributes(TypedArray a) throws XmlPullParserException {
		if (getDrawable() == null) {
			throw new XmlPullParserException(a.getPositionDescription() + ": <scale> tag requires a 'drawable' attribute or "
			                                 + "child tag defining a drawable");
		}
	}

	private void updateStateFromTypedArray(TypedArray a) {
		final ScaleState state = mState;
		if (state == null) {
			return;
		}

		state.mScaleWidth = getPercent(a, R.styleable.ScaleDrawable_scaleWidth, state.mScaleWidth);
		state.mScaleHeight = getPercent(a, R.styleable.ScaleDrawable_scaleHeight, state.mScaleHeight);
		state.mGravity = a.getInt(R.styleable.ScaleDrawable_scaleGravity, state.mGravity);
		state.mUseIntrinsicSizeAsMin = a.getBoolean(R.styleable.ScaleDrawable_useIntrinsicSizeAsMinimum, state.mUseIntrinsicSizeAsMin);
		state.mInitialLevel = a.getInt(R.styleable.ScaleDrawable_level, state.mInitialLevel);
	}

	private static float getPercent(TypedArray a, int index, float defaultValue) {
		final int type = a.getType(index);
		if (type == TypedValue.TYPE_FRACTION || type == TypedValue.TYPE_NULL) {
			return a.getFraction(index, 1, 1, defaultValue);
		}

		// Coerce to float.
		final String s = a.getString(index);
		if (s != null) {
			if (s.endsWith("%")) {
				final String f = s.substring(0, s.length() - 1);
				return Float.parseFloat(f) / 100.0f;
			}
		}

		return defaultValue;
	}

	@Override
	public void draw(Canvas canvas) {
		final Drawable d = getDrawable();
		if (d != null && d.getLevel() != 0) {
			d.draw(canvas);
		}
	}

	@Override
	protected boolean onLevelChange(int level) {
		super.onLevelChange(level);
		onBoundsChange(getBounds());
		invalidateSelf();
		return true;
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		final Drawable d = getDrawable();
		final Rect r = mTmpRect;
		final boolean min = mState.mUseIntrinsicSizeAsMin;
		final int level = getLevel();

		int w = bounds.width();
		if (mState.mScaleWidth > 0) {
			final int iw = min ? d.getIntrinsicWidth() : 0;
			w -= (int)((w - iw) * (MAX_LEVEL - level) * mState.mScaleWidth / MAX_LEVEL);
		}

		int h = bounds.height();
		if (mState.mScaleHeight > 0) {
			final int ih = min ? d.getIntrinsicHeight() : 0;
			h -= (int)((h - ih) * (MAX_LEVEL - level) * mState.mScaleHeight / MAX_LEVEL);
		}

		final int layoutDirection = getLayoutDirection();
		Gravity.apply(mState.mGravity, w, h, bounds, r, layoutDirection);

		if (w > 0 && h > 0) {
			d.setBounds(r.left, r.top, r.right, r.bottom);
		}
	}

	@Override
	DrawableWrapperState mutateConstantState() {
		mState = new ScaleState(mState, null);
		return mState;
	}

	static final class ScaleState extends DrawableWrapper.DrawableWrapperState {
		private static final float DO_NOT_SCALE = -1.0f;

		float mScaleWidth = DO_NOT_SCALE;
		float mScaleHeight = DO_NOT_SCALE;
		int mGravity = Gravity.LEFT;
		boolean mUseIntrinsicSizeAsMin = false;
		int mInitialLevel = 0;

		ScaleState(ScaleState orig, Resources res) {
			super(orig, res);

			if (orig != null) {
				mScaleWidth = orig.mScaleWidth;
				mScaleHeight = orig.mScaleHeight;
				mGravity = orig.mGravity;
				mUseIntrinsicSizeAsMin = orig.mUseIntrinsicSizeAsMin;
				mInitialLevel = orig.mInitialLevel;
			}
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new ScaleDrawable(this, res);
		}
	}

	private ScaleDrawable(ScaleState state, Resources res) {
		super(state, res);

		mState = state;

		updateLocalState();
	}

	private void updateLocalState() {
		setLevel(mState.mInitialLevel);
	}
}
