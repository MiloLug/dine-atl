/*
 * Portions Copyright (C) 2008 The Android Open Source Project, from
 * graphics/java/android/graphics/drawable/ColorDrawable.java at android-security-10.0.0_r75, Apache
 * License 2.0; see doc/AOSP.md.
 */
package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;

public class ColorDrawable extends Drawable {

	private ColorState state;
	private boolean mutated;
	private final Paint paint = new Paint();

	public ColorDrawable() {
		this(new ColorState());
	}

	public ColorDrawable(int color) {
		this(new ColorState());
		state.color = color;
	}

	private ColorDrawable(ColorState state) {
		this.state = state;
	}

	public int getColor() {
		return state.color;
	}

	/* Copyright (C) 2008 The Android Open Source Project */
	@Override
	public void draw(Canvas canvas) {
		if ((state.color >>> 24) != 0) {
			paint.setColor(state.color);
			canvas.drawRect(getBounds(), paint);
		}
	}

	public void setColor(int color) {
		if (state.color != color) {
			state.color = color;
			invalidateSelf();
		}
	}

	@Override
	public Drawable mutate() {
		if (!mutated && super.mutate() == this) {
			state = new ColorState(state);
			mutated = true;
		}
		return this;
	}

	@Override
	public ConstantState getConstantState() {
		return state;
	}

	/* Copyright (C) 2008 The Android Open Source Project */
	// the color alone: drawables made from it share it until they mutate()
	static final class ColorState extends ConstantState {
		int color;

		ColorState() {}

		ColorState(ColorState state) {
			color = state.color;
		}

		@Override
		public Drawable newDrawable() {
			return new ColorDrawable(this);
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new ColorDrawable(this);
		}

		@Override
		public int getChangingConfigurations() {
			return 0;
		}
	}
}
