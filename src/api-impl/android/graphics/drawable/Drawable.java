package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.NinePatch;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.LayoutDirection;
import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Drawable {
	public static interface Callback {
		public void invalidateDrawable(Drawable drawable);
		public void scheduleDrawable(Drawable drawable, Runnable runnable, long time);
		public void unscheduleDrawable(Drawable drawable, Runnable runnable);
	}

	static final BlendMode DEFAULT_BLEND_MODE = BlendMode.SRC_IN;
	static final PorterDuff.Mode DEFAULT_TINT_MODE = PorterDuff.Mode.SRC_IN;

	private Rect mBounds = new Rect();
	private int[] mStateSet = new int[0];
	public long paintable;

	private Callback callback = null;

	public Drawable() {
		this.paintable = native_constructor();
	}

	// takes over the caller's ref, like adoptPaintable
	public Drawable(long paintable) {
		this.paintable = paintable;
	}

	// for a paintable owned elsewhere, such as a BitmapDrawable's
	protected void setPaintable(long paintable) {
		if (paintable != 0)
			native_ref(paintable);
		adoptPaintable(paintable);
	}

	// for a paintable a native just made: takes over its ref
	protected void adoptPaintable(long paintable) {
		if (this.paintable != 0)
			native_unref(this.paintable);
		this.paintable = paintable;
	}

	public int getChangingConfigurations() {
		return 0;
	}

	public void setChangingConfigurations(int bitmap) {}

	// none: Resources doesn't cache it, and containers that hold it share it
	public ConstantState getConstantState() {
		return null;
	}

	public static abstract class ConstantState {

		public abstract Drawable newDrawable(Resources res);

		public abstract Drawable newDrawable();

		public Drawable newDrawable(Resources res, Theme theme) {
			return newDrawable(res);
		}

		public abstract int getChangingConfigurations();
	}

	public void setBounds(int left, int top, int right, int bottom) {
		boolean changed = left != mBounds.left || top != mBounds.top || right != mBounds.right || bottom != mBounds.bottom;
		mBounds.set(left, top, right, bottom);
		if (changed)
			onBoundsChange(mBounds);
	}

	public void setBounds(Rect bounds) {
		setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
	}

	public final Rect getBounds() {
		return mBounds;
	}

	// a view's canvas, a Canvas(Bitmap) or a Picture's
	public void draw(Canvas canvas) {
		if (paintable == 0)
			return;
		if (mBounds.left != 0 || mBounds.top != 0)
			canvas.translate(mBounds.left, mBounds.top);
		canvas.atlDrawDrawable(this, mBounds.width(), mBounds.height());
		if (mBounds.left != 0 || mBounds.top != 0)
			canvas.translate(-mBounds.left, -mBounds.top);
	}

	public boolean setState(int[] stateSet) {
		if (!Arrays.equals(this.mStateSet, stateSet)) {
			this.mStateSet = stateSet;
			return onStateChange(stateSet);
		}
		return false;
	}

	public int[] getState() {
		return mStateSet;
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public Callback getCallback() {
		return this.callback;
	}

	public void invalidateSelf() {
		native_invalidate(paintable);

		/* this shouldn't ever be needed with Gtk, but let's play it safe for now */
		if (this.callback != null) {
			callback.invalidateDrawable(this);
		}
	}

	public void scheduleSelf(Runnable runnable, long time) {
		if (this.callback != null) {
			callback.scheduleDrawable(this, runnable, time);
		}
	}

	public void unscheduleSelf(Runnable runnable) {
		if (this.callback != null) {
			callback.unscheduleDrawable(this, runnable);
		}
	}

	public boolean isVisible() {
		return true;
	}

	public boolean setVisible(boolean visible, boolean restart) {
		return false;
	}

	protected static TypedArray obtainAttributes(Resources r, Theme theme, AttributeSet set, int[] attrs) {
		if (theme != null)
			return theme.obtainStyledAttributes(set, attrs, 0, 0);
		else
			return r.obtainAttributes(set, attrs);
	}

	public void clearColorFilter() {}

	private int mLevel = 0;

	public final int getLevel() {
		return mLevel;
	}

	// true: the drawable looks different, as AOSP's
	public final boolean setLevel(int level) {
		if (mLevel != level) {
			mLevel = level;
			return onLevelChange(level);
		}
		return false;
	}

	protected boolean onLevelChange(int level) {
		return false;
	}

	public void setColorFilter(int color, PorterDuff.Mode mode) {
		setColorFilter(new PorterDuffColorFilter(color, mode));
	}
	public void setColorFilter(ColorFilter filter) {}

	public ColorFilter getColorFilter() { return null; }

	public Drawable mutate() {
		return this;
	}

	public int getIntrinsicWidth() { return -1; }
	public int getIntrinsicHeight() { return -1; }

	public void setTintList(ColorStateList tint) {}

	public void setTint(int tint) {
		setTintList(ColorStateList.valueOf(tint));
	}

	/* Copyright (C) 2006 The Android Open Source Project: a tintMode attribute's enum value */
	public static PorterDuff.Mode parseTintMode(int value, PorterDuff.Mode defaultMode) {
		switch (value) {
			case 3: return PorterDuff.Mode.SRC_OVER;
			case 5: return PorterDuff.Mode.SRC_IN;
			case 9: return PorterDuff.Mode.SRC_ATOP;
			case 14: return PorterDuff.Mode.MULTIPLY;
			case 15: return PorterDuff.Mode.SCREEN;
			case 16: return PorterDuff.Mode.ADD;
			default: return defaultMode;
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public static BlendMode parseBlendMode(int value, BlendMode defaultMode) {
		switch (value) {
			case 3: return BlendMode.SRC_OVER;
			case 5: return BlendMode.SRC_IN;
			case 9: return BlendMode.SRC_ATOP;
			case 14: return BlendMode.MODULATE; // PorterDuff's MULTIPLY is Skia's modulate (b/73224934)
			case 15: return BlendMode.SCREEN;
			case 16: return BlendMode.PLUS;
			default: return defaultMode;
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	BlendModeColorFilter updateBlendModeFilter(BlendModeColorFilter blendFilter, ColorStateList tint, BlendMode blendMode) {
		if (tint == null || blendMode == null) {
			return null;
		}

		final int color = tint.getColorForState(getState(), Color.TRANSPARENT);
		if (blendFilter == null || blendFilter.getColor() != color
		    || blendFilter.getMode() != blendMode) {
			return new BlendModeColorFilter(color, blendMode);
		}
		return blendFilter;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	PorterDuffColorFilter updateTintFilter(PorterDuffColorFilter tintFilter, ColorStateList tint, PorterDuff.Mode tintMode) {
		System.out.println("updateTintFilter(" + tintFilter + ", " + tint + ", " + tintMode + ")");
		if (tint == null || tintMode == null) {
			return null;
		}
		final int color = tint.getColorForState(getState(), Color.TRANSPARENT);
		if (tintFilter == null || tintFilter.getColor() != color || tintFilter.getMode() != tintMode) {
			return new PorterDuffColorFilter(color, tintMode);
		}
		return tintFilter;
	}

	public boolean isStateful() {
		return false;
	}

	public void setTintMode(PorterDuff.Mode tintMode) {}

	public boolean isProjected() { return false; }

	public static Drawable createFromXml(Resources resources, XmlResourceParser parser) throws XmlPullParserException, IOException {
		return createFromXml(resources, parser, null);
	}

	public static Drawable createFromXml(Resources resources, XmlResourceParser parser, Theme theme) throws XmlPullParserException, IOException {
		int type;
		while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT)
			;
		if (type != XmlPullParser.START_TAG)
			throw new XmlPullParserException("No start tag found");

		return createFromXmlInner(resources, parser, parser, theme);
	}

	public static Drawable createFromXmlInner(Resources resources, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
		return createFromXmlInner(resources, parser, attrs, null);
	}

	public static Drawable createFromXmlInner(Resources resources, XmlPullParser parser, AttributeSet attrs, Theme theme) throws XmlPullParserException, IOException {
		switch (parser.getName()) {
			case "selector": {
				StateListDrawable drawable = new StateListDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "shape": {
				GradientDrawable drawable = new GradientDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "bitmap": {
				BitmapDrawable drawable = new BitmapDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "transition": {
				TransitionDrawable drawable = new TransitionDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "scale": {
				ScaleDrawable drawable = new ScaleDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "clip": {
				ClipDrawable drawable = new ClipDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "rotate": {
				RotateDrawable drawable = new RotateDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "animated-rotate": {
				AnimatedRotateDrawable drawable = new AnimatedRotateDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "color": {
				ColorDrawable drawable = new ColorDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "animated-selector": {
				AnimatedStateListDrawable drawable = new AnimatedStateListDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "ripple": {
				RippleDrawable drawable = new RippleDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "vector": {
				VectorDrawable drawable = new VectorDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "layer-list": {
				LayerDrawable drawable = new LayerDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "nine-patch": {
				NinePatchDrawable drawable = new NinePatchDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "animation-list": {
				AnimationDrawable drawable = new AnimationDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "level-list": {
				LevelListDrawable drawable = new LevelListDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "adaptive-icon": {
				AdaptiveIconDrawable drawable = new AdaptiveIconDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "inset": {
				InsetDrawable drawable = new InsetDrawable();
				drawable.inflate(resources, parser, attrs, theme);
				return drawable;
			}
			case "animated-vector": {
				return new AnimatedVectorDrawable();
			}
		}

		return null;
	}

	public static Drawable createFromResourceStream(Resources res, TypedValue value, InputStream is, String srcName,
	                                                Object opts) {
		if (is == null)
			return null;
		Rect pad = new Rect();
		Bitmap bm = BitmapFactory.decodeResourceStream(res, value, is, pad, null);
		return bm != null ? drawableFromBitmap(res, bm, pad, srcName) : null;
	}

	public static Drawable createFromPath(String path) {
		if (path == null)
			return null;
		Bitmap bm = BitmapFactory.decodeFile(path);
		return bm != null ? drawableFromBitmap(null, bm, new Rect(), path) : null;
	}

	// AOSP's: a valid nine-patch chunk makes a NinePatchDrawable
	private static Drawable drawableFromBitmap(Resources res, Bitmap bm, Rect pad, String srcName) {
		byte[] chunk = bm.getNinePatchChunk();
		if (NinePatch.isNinePatchChunk(chunk))
			return new NinePatchDrawable(res, bm, chunk, pad, srcName);
		return new BitmapDrawable(res, bm);
	}

	protected boolean onStateChange(int[] stateSet) {
		return false;
	}

	public void setAlpha(int alpha) {}

	public int getAlpha() {
		return 0xFF;
	}

	public boolean getPadding(Rect padding) {
		return false;
	}

	public void copyBounds(Rect bounds) {
		bounds.set(mBounds);
	}

	public int getMinimumWidth() {
		return 10; // FIXME
	}
	public int getMinimumHeight() {
		return 10; // FIXME
	}

	protected void onBoundsChange(Rect bounds) {}

	public void setDither(boolean dither) {}

	public void setAutoMirrored(boolean mirrored) {}

	public void jumpToCurrentState() {}

	public boolean setLayoutDirection(int layoutDirection) {
		return false;
	}

	public void setHotspot(float x, float y) {}

	public int getLayoutDirection() {
		return LayoutDirection.LTR;
	}

	static int resolveDensity(Resources r, int parentDensity) {
		final int densityDpi = r == null ? parentDensity : r.getDisplayMetrics().densityDpi;
		return densityDpi == 0 ? DisplayMetrics.DENSITY_DEFAULT : densityDpi;
	}

	public void setFilterBitmap(boolean filter) {}

	public void setHotspotBounds(int left, int top, int right, int bottom) {}

	public Drawable getCurrent() {
		return this;
	}

	public Rect copyBounds() {
		return new Rect(mBounds);
	}

	@SuppressWarnings("removal")
	protected void finalize() throws Throwable {
		try {
			if (paintable != 0)
				native_unref(paintable);
			paintable = 0;
		} finally {
			super.finalize();
		}
	}

	protected native long native_constructor();
	protected native void native_invalidate(long paintable);
	protected native void native_ref(long paintable);
	protected native void native_unref(long paintable);
	// mode -1: none
	static native void native_setColorFilter(long paintable, int mode, int color, float[] matrix);
}
