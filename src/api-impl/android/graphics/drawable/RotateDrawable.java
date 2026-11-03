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
 * graphics/java/android/graphics/drawable/RotateDrawable.java at android-security-10.0.0_r75, without
 * its documentation and theme attributes; modified for ATL.
 */

package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.util.TypedValue;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class RotateDrawable extends DrawableWrapper {
	private static final int MAX_LEVEL = 10000;

	private RotateState mState;

	public RotateDrawable() {
		this(new RotateState(null, null), null);
	}

	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
	    throws XmlPullParserException, IOException {
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.RotateDrawable);

		// Inflation will advance the XmlPullParser and AttributeSet.
		super.inflate(r, parser, attrs, theme);

		updateStateFromTypedArray(a);
		verifyRequiredAttributes(a);
		a.recycle();
	}

	private void verifyRequiredAttributes(TypedArray a) throws XmlPullParserException {
		if (getDrawable() == null) {
			throw new XmlPullParserException(a.getPositionDescription() + ": <rotate> tag requires a 'drawable' attribute or "
			                                 + "child tag defining a drawable");
		}
	}

	private void updateStateFromTypedArray(TypedArray a) {
		final RotateState state = mState;
		if (state == null) {
			return;
		}

		if (a.hasValue(R.styleable.RotateDrawable_pivotX)) {
			final TypedValue tv = a.peekValue(R.styleable.RotateDrawable_pivotX);
			state.mPivotXRel = tv.type == TypedValue.TYPE_FRACTION;
			state.mPivotX = state.mPivotXRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
		}

		if (a.hasValue(R.styleable.RotateDrawable_pivotY)) {
			final TypedValue tv = a.peekValue(R.styleable.RotateDrawable_pivotY);
			state.mPivotYRel = tv.type == TypedValue.TYPE_FRACTION;
			state.mPivotY = state.mPivotYRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
		}

		state.mFromDegrees = a.getFloat(R.styleable.RotateDrawable_fromDegrees, state.mFromDegrees);
		state.mToDegrees = a.getFloat(R.styleable.RotateDrawable_toDegrees, state.mToDegrees);
		state.mCurrentDegrees = state.mFromDegrees;
	}

	@Override
	public void draw(Canvas canvas) {
		final Drawable d = getDrawable();
		final Rect bounds = d.getBounds();
		final int w = bounds.right - bounds.left;
		final int h = bounds.bottom - bounds.top;
		final RotateState st = mState;
		final float px = st.mPivotXRel ? (w * st.mPivotX) : st.mPivotX;
		final float py = st.mPivotYRel ? (h * st.mPivotY) : st.mPivotY;

		final int saveCount = canvas.save();
		canvas.rotate(st.mCurrentDegrees, px + bounds.left, py + bounds.top);
		d.draw(canvas);
		canvas.restoreToCount(saveCount);
	}

	public void setFromDegrees(float fromDegrees) {
		if (mState.mFromDegrees != fromDegrees) {
			mState.mFromDegrees = fromDegrees;
			invalidateSelf();
		}
	}

	public float getFromDegrees() {
		return mState.mFromDegrees;
	}

	public void setToDegrees(float toDegrees) {
		if (mState.mToDegrees != toDegrees) {
			mState.mToDegrees = toDegrees;
			invalidateSelf();
		}
	}

	public float getToDegrees() {
		return mState.mToDegrees;
	}

	public void setPivotX(float pivotX) {
		if (mState.mPivotX != pivotX) {
			mState.mPivotX = pivotX;
			invalidateSelf();
		}
	}

	public float getPivotX() {
		return mState.mPivotX;
	}

	public void setPivotXRelative(boolean relative) {
		if (mState.mPivotXRel != relative) {
			mState.mPivotXRel = relative;
			invalidateSelf();
		}
	}

	public boolean isPivotXRelative() {
		return mState.mPivotXRel;
	}

	public void setPivotY(float pivotY) {
		if (mState.mPivotY != pivotY) {
			mState.mPivotY = pivotY;
			invalidateSelf();
		}
	}

	public float getPivotY() {
		return mState.mPivotY;
	}

	public void setPivotYRelative(boolean relative) {
		if (mState.mPivotYRel != relative) {
			mState.mPivotYRel = relative;
			invalidateSelf();
		}
	}

	public boolean isPivotYRelative() {
		return mState.mPivotYRel;
	}

	@Override
	protected boolean onLevelChange(int level) {
		super.onLevelChange(level);

		final float value = level / (float)MAX_LEVEL;
		final float degrees = MathUtils.lerp(mState.mFromDegrees, mState.mToDegrees, value);
		mState.mCurrentDegrees = degrees;

		invalidateSelf();
		return true;
	}

	@Override
	DrawableWrapperState mutateConstantState() {
		mState = new RotateState(mState, null);
		return mState;
	}

	static final class RotateState extends DrawableWrapper.DrawableWrapperState {
		boolean mPivotXRel = true;
		float mPivotX = 0.5f;
		boolean mPivotYRel = true;
		float mPivotY = 0.5f;
		float mFromDegrees = 0.0f;
		float mToDegrees = 360.0f;
		float mCurrentDegrees = 0.0f;

		RotateState(RotateState orig, Resources res) {
			super(orig, res);

			if (orig != null) {
				mPivotXRel = orig.mPivotXRel;
				mPivotX = orig.mPivotX;
				mPivotYRel = orig.mPivotYRel;
				mPivotY = orig.mPivotY;
				mFromDegrees = orig.mFromDegrees;
				mToDegrees = orig.mToDegrees;
				mCurrentDegrees = orig.mCurrentDegrees;
			}
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new RotateDrawable(this, res);
		}
	}

	private RotateDrawable(RotateState state, Resources res) {
		super(state, res);

		mState = state;
	}
}
