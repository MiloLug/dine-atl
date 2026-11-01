package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.NinePatch;
import android.graphics.PorterDuff;
import android.graphics.Rect;

/* AOSP 10's API over the gfx slot's nine-patch drawable; copies from the constant state share its
 * bitmap and chunk. */
public class NinePatchDrawable extends Drawable {
	private final NinePatchState state;
	private ColorStateList tint;

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

	// <nine-patch> XML, which ATL doesn't inflate: draws nothing
	NinePatchDrawable() {
		this(new NinePatchState(null, null, null));
	}

	private NinePatchDrawable(NinePatchState state) {
		this.state = state;
		if (state.bitmap != null && state.chunk != null) {
			long drawable = nativeCreate(state.bitmap.getTexture(), state.chunk);
			if (drawable == 0)
				throw new RuntimeException("not a nine-patch chunk");
			adoptPaintable(drawable);
		}
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
	public void setTintList(ColorStateList tint) {
		this.tint = tint;
		updateTint();
		invalidateSelf();
	}

	@Override
	public boolean isStateful() {
		return tint != null && tint.isStateful();
	}

	@Override
	protected boolean onStateChange(int[] stateSet) {
		if (!isStateful())
			return false;
		updateTint();
		return true;
	}

	// Android's default tint mode
	private void updateTint() {
		if (paintable == 0)
			return;
		if (tint == null)
			nativeSetColorFilter(paintable, -1, 0);
		else
			nativeSetColorFilter(paintable, PorterDuff.Mode.SRC_IN.nativeInt, tint.getColorForState(getState(), tint.getDefaultColor()));
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
	private static native void nativeSetColorFilter(long drawable, int mode, int color);
}
