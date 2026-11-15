package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class BitmapDrawable extends Drawable {

	private Bitmap bitmap;
	private final PaintableFilter filter = new PaintableFilter();
	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG); // its alpha, color filter and filter flag draw
	private boolean native_filter = true; // the flag the native drawable has: a new one filters

	public BitmapDrawable() {
	}

	public BitmapDrawable(Resources res, Bitmap bitmap) {
		this(bitmap);
	}

	public BitmapDrawable(Bitmap bitmap) {
		this.bitmap = bitmap;
		if (bitmap != null && !bitmap.isRecycled()) // a recycled one throws when drawn, as Canvas.drawBitmap
			adoptPaintable(native_paintable(bitmap.getNativeInstance()));
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	@Override
	public void draw(Canvas canvas) {
		if (bitmap != null && bitmap.isRecycled())
			throw new RuntimeException("Canvas: trying to use a recycled bitmap " + bitmap);
		if (paint.getColorFilter() != filter.colorFilter) // set through getPaint()
			setColorFilter(paint.getColorFilter());
		if (paintable != 0 && paint.isFilterBitmap() != native_filter) {
			native_filter = paint.isFilterBitmap();
			native_setFilterBitmap(paintable, native_filter);
		}
		int alpha = paint.getAlpha();
		if (alpha == 255) {
			super.draw(canvas);
			return;
		}
		Rect bounds = getBounds();
		int count = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
		super.draw(canvas);
		canvas.restoreToCount(count);
	}

	@Override
	public void setFilterBitmap(boolean filter) {
		paint.setFilterBitmap(filter);
		invalidateSelf();
	}

	@Override
	public boolean isFilterBitmap() {
		return paint.isFilterBitmap();
	}

	@Override
	public void setAlpha(int alpha) {
		if (alpha != paint.getAlpha()) {
			paint.setAlpha(alpha);
			invalidateSelf();
		}
	}

	@Override
	public int getAlpha() {
		return paint.getAlpha();
	}

	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
	    throws XmlPullParserException, IOException {
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.BitmapDrawable);
		if (a.hasValue(R.styleable.BitmapDrawable_src)) {
			BitmapDrawable dr = null;
			try {
				// TODO: getDrawable() might pick up another <bitmap> XML,
				// but we should reject anything other than image files.
				dr = (BitmapDrawable)a.getDrawable(R.styleable.BitmapDrawable_src);
			} catch (java.lang.Exception e) {
				e.printStackTrace();
			}
			if (dr != null)
				bitmap = dr.bitmap;
		}
		paint.setFilterBitmap(a.getBoolean(R.styleable.BitmapDrawable_filter, paint.isFilterBitmap()));
		a.recycle();
		if (bitmap == null)
			throw new XmlPullParserException("<bitmap> needs a valid `src' attribute");
		adoptPaintable(native_paintable(bitmap.getNativeInstance()));
		native_filter = true;
		filter.apply(this);
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		filter.colorFilter = colorFilter;
		paint.setColorFilter(colorFilter);
		filter.apply(this);
		invalidateSelf();
	}

	@Override
	public ColorFilter getColorFilter() {
		return filter.colorFilter;
	}

	@Override
	public void setTintList(ColorStateList tint) {
		filter.tint = tint;
		filter.apply(this);
		invalidateSelf();
	}

	@Override
	public void setTintMode(PorterDuff.Mode tintMode) {
		filter.tintMode = tintMode;
		filter.apply(this);
		invalidateSelf();
	}

	@Override
	public boolean isStateful() {
		return filter.isStateful();
	}

	@Override
	protected boolean onStateChange(int[] stateSet) {
		if (!filter.isStateful())
			return false;
		filter.apply(this);
		return true;
	}

	@Override
	public int getIntrinsicWidth() {
		if (bitmap == null)
			return 0;
		return bitmap.getWidth();
	}

	@Override
	public int getIntrinsicHeight() {
		if (bitmap == null)
			return 0;
		return bitmap.getHeight();
	}

	public void setTileModeX(Shader.TileMode mode) {}

	public Paint getPaint() {
		return paint;
	}

	public void setGravity(int g) {}

	@Override
	public ConstantState getConstantState() {
		return bitmap != null ? new BitmapState(bitmap, paint.isFilterBitmap()) : null;
	}

	private static native long native_paintable(long bitmap);

	// drawables made from it share the Bitmap: a resource decodes once
	static final class BitmapState extends ConstantState {
		final Bitmap bitmap;
		final boolean filter;

		BitmapState(Bitmap bitmap, boolean filter) {
			this.bitmap = bitmap;
			this.filter = filter;
		}

		@Override
		public Drawable newDrawable() {
			return newDrawable(null);
		}

		@Override
		public Drawable newDrawable(Resources res) {
			BitmapDrawable drawable = new BitmapDrawable(res, bitmap);
			drawable.setFilterBitmap(filter);
			return drawable;
		}

		@Override
		public int getChangingConfigurations() {
			return 0;
		}
	}
}
