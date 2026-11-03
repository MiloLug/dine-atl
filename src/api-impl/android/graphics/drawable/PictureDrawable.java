/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * graphics/java/android/graphics/drawable/PictureDrawable.java at android-security-10.0.0_r75,
 * Apache License 2.0; see doc/AOSP.md.
 */
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Picture;
import android.graphics.Rect;

public class PictureDrawable extends Drawable {
	private Picture mPicture;

	public PictureDrawable(Picture picture) {
		mPicture = picture;
	}

	public Picture getPicture() {
		return mPicture;
	}

	public void setPicture(Picture picture) {
		mPicture = picture;
	}

	@Override
	public void draw(Canvas canvas) {
		if (mPicture != null) {
			Rect bounds = getBounds();
			canvas.save();
			canvas.clipRect(bounds);
			canvas.translate(bounds.left, bounds.top);
			canvas.drawPicture(mPicture);
			canvas.restore();
		}
	}

	@Override
	public int getIntrinsicWidth() {
		return mPicture != null ? mPicture.getWidth() : -1;
	}

	@Override
	public int getIntrinsicHeight() {
		return mPicture != null ? mPicture.getHeight() : -1;
	}

	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public ConstantState getConstantState() {
		return new StubState(() -> new PictureDrawable(mPicture));
	}
}
