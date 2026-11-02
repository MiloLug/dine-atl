package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.NinePatch;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/* AOSP 10's API over the gfx slot's nine-patch drawable; copies from the constant state share its
 * bitmap and chunk. */
public class NinePatchDrawable extends Drawable {
	private NinePatchState state;
	private final PaintableFilter filter = new PaintableFilter();

	public NinePatchDrawable(Resources res, Bitmap bitmap, byte[] chunk, Rect padding, String srcName) {
		this(new NinePatchState(bitmap, chunk, padding));
	}

	public NinePatchDrawable(Bitmap bitmap, byte[] chunk, Rect padding, String srcName) {
		this(new NinePatchState(bitmap, chunk, padding));
	}

	public NinePatchDrawable(Resources res, NinePatch patch) {
		this(patch);
	}

	public NinePatchDrawable(NinePatch patch) {
		this(new NinePatchState(patch.getBitmap(), patch.getChunk(), null));
	}

	// <nine-patch> XML, which inflate() fills
	NinePatchDrawable() {
		this(new NinePatchState(null, null, null));
	}

	private NinePatchDrawable(NinePatchState state) {
		setState(state);
	}

	private void setState(NinePatchState state) {
		this.state = state;
		if (state.bitmap != null && state.chunk != null) {
			long drawable = nativeCreate(state.bitmap.getTexture(), state.chunk);
			if (drawable == 0)
				throw new RuntimeException("not a nine-patch chunk");
			adoptPaintable(drawable);
			filter.apply(this);
		}
	}

	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
	    throws XmlPullParserException, IOException {
		TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.NinePatchDrawable);
		Drawable src = a.getDrawable(R.styleable.NinePatchDrawable_src);
		a.recycle();
		if (!(src instanceof NinePatchDrawable))
			throw new XmlPullParserException(parser.getPositionDescription() + ": <nine-patch> requires a valid 9-patch source image");
		setState(((NinePatchDrawable)src).state);
	}

	@Override
	public boolean getPadding(Rect padding) {
		if (state.padding == null)
			return super.getPadding(padding);
		padding.set(state.padding);
		return (padding.left | padding.top | padding.right | padding.bottom) != 0;
	}

	@Override
	public int getIntrinsicWidth() {
		return state.bitmap != null ? state.bitmap.getWidth() : -1;
	}

	@Override
	public int getIntrinsicHeight() {
		return state.bitmap != null ? state.bitmap.getHeight() : -1;
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		filter.colorFilter = colorFilter;
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
	public ConstantState getConstantState() {
		return state;
	}

	static final class NinePatchState extends ConstantState {
		final Bitmap bitmap;
		final byte[] chunk;
		final Rect padding;

		NinePatchState(Bitmap bitmap, byte[] chunk, Rect padding) {
			this.bitmap = bitmap;
			this.chunk = chunk;
			this.padding = padding != null ? new Rect(padding) : null;
		}

		@Override
		public Drawable newDrawable() {
			return new NinePatchDrawable(this);
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new NinePatchDrawable(this);
		}

		@Override
		public int getChangingConfigurations() {
			return 0;
		}
	}

	private static native long nativeCreate(long bitmap, byte[] chunk);
}
