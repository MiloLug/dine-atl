/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/widget/ImageView.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

public class ImageView extends View {

	private ScaleType scaleType = ScaleType.FIT_CENTER;
	private Drawable drawable = null;
	private ColorFilter colorFilter = null;
	private final Matrix matrix = new Matrix();
	private Matrix drawMatrix = null;
	private final RectF tempSrc = new RectF();
	private final RectF tempDst = new RectF();

	public ImageView(Context context) {
		this(context, null);
	}

	public ImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public ImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		haveCustomMeasure = false;
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.ImageView, defStyleAttr, defStyleRes);
		if (a.hasValue(com.android.internal.R.styleable.ImageView_tint))
			colorFilter = new PorterDuffColorFilter(a.getColor(com.android.internal.R.styleable.ImageView_tint, 0),
			                                        PorterDuff.Mode.values()[a.getInt(com.android.internal.R.styleable.ImageView_tintMode, PorterDuff.Mode.SRC_IN.nativeInt)]);
		drawable = a.getDrawable(com.android.internal.R.styleable.ImageView_src);
		if (drawable != null) {
			drawable.setCallback(this);
			if (colorFilter != null)
				drawable.setColorFilter(colorFilter);
			native_setDrawable(widget, drawable.paintable, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
		}
		if (a.hasValue(com.android.internal.R.styleable.ImageView_scaleType))
			setScaleType(scaletype_from_int[a.getInt(com.android.internal.R.styleable.ImageView_scaleType, 3 /*CENTER*/)]);
		a.recycle();
	}

	public void setImageResource(final int resid) {
		if (this.getResources().getString(resid).endsWith(".xml")) {
			setImageDrawable(getContext().getDrawable(resid));
			return;
		}
		setImageBitmap(BitmapFactory.decodeResource(this.getResources(), resid));
	}
	public void setAdjustViewBounds(boolean adjustViewBounds) {}

	public void setScaleType(ScaleType scaleType) {
		this.scaleType = scaleType;
		native_setScaleType(widget, scaleType.nativeInt);
	}

	public ScaleType getScaleType() {
		return scaleType;
	}

	public Drawable getDrawable() {
		return drawable;
	}

	public void setImageDrawable(Drawable drawable) {
		if (drawable != null && colorFilter != null) {
			drawable = drawable.mutate();
			drawable.setColorFilter(colorFilter);
		}
		if (this.drawable != null) {
			this.drawable.setCallback(null);
		}
		this.drawable = drawable;
		if (drawable != null) {
			drawable.setCallback(this);
		}
		if (drawable != null)
			native_setDrawable(widget, drawable.paintable, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
		else
			native_setDrawable(widget, 0, -1, -1);
	}

	public void setImageMatrix(Matrix matrix) {
		if (matrix == null)
			this.matrix.reset();
		else
			this.matrix.set(matrix);
		invalidate();
	}

	public void setImageBitmap(Bitmap bitmap) {
		setImageDrawable(bitmap != null ? new BitmapDrawable(getResources(), bitmap) : null);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		if (drawable != null && drawable.isStateful() && drawable.setState(getDrawableState()))
			invalidateDrawable(drawable);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	// onDraw's; bounds are configured per draw, as ATL has no setFrame
	@Override
	protected void atlDrawContent(Canvas canvas) {
		if (drawable == null)
			return;
		int dwidth = drawable.getIntrinsicWidth();
		int dheight = drawable.getIntrinsicHeight();
		if (dwidth == 0 || dheight == 0)
			return;
		configureBounds(dwidth, dheight);
		if (drawMatrix == null && getPaddingTop() == 0 && getPaddingLeft() == 0) {
			drawable.draw(canvas);
		} else {
			int saveCount = canvas.getSaveCount();
			canvas.save();
			canvas.translate(getPaddingLeft(), getPaddingTop());
			if (drawMatrix != null)
				canvas.concat(drawMatrix);
			drawable.draw(canvas);
			canvas.restoreToCount(saveCount);
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void configureBounds(int dwidth, int dheight) {
		int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
		int vheight = getHeight() - getPaddingTop() - getPaddingBottom();
		boolean fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight);
		if (dwidth <= 0 || dheight <= 0 || ScaleType.FIT_XY == scaleType) {
			drawable.setBounds(0, 0, vwidth, vheight);
			drawMatrix = null;
			return;
		}
		drawable.setBounds(0, 0, dwidth, dheight);
		if (ScaleType.MATRIX == scaleType) {
			drawMatrix = matrix.isIdentity() ? null : matrix;
		} else if (fits) {
			drawMatrix = null;
		} else if (ScaleType.CENTER == scaleType) {
			drawMatrix = matrix;
			drawMatrix.setTranslate(Math.round((vwidth - dwidth) * 0.5f), Math.round((vheight - dheight) * 0.5f));
		} else if (ScaleType.CENTER_CROP == scaleType) {
			drawMatrix = matrix;
			float scale;
			float dx = 0, dy = 0;
			if (dwidth * vheight > vwidth * dheight) {
				scale = (float)vheight / (float)dheight;
				dx = (vwidth - dwidth * scale) * 0.5f;
			} else {
				scale = (float)vwidth / (float)dwidth;
				dy = (vheight - dheight * scale) * 0.5f;
			}
			drawMatrix.setScale(scale, scale);
			drawMatrix.postTranslate(Math.round(dx), Math.round(dy));
		} else if (ScaleType.CENTER_INSIDE == scaleType) {
			drawMatrix = matrix;
			float scale;
			if (dwidth <= vwidth && dheight <= vheight)
				scale = 1.0f;
			else
				scale = Math.min((float)vwidth / (float)dwidth, (float)vheight / (float)dheight);
			float dx = Math.round((vwidth - dwidth * scale) * 0.5f);
			float dy = Math.round((vheight - dheight * scale) * 0.5f);
			drawMatrix.setScale(scale, scale);
			drawMatrix.postTranslate(dx, dy);
		} else {
			tempSrc.set(0, 0, dwidth, dheight);
			tempDst.set(0, 0, vwidth, vheight);
			drawMatrix = matrix;
			drawMatrix.setRectToRect(tempSrc, tempDst, scaleType == ScaleType.FIT_START ? Matrix.ScaleToFit.START : scaleType == ScaleType.FIT_END ? Matrix.ScaleToFit.END : Matrix.ScaleToFit.CENTER);
		}
	}

	/**
	 * Options for scaling the bounds of an image to the bounds of this view.
	 */
	public enum ScaleType {
		/**
		 * Scale using the image matrix when drawing. The image matrix can be set using
		 * {@link ImageView#setImageMatrix(Matrix)}. From XML, use this syntax:
		 * <code>android:scaleType="matrix"</code>.
		 */
		MATRIX(0),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#FILL}.
		 * From XML, use this syntax: <code>android:scaleType="fitXY"</code>.
		 */
		FIT_XY(1),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#START}.
		 * From XML, use this syntax: <code>android:scaleType="fitStart"</code>.
		 */
		FIT_START(2),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#CENTER}.
		 * From XML, use this syntax:
		 * <code>android:scaleType="fitCenter"</code>.
		 */
		FIT_CENTER(3),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#END}.
		 * From XML, use this syntax: <code>android:scaleType="fitEnd"</code>.
		 */
		FIT_END(4),
		/**
		 * Center the image in the view, but perform no scaling.
		 * From XML, use this syntax: <code>android:scaleType="center"</code>.
		 */
		CENTER(5),
		/**
		 * Scale the image uniformly (maintain the image's aspect ratio) so
		 * that both dimensions (width and height) of the image will be equal
		 * to or larger than the corresponding dimension of the view
		 * (minus padding). The image is then centered in the view.
		 * From XML, use this syntax: <code>android:scaleType="centerCrop"</code>.
		 */
		CENTER_CROP(6),
		/**
		 * Scale the image uniformly (maintain the image's aspect ratio) so
		 * that both dimensions (width and height) of the image will be equal
		 * to or less than the corresponding dimension of the view
		 * (minus padding). The image is then centered in the view.
		 * From XML, use this syntax: <code>android:scaleType="centerInside"</code>.
		 */
		CENTER_INSIDE(7);

		ScaleType(int ni) {
			nativeInt = ni;
		}
		final int nativeInt;
	}
	private final ScaleType[] scaletype_from_int = {
		ScaleType.MATRIX,
		ScaleType.FIT_XY,
		ScaleType.FIT_START,
		ScaleType.FIT_CENTER,
		ScaleType.FIT_END,
		ScaleType.CENTER,
		ScaleType.CENTER_CROP,
		ScaleType.CENTER_INSIDE,
	};

	public final void setColorFilter(int color, PorterDuff.Mode mode) {}

	public void setImageTintList(ColorStateList tint) {
		if (tint == null)
			colorFilter = null;
		else
			colorFilter = new PorterDuffColorFilter(tint.getDefaultColor(), PorterDuff.Mode.SRC_IN);
		setImageDrawable(drawable);
	}

	public void setImageAlpha(int alpha) {}

	public void setMaxWidth(int width) {}

	public void setMaxHeight(int height) {}

	public void setImageState(int[] state, boolean merge) {}

	public ColorStateList getImageTintList() {
		return null;
	}

	public PorterDuff.Mode getImageTintMode() {
		return null;
	}

	public void setImageTintMode(PorterDuff.Mode tintMode) {}

	public void setCropToPadding(boolean crop) {}

	public void setColorFilter(int color) {}

	public void setColorFilter(ColorFilter cf) {}

	public Matrix getImageMatrix() {
		return drawMatrix == null ? new Matrix(Matrix.IDENTITY_MATRIX) : drawMatrix;
	}

	public void clearColorFilter() {
		colorFilter = null;
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	protected native void native_setDrawable(long widget, long paintable, int width, int height);
	protected native void native_setScaleType(long widget, int scale_type);
}
