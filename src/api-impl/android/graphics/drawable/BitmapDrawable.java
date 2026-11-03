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
	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG); // its alpha and color filter draw

	public BitmapDrawable() {
	}

	public BitmapDrawable(Resources res, Bitmap bitmap) {
		this.bitmap = bitmap;
		if (bitmap != null)
			adoptPaintable(native_paintable(bitmap.getTexture()));
	}

	public BitmapDrawable(Bitmap bitmap) {
		this.bitmap = bitmap;
		if (bitmap != null)
			adoptPaintable(native_paintable(bitmap.getTexture()));
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	// what a Canvas(Bitmap) recorded reaches the pixels first
	@Override
	public void draw(Canvas canvas) {
		if (bitmap != null)
			bitmap.prepareToDraw();
		if (paint.getColorFilter() != filter.colorFilter) // set through getPaint()
			setColorFilter(paint.getColorFilter());
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
		a.recycle();
		if (bitmap == null)
			throw new XmlPullParserException("<bitmap> needs a valid `src' attribute");
		adoptPaintable(native_paintable(bitmap.getTexture()));
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
		return bitmap != null ? new BitmapState(bitmap) : null;
	}

	private static native long native_paintable(long bitmap);

	// drawables made from it share the Bitmap: a resource decodes once
	static final class BitmapState extends ConstantState {
		final Bitmap bitmap;

		BitmapState(Bitmap bitmap) {
			this.bitmap = bitmap;
		}

		@Override
		public Drawable newDrawable() {
			return new BitmapDrawable(bitmap);
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new BitmapDrawable(res, bitmap);
		}

		@Override
		public int getChangingConfigurations() {
			return 0;
		}
	}
}
