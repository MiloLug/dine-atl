package dev.dine.framework.graphics;

import android.atl.ATLCanvas;
import android.atl.ATLTestHooks;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import java.util.IdentityHashMap;

// every blend mode as a paint's, a color filter's and a layer's, against Skia's formulas
public class BlendModeTest extends android.test.InstrumentationTestCase {
	private static final int TOLERANCE = 3; // per premultiplied channel, 0-255: gsk renders on the GPU

	// Skia's blend of premultiplied s over d, channels 0-1 in r, g, b, a order
	static float[] blend(BlendMode mode, float[] s, float[] d) {
		float sa = s[3], da = d[3];
		float[] out = new float[4];
		float alphaOver = sa + da - sa * da;
		switch (mode) {
			case CLEAR: return out;
			case SRC: return s.clone();
			case DST: return d.clone();
			case SRC_OVER: return mix(s, 1, d, 1 - sa);
			case DST_OVER: return mix(d, 1, s, 1 - da);
			case SRC_IN: return mix(s, da, d, 0);
			case DST_IN: return mix(d, sa, s, 0);
			case SRC_OUT: return mix(s, 1 - da, d, 0);
			case DST_OUT: return mix(d, 1 - sa, s, 0);
			case SRC_ATOP: return mix(s, da, d, 1 - sa);
			case DST_ATOP: return mix(d, sa, s, 1 - da);
			case XOR: return mix(s, 1 - da, d, 1 - sa);
			case PLUS:
				for (int i = 0; i < 4; i++)
					out[i] = Math.min(s[i] + d[i], 1);
				return out;
			case MODULATE:
				for (int i = 0; i < 4; i++)
					out[i] = s[i] * d[i];
				return out;
			case SCREEN:
				for (int i = 0; i < 4; i++)
					out[i] = s[i] + d[i] - s[i] * d[i];
				return out;
			case HUE:
			case SATURATION:
			case COLOR:
			case LUMINOSITY:
				return nonSeparable(mode, s, d);
			default:
				for (int i = 0; i < 3; i++)
					out[i] = separable(mode, s[i], d[i], sa, da);
				out[3] = alphaOver;
				return out;
		}
	}

	private static float[] mix(float[] a, float fa, float[] b, float fb) {
		float[] out = new float[4];
		for (int i = 0; i < 4; i++)
			out[i] = a[i] * fa + b[i] * fb;
		return out;
	}

	// SkRasterPipeline's separable stages
	private static float separable(BlendMode mode, float s, float d, float sa, float da) {
		switch (mode) {
			case MULTIPLY: return s * (1 - da) + d * (1 - sa) + s * d;
			case DARKEN: return s + d - Math.max(s * da, d * sa);
			case LIGHTEN: return s + d - Math.min(s * da, d * sa);
			case DIFFERENCE: return s + d - 2 * Math.min(s * da, d * sa);
			case EXCLUSION: return s + d - 2 * s * d;
			case HARD_LIGHT: return hardLight(s, d, sa, da);
			case OVERLAY: return hardLight(d, s, da, sa);
			case COLOR_DODGE:
				if (d == 0)
					return s * (1 - da);
				if (s == sa)
					return s + d * (1 - sa);
				return sa * Math.min(da, (d * sa) / (sa - s)) + s * (1 - da) + d * (1 - sa);
			case COLOR_BURN:
				if (d == da)
					return d + s * (1 - da);
				if (s == 0)
					return d * (1 - sa);
				return sa * (da - Math.min(da, (da - d) * sa / s)) + s * (1 - da) + d * (1 - sa);
			case SOFT_LIGHT: {
				float m = da > 0 ? d / da : 0, s2 = 2 * s, m4 = 4 * m;
				float darkSrc = d * (sa + (s2 - sa) * (1 - m));
				float darkDst = (m4 * m4 + m4) * (m - 1) + 7 * m;
				float liteDst = (float)Math.sqrt(m) - m;
				float liteSrc = d * sa + da * (s2 - sa) * (4 * d <= da ? darkDst : liteDst);
				return s * (1 - da) + d * (1 - sa) + (s2 <= sa ? darkSrc : liteSrc);
			}
			default: throw new IllegalArgumentException(mode.toString());
		}
	}

	private static float hardLight(float s, float d, float sa, float da) {
		return s * (1 - da) + d * (1 - sa) + (2 * s <= sa ? 2 * s * d : sa * da - 2 * (da - d) * (sa - s));
	}

	// W3C's non-separable modes over unpremultiplied colors, composited as Skia does
	private static float[] nonSeparable(BlendMode mode, float[] s, float[] d) {
		float sa = s[3], da = d[3];
		float[] cs = new float[3], cb = new float[3];
		for (int i = 0; i < 3; i++) {
			cs[i] = sa > 0 ? s[i] / sa : 0;
			cb[i] = da > 0 ? d[i] / da : 0;
		}
		float[] b;
		switch (mode) {
			case HUE: b = setLum(setSat(cs, sat(cb)), lum(cb)); break;
			case SATURATION: b = setLum(setSat(cb, sat(cs)), lum(cb)); break;
			case COLOR: b = setLum(cs, lum(cb)); break;
			default: b = setLum(cb, lum(cs)); break;
		}
		float[] out = new float[4];
		for (int i = 0; i < 3; i++)
			out[i] = s[i] * (1 - da) + d[i] * (1 - sa) + sa * da * b[i];
		out[3] = sa + da - sa * da;
		return out;
	}

	private static float lum(float[] c) {
		return 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2];
	}

	private static float sat(float[] c) {
		return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2]));
	}

	private static float[] setLum(float[] c, float l) {
		float delta = l - lum(c);
		float[] out = {c[0] + delta, c[1] + delta, c[2] + delta};
		float lo = lum(out), min = Math.min(out[0], Math.min(out[1], out[2])), max = Math.max(out[0], Math.max(out[1], out[2]));
		for (int i = 0; i < 3; i++) {
			if (min < 0)
				out[i] = lo + (out[i] - lo) * lo / (lo - min);
			if (max > 1)
				out[i] = lo + (out[i] - lo) * (1 - lo) / (max - lo);
		}
		return out;
	}

	private static float[] setSat(float[] c, float s) {
		float max = Math.max(c[0], Math.max(c[1], c[2])), min = Math.min(c[0], Math.min(c[1], c[2]));
		float[] out = new float[3];
		for (int i = 0; i < 3; i++)
			out[i] = max > min ? (c[i] - min) * s / (max - min) : 0;
		return out;
	}

	static float[] premultiplied(int color) {
		float a = (color >>> 24) / 255f;
		return new float[] {((color >> 16) & 0xff) / 255f * a, ((color >> 8) & 0xff) / 255f * a, (color & 0xff) / 255f * a, a};
	}

	// getPixel's unpremultiplied color, premultiplied again for the comparison; null: a match
	private static String mismatch(String what, float[] expected, int actual) {
		float[] got = premultiplied(actual);
		for (int i = 0; i < 4; i++) {
			if (Math.abs(Math.round(expected[i] * 255) - Math.round(got[i] * 255)) > TOLERANCE)
				return String.format("%s: #%08x, want premultiplied %.0f,%.0f,%.0f,%.0f", what, actual, expected[0] * 255, expected[1] * 255, expected[2] * 255, expected[3] * 255);
		}
		return null;
	}

	private static void assertBlend(String what, float[] expected, int actual) {
		String mismatch = mismatch(what, expected, actual);
		if (mismatch != null)
			fail(mismatch);
	}

	// every mode's mismatch at once
	private static class Mismatches {
		private final StringBuilder all = new StringBuilder();
		String where = "";

		void check(String what, float[] expected, int actual) {
			String mismatch = mismatch(where + what, expected, actual);
			if (mismatch != null)
				all.append(all.length() > 0 ? "; " : "").append(mismatch);
		}

		void assertNone() {
			if (all.length() > 0)
				fail(all.toString());
		}
	}

	interface Checks {
		void run(Mismatches mismatches);
	}

	private static boolean viewPath; // the checks draw as a view does (gtk: GSK nodes, the display's renderer)
	private static final IdentityHashMap<Bitmap, ATLCanvas> views = new IdentityHashMap<>();
	private static Boolean viewPathWorks;

	// what a check draws into its target bitmap with: the bitmap's Canvas, or a view's recording over its pixels
	private static Canvas canvasFor(Bitmap bitmap) {
		if (!viewPath)
			return new Canvas(bitmap);
		ATLCanvas canvas = views.get(bitmap);
		if (canvas == null) {
			canvas = ATLTestHooks.newViewCanvas(bitmap.getWidth(), bitmap.getHeight());
			Paint copy = new Paint();
			copy.setBlendMode(BlendMode.SRC);
			canvas.drawBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false), 0, 0, copy);
			views.put(bitmap, canvas);
		}
		return canvas;
	}

	// a target's pixel: a view's recording is drawn into it at the first read
	private static int pixel(Bitmap bitmap, int x, int y) {
		ATLCanvas canvas = views.remove(bitmap);
		if (canvas != null)
			ATLTestHooks.drawViewCanvas(canvas, bitmap);
		return bitmap.getPixel(x, y);
	}

	// on the main thread: whether this backend draws views anywhere readable (headless doesn't)
	private static boolean viewPathWorks() {
		if (viewPathWorks == null) {
			ATLCanvas canvas = ATLTestHooks.newViewCanvas(1, 1);
			Paint red = new Paint();
			red.setColor(0xffff0000);
			canvas.drawRect(0, 0, 1, 1, red);
			Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
			viewPathWorks = ATLTestHooks.drawViewCanvas(canvas, bitmap) && bitmap.getPixel(0, 0) == 0xffff0000;
		}
		return viewPathWorks;
	}

	/* Into bitmaps, and again as a view draws them, on the main thread where the window's renderer
	 * runs: a backend may draw a view's blend differently (gsk: GSK's nodes on the GPU). */
	private void checkOnBoth(final Checks checks) {
		final Mismatches mismatches = new Mismatches();
		checks.run(mismatches);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				if (!viewPathWorks())
					return;
				mismatches.where = "view ";
				viewPath = true;
				try {
					checks.run(mismatches);
				} finally {
					viewPath = false;
					views.clear();
				}
			}
		});
		mismatches.assertNone();
	}

	/* The modes GSK has no blend mode for get their own tests: PLUS, and MODULATE of more than one
	 * color (a layer's, a ComposeShader's); gsk draws them with its arithmetic node. */
	private static boolean ownTest(BlendMode mode, boolean layer) {
		return mode == BlendMode.PLUS || (layer && mode == BlendMode.MODULATE);
	}

	private static final int SOURCE = 0xccff8040, OPAQUE = 0xff3366cc, TRANSLUCENT = 0x80336699;

	// a 4x4 bitmap of dst, the inner 2x2 drawn in src with the paint's mode
	private static Bitmap drawn(int src, int dst, BlendMode mode) {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(dst);
		Paint paint = new Paint();
		paint.setColor(src);
		paint.setBlendMode(mode);
		canvasFor(bitmap).drawRect(1, 1, 3, 3, paint);
		return bitmap;
	}

	private static void checkPaintMode(Mismatches mismatches, BlendMode mode, int dst) {
		Bitmap bitmap = drawn(SOURCE, dst, mode);
		mismatches.check(mode + " inside", blend(mode, premultiplied(SOURCE), premultiplied(dst)), pixel(bitmap, 2, 2));
		mismatches.check(mode + " outside", premultiplied(dst), pixel(bitmap, 0, 0));
	}

	private void checkPaintModes(final int dst) {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (BlendMode mode : BlendMode.values()) {
					if (!ownTest(mode, false))
						checkPaintMode(mismatches, mode, dst);
				}
			}
		});
	}

	public void testPaintModesOverOpaque() {
		checkPaintModes(OPAQUE);
	}

	public void testPaintModesOverTranslucent() {
		checkPaintModes(TRANSLUCENT);
	}

	public void testPaintModesOverTransparent() {
		checkPaintModes(0);
	}

	public void testPlusPaint() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (int dst : new int[] {OPAQUE, TRANSLUCENT, 0})
					checkPaintMode(mismatches, BlendMode.PLUS, dst);
			}
		});
	}

	// a filter blends its color (src) with what the paint draws (dst): one color, and a bitmap's
	private static void checkFilterMode(Mismatches mismatches, BlendMode mode) {
		Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setColor(OPAQUE);
		paint.setColorFilter(new BlendModeColorFilter(SOURCE, mode));
		canvasFor(bitmap).drawRect(0, 0, 2, 2, paint);
		mismatches.check(mode + " filter", blend(mode, premultiplied(SOURCE), premultiplied(OPAQUE)), pixel(bitmap, 1, 1));
		Bitmap source = Bitmap.createBitmap(new int[] {TRANSLUCENT}, 1, 1, Bitmap.Config.ARGB_8888);
		Bitmap drawn = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		Paint filter = new Paint();
		filter.setColorFilter(new BlendModeColorFilter(SOURCE, mode));
		canvasFor(drawn).drawBitmap(source, 0, 0, filter);
		mismatches.check(mode + " bitmap filter", blend(mode, premultiplied(SOURCE), premultiplied(TRANSLUCENT)), pixel(drawn, 0, 0)); // onto nothing
	}

	public void testColorFilterModes() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (BlendMode mode : BlendMode.values()) {
					if (!ownTest(mode, false))
						checkFilterMode(mismatches, mode);
				}
			}
		});
	}

	public void testPlusFilter() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				checkFilterMode(mismatches, BlendMode.PLUS);
			}
		});
	}

	// BlendMode.blendModeToPorterDuffMode's inverse
	private static BlendMode blendModeOf(PorterDuff.Mode mode) {
		switch (mode) {
			case MULTIPLY: return BlendMode.MODULATE;
			case ADD: return BlendMode.PLUS;
			default: return BlendMode.valueOf(mode.name());
		}
	}

	public void testPorterDuffFilterIsTheBlendModeFilter() {
		for (PorterDuff.Mode mode : PorterDuff.Mode.values()) {
			Bitmap a = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
			Paint paint = new Paint();
			paint.setColor(OPAQUE);
			paint.setColorFilter(new PorterDuffColorFilter(SOURCE, mode));
			canvasFor(a).drawRect(0, 0, 1, 1, paint);
			paint.setColorFilter(new BlendModeColorFilter(SOURCE, blendModeOf(mode)));
			canvasFor(b).drawRect(0, 0, 1, 1, paint);
			assertBlend(mode + " PorterDuff", premultiplied(b.getPixel(0, 0)), a.getPixel(0, 0));
		}
	}

	// MULTIPLY is Skia's modulate: transparent pixels stay transparent
	public void testMultiplyFilterKeepsTransparentPixels() {
		Bitmap source = Bitmap.createBitmap(new int[] {0, 0xffffffff}, 2, 1, Bitmap.Config.ARGB_8888);
		Bitmap bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setColorFilter(new PorterDuffColorFilter(0xff00ff00, PorterDuff.Mode.MULTIPLY));
		canvasFor(bitmap).drawBitmap(source, 0, 0, paint);
		assertEquals(0, pixel(bitmap, 0, 0));
		assertBlend("white", premultiplied(0xff00ff00), pixel(bitmap, 1, 0));
	}

	private static void checkLayerMode(Mismatches mismatches, BlendMode mode) {
		Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(OPAQUE);
		Canvas canvas = canvasFor(bitmap);
		Paint layer = new Paint();
		layer.setBlendMode(mode);
		canvas.saveLayer(null, layer);
		Paint paint = new Paint();
		paint.setColor(SOURCE);
		canvas.drawRect(0, 0, 2, 2, paint);
		canvas.restore();
		mismatches.check(mode + " layer", blend(mode, premultiplied(SOURCE), premultiplied(OPAQUE)), pixel(bitmap, 1, 1));
	}

	public void testLayerModes() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (BlendMode mode : BlendMode.values()) {
					if (!ownTest(mode, true))
						checkLayerMode(mismatches, mode);
				}
			}
		});
	}

	private static Shader solid(int color) {
		return new LinearGradient(0, 0, 1, 0, color, color, Shader.TileMode.CLAMP);
	}

	// a ComposeShader blends its second shader (src) onto its first (dst)
	private static void checkComposeMode(Mismatches mismatches, BlendMode mode) {
		Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setShader(new ComposeShader(solid(TRANSLUCENT), solid(SOURCE), mode));
		canvasFor(bitmap).drawRect(0, 0, 2, 2, paint);
		mismatches.check(mode + " compose", blend(mode, premultiplied(SOURCE), premultiplied(TRANSLUCENT)), pixel(bitmap, 1, 1)); // onto nothing
	}

	public void testComposeShaderModes() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (BlendMode mode : BlendMode.values()) {
					if (!ownTest(mode, true))
						checkComposeMode(mismatches, mode);
				}
			}
		});
	}

	public void testPlusAndModulateOfTwoSources() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				checkLayerMode(mismatches, BlendMode.PLUS);
				checkLayerMode(mismatches, BlendMode.MODULATE);
				checkComposeMode(mismatches, BlendMode.PLUS);
				checkComposeMode(mismatches, BlendMode.MODULATE);
			}
		});
	}

	// MODULATE of more than one color: a shader's, a bitmap's, a shader's glyphs
	public void testModulateOfManyColors() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				float[] want = blend(BlendMode.MODULATE, premultiplied(SOURCE), premultiplied(OPAQUE));
				Bitmap shaded = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
				shaded.eraseColor(OPAQUE);
				Paint shader = new Paint();
				shader.setShader(solid(SOURCE));
				shader.setBlendMode(BlendMode.MODULATE);
				canvasFor(shaded).drawRect(1, 1, 3, 3, shader);
				mismatches.check("MODULATE shader inside", want, pixel(shaded, 2, 2));
				mismatches.check("MODULATE shader outside", premultiplied(OPAQUE), pixel(shaded, 0, 0));
				Bitmap drawn = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
				drawn.eraseColor(OPAQUE);
				Paint modulate = new Paint();
				modulate.setBlendMode(BlendMode.MODULATE);
				canvasFor(drawn).drawBitmap(Bitmap.createBitmap(new int[] {SOURCE}, 1, 1, Bitmap.Config.ARGB_8888), null, new RectF(1, 1, 3, 3), modulate);
				mismatches.check("MODULATE bitmap inside", want, pixel(drawn, 2, 2));
				mismatches.check("MODULATE bitmap outside", premultiplied(OPAQUE), pixel(drawn, 0, 0));
				Bitmap colored = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), glyphs = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888);
				Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
				color.setColor(SOURCE);
				Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
				text.setShader(solid(SOURCE));
				for (Paint paint : new Paint[] {color, text}) {
					paint.setTextSize(24);
					paint.setBlendMode(BlendMode.MODULATE);
				}
				colored.eraseColor(OPAQUE);
				glyphs.eraseColor(OPAQUE);
				canvasFor(colored).drawText("W", 4, 24, color);
				canvasFor(glyphs).drawText("W", 4, 24, text);
				for (int y = 0; y < 30; y++)
					for (int x = 0; x < 40; x++)
						mismatches.check("MODULATE text " + x + "," + y, premultiplied(pixel(colored, x, y)), pixel(glyphs, x, y));
			}
		});
	}

	// a PLUS filter over a shader's colors and over a BitmapDrawable's bitmap
	public void testPlusFilterOverAShaderAndADrawable() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				float[] want = blend(BlendMode.PLUS, premultiplied(SOURCE), premultiplied(TRANSLUCENT));
				Bitmap shaded = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
				Paint paint = new Paint();
				paint.setShader(solid(TRANSLUCENT));
				paint.setColorFilter(new BlendModeColorFilter(SOURCE, BlendMode.PLUS));
				canvasFor(shaded).drawRect(0, 0, 2, 2, paint);
				mismatches.check("PLUS shader filter", want, pixel(shaded, 1, 1)); // onto nothing
				Bitmap target = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
				BitmapDrawable drawable = new BitmapDrawable(getInstrumentation().getTargetContext().getResources(),
				                                             Bitmap.createBitmap(new int[] {TRANSLUCENT}, 1, 1, Bitmap.Config.ARGB_8888));
				drawable.setColorFilter(new BlendModeColorFilter(SOURCE, BlendMode.PLUS));
				drawable.setBounds(0, 0, 2, 2);
				drawable.draw(canvasFor(target));
				mismatches.check("PLUS drawable filter", want, pixel(target, 1, 1));
			}
		});
	}

	/* Skia filters a shader's colors, then applies the shape: outside the shape nothing changes,
	 * even for a filter that gives transparent a color (SRC, a ColorMatrix with an alpha offset). */
	public void testFilteredShader() {
		final ColorMatrix lift = new ColorMatrix(new float[] {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0.5f, 64});
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				ColorFilter[] filters = {new BlendModeColorFilter(SOURCE, BlendMode.SRC), new BlendModeColorFilter(SOURCE, BlendMode.SRC_IN),
				                         new BlendModeColorFilter(SOURCE, BlendMode.MODULATE), new BlendModeColorFilter(SOURCE, BlendMode.SCREEN),
				                         new ColorMatrixColorFilter(lift)};
				String[] names = {"SRC", "SRC_IN", "MODULATE", "SCREEN", "matrix"};
				for (int i = 0; i < filters.length; i++) {
					Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
					bitmap.eraseColor(OPAQUE);
					Paint paint = new Paint();
					paint.setShader(solid(TRANSLUCENT));
					paint.setColorFilter(filters[i]);
					canvasFor(bitmap).drawCircle(8, 8, 6, paint);
					Bitmap reference = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // the filter over one color, exact everywhere
					Paint color = new Paint();
					color.setColor(TRANSLUCENT);
					color.setColorFilter(filters[i]);
					canvasFor(reference).drawRect(0, 0, 1, 1, color);
					float[] filtered = premultiplied(pixel(reference, 0, 0));
					mismatches.check(names[i] + " shader inside", blend(BlendMode.SRC_OVER, filtered, premultiplied(OPAQUE)), pixel(bitmap, 8, 8));
					mismatches.check(names[i] + " shader outside", premultiplied(OPAQUE), pixel(bitmap, 2, 2)); // in its bounding box
				}
			}
		});
	}

	// Skia scales a bitmap by the paint's alpha before its color filter
	public void testPaintAlphaBeforeFilter() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (BlendMode mode : new BlendMode[] {BlendMode.SRC, BlendMode.DST_OVER, BlendMode.XOR, BlendMode.SRC_IN}) {
					Bitmap source = Bitmap.createBitmap(new int[] {TRANSLUCENT}, 1, 1, Bitmap.Config.ARGB_8888);
					Bitmap drawn = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
					Paint paint = new Paint();
					paint.setAlpha(0x80);
					paint.setColorFilter(new BlendModeColorFilter(SOURCE, mode));
					canvasFor(drawn).drawBitmap(source, 0, 0, paint);
					float[] faded = premultiplied(TRANSLUCENT);
					for (int i = 0; i < 4; i++)
						faded[i] *= 0x80 / 255f;
					mismatches.check(mode + " alpha then filter", blend(mode, premultiplied(SOURCE), faded), pixel(drawn, 0, 0)); // onto nothing
				}
			}
		});
	}

	private static void checkOpaque(Mismatches mismatches, String what, Bitmap bitmap) {
		for (int y = 0; y < bitmap.getHeight(); y++)
			for (int x = 0; x < bitmap.getWidth(); x++)
				if (pixel(bitmap, x, y) >>> 24 != 0xff) {
					mismatches.check(what + " edge " + x + "," + y, new float[] {0, 0, 0, 1}, pixel(bitmap, x, y));
					return;
				}
	}

	/* A Porter-Duff mode takes the coverage once: an opaque source over opaque stays opaque at its
	 * edge, at any scale, a bitmap's rotated edge included. */
	public void testPorterDuffEdgesStayOpaque() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				for (BlendMode mode : new BlendMode[] {BlendMode.SRC, BlendMode.SRC_ATOP, BlendMode.DST_OVER}) {
					for (float scale : new float[] {1, 0.2f}) {
						Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
						bitmap.eraseColor(OPAQUE);
						Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
						paint.setColor(0xffff0000);
						paint.setBlendMode(mode);
						Canvas canvas = canvasFor(bitmap);
						canvas.scale(scale, scale);
						canvas.drawCircle(8 / scale, 8 / scale, 5.5f / scale, paint);
						checkOpaque(mismatches, mode + " at " + scale, bitmap);
					}
					Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
					bitmap.eraseColor(OPAQUE);
					Bitmap red = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
					red.eraseColor(0xffff0000);
					Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
					paint.setBlendMode(mode);
					Canvas canvas = canvasFor(bitmap);
					canvas.rotate(30, 8, 8);
					canvas.drawBitmap(red, null, new RectF(4.3f, 4.3f, 11.7f, 11.7f), paint);
					checkOpaque(mismatches, mode + " rotated bitmap", bitmap);
				}
			}
		});
	}

	// a shader's edge has a color's coverage, under rotation too
	public void testShaderEdgesAsAColors() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				Bitmap colored = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888), shaded = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
				Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
				color.setColor(0xffff0000);
				Paint shader = new Paint(Paint.ANTI_ALIAS_FLAG);
				shader.setShader(solid(0xffff0000));
				for (Object[] pair : new Object[][] {{colored, color}, {shaded, shader}}) {
					Canvas canvas = canvasFor((Bitmap)pair[0]);
					canvas.rotate(30, 8, 8);
					canvas.drawRect(4, 4, 12, 12, (Paint)pair[1]);
				}
				for (int y = 0; y < 16; y++)
					for (int x = 0; x < 16; x++)
						mismatches.check("shader " + x + "," + y, premultiplied(pixel(colored, x, y)), pixel(shaded, x, y));
			}
		});
	}

	// text with a shader through a filter draws as text in the shader's color through it
	public void testShadedTextIsFiltered() {
		checkOnBoth(new Checks() {
			public void run(Mismatches mismatches) {
				Bitmap colored = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), shaded = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888);
				Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
				color.setColor(TRANSLUCENT);
				Paint shader = new Paint(Paint.ANTI_ALIAS_FLAG);
				shader.setShader(solid(TRANSLUCENT));
				for (Paint paint : new Paint[] {color, shader}) {
					paint.setTextSize(24);
					paint.setColorFilter(new BlendModeColorFilter(SOURCE, BlendMode.SRC_IN));
				}
				canvasFor(colored).drawText("W", 4, 24, color);
				canvasFor(shaded).drawText("W", 4, 24, shader);
				for (int y = 0; y < 30; y++)
					for (int x = 0; x < 40; x++)
						mismatches.check("text " + x + "," + y, premultiplied(pixel(colored, x, y)), pixel(shaded, x, y));
			}
		});
	}

	// CLEAR clears what it draws, inside the clip, and nothing else
	public void testClearInsideAClip() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(OPAQUE);
		Canvas canvas = new Canvas(bitmap);
		canvas.clipRect(0, 0, 2, 4);
		Paint clear = new Paint();
		clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		canvas.drawRect(1, 1, 3, 3, clear);
		assertEquals(0, bitmap.getPixel(1, 1));
		assertEquals(OPAQUE, bitmap.getPixel(2, 2));
		assertEquals(OPAQUE, bitmap.getPixel(0, 0));
	}

	public void testBlendModeOfAPorterDuffXfermode() {
		Paint paint = new Paint();
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
		assertEquals(BlendMode.MODULATE, paint.getBlendMode());
		paint.setBlendMode(BlendMode.SCREEN);
		assertEquals(BlendMode.SCREEN, paint.getBlendMode());
	}
}
