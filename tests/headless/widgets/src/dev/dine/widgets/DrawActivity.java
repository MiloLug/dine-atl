package dev.dine.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

/* A view drawn through Canvas, text, a Layout, paths, arcs, gradients, a layer and a nine-patch
 * included, for run-draw.sh: headless compares its display lists with draw.displaylist, gtk draws
 * it. It redraws after invalidate(), not after a layout at the same size, and after showing again
 * from INVISIBLE with an invalidate() while hidden; then it finishes. Beside it, a view scrolled by
 * 10 px keeps its selector background in place. */
public class DrawActivity extends Activity {
	static final String TAG = "widgets";
	protected FrameLayout root;

	private class DrawView extends View {
		private final Bitmap photo = BitmapFactory.decodeResource(getResources(), R.drawable.photo);
		private final Drawable frame = getResources().getDrawable(R.drawable.frame);
		private final BitmapDrawable filtered = new BitmapDrawable(getResources(), photo);
		private final Drawable icon = getResources().getDrawable(R.drawable.adaptive); // masked through a BitmapShader
		private final Picture picture = textPicture();
		private int draws;

		DrawView(Context context) {
			super(context);
		}

		@Override
		public void onDraw(Canvas canvas) {
			Paint fill = new Paint();
			fill.setColor(0xffff0000);
			canvas.drawRect(10, 10, 110, 60, fill);

			Paint stroke = new Paint();
			stroke.setColor(0xff0000ff);
			stroke.setStyle(Paint.Style.STROKE);
			stroke.setStrokeWidth(4);
			canvas.drawLine(0, 0, 100, 100, stroke);
			canvas.drawLines(new float[] {0, 120, 50, 120, 50, 130, 100, 130}, stroke);

			Matrix matrix = new Matrix();
			matrix.preScale(2, 2, 10, 10);
			matrix.preRotate(90, 50, 50);
			canvas.save();
			canvas.concat(matrix);
			canvas.drawRect(0, 0, 20, 10, fill);
			canvas.restore();

			Paint copy = new Paint(fill);
			fill.setColor(0xff00ff00);
			canvas.drawRect(120, 10, 170, 60, copy);

			Paint blue = new Paint();
			blue.setColor(0xff0000ff);
			canvas.save();
			canvas.clipRect(180, 10, 200, 30);
			canvas.drawRect(170, 0, 210, 40, blue);
			canvas.restore();
			canvas.save();
			canvas.clipRect(220, 10, 240, 30, Region.Op.DIFFERENCE);
			canvas.drawRect(210, 0, 250, 40, blue);
			canvas.restore();
			canvas.drawBitmap(photo, null, new Rect(260, 0, 324, 64), null);

			// a copy keeps its paint's blend mode
			Paint clear = new Paint(fill);
			clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			canvas.drawRect(0, 140, 10, 150, new Paint(clear));

			Paint text = new Paint();
			text.setTextSize(30);
			text.setTypeface(Typeface.DEFAULT_BOLD);
			canvas.drawText("Dine bold", 10, 190, text);
			Paint serif = new Paint(text);
			serif.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
			canvas.drawText("serif italic", 10, 230, serif);
			Paint mono = new Paint(text);
			mono.setTypeface(Typeface.MONOSPACE);
			mono.setTextAlign(Paint.Align.CENTER);
			canvas.drawText("mono, centered", 160, 270, mono);
			Paint outline = new Paint(stroke);
			outline.setStrokeWidth(1);
			outline.setTextSize(30);
			canvas.drawText("outline", 10, 310, outline);
			StaticLayout layout = new StaticLayout("two lines from a Layout", new TextPaint(text), 300, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
			canvas.save();
			canvas.translate(330, 160);
			layout.draw(canvas);
			canvas.restore();

			Path shape = new Path();
			shape.moveTo(10, 330);
			shape.lineTo(60, 330);
			shape.quadTo(60, 380, 10, 380);
			shape.close();
			Path circle = new Path();
			circle.addCircle(35, 355, 20, Path.Direction.CW);
			canvas.save();
			canvas.clipPath(circle);
			canvas.drawPath(shape, fill);
			canvas.restore();
			canvas.drawArc(new RectF(70, 330, 120, 380), 0, 90, true, blue);
			canvas.drawArc(new RectF(130, 330, 180, 380), 0, 360, true, stroke); // the whole oval: no radius line
			canvas.drawArc(new RectF(190, 330, 240, 380), 180, 90, false, stroke);
			Paint gradient = new Paint();
			gradient.setShader(new LinearGradient(250, 330, 300, 330, 0xffff0000, 0xff0000ff, Shader.TileMode.MIRROR));
			canvas.drawRect(250, 330, 350, 380, gradient);
			Matrix shift = new Matrix();
			shift.setTranslate(10, 0);
			gradient.getShader().setLocalMatrix(shift); // after setShader: the next draw takes it
			canvas.drawRect(250, 385, 350, 395, gradient);
			Paint sweep = new Paint();
			sweep.setShader(new SweepGradient(505, 355, new int[] {0xffff0000, 0xff00ff00, 0xff0000ff}, null));
			canvas.drawCircle(505, 355, 25, sweep);
			GradientDrawable ring = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xffff0000, 0xff0000ff});
			ring.setGradientType(GradientDrawable.SWEEP_GRADIENT);
			ring.setBounds(540, 330, 590, 380);
			ring.draw(canvas);
			canvas.saveLayerAlpha(360, 330, 410, 380, 128);
			canvas.drawRect(360, 330, 410, 380, blue);
			canvas.drawCircle(385, 355, 10, copy);
			canvas.restore();
			frame.setBounds(420, 330, 460, 350); // stretched to 40x20; tinted below
			frame.draw(canvas);
			frame.setTint(0xff00ff00);
			frame.setBounds(470, 330, 473, 333); // smaller than its fixed parts: they shrink
			frame.draw(canvas);
			frame.setTintList(null);
			filtered.setColorFilter(0xff0000ff, PorterDuff.Mode.SRC_ATOP);
			filtered.setBounds(480, 330, 496, 346);
			filtered.draw(canvas);
			icon.setBounds(500, 330, 532, 362);
			icon.draw(canvas);
			ColorMatrix grey = new ColorMatrix();
			grey.setSaturation(0);
			Paint greyscale = new Paint();
			greyscale.setColorFilter(new ColorMatrixColorFilter(grey));
			canvas.drawBitmap(photo, null, new Rect(600, 330, 632, 362), greyscale);
			canvas.drawPicture(picture, new RectF(10, 400, 130, 430)); // replayed: its text stays text

			draws++;
			Log.i(TAG, "widgets draw layout lines " + layout.getLineCount());
			Log.i(TAG, "widgets draw " + draws);
			if (draws == 1) {
				post(new Runnable() {
					public void run() {
						invalidate();
					}
				});
			} else if (draws == 2) {
				post(new Runnable() {
					public void run() {
						afterLayout(new Runnable() {
							public void run() {
								hide();
							}
						});
					}
				});
			} else if (draws == 3) {
				post(new Runnable() {
					public void run() {
						Log.i(TAG, "widgets draw done");
						done();
					}
				});
			}
		}

		private void hide() {
			Log.i(TAG, "widgets draw hidden");
			setVisibility(View.INVISIBLE);
			invalidate();
			afterLayout(new Runnable() {
				public void run() {
					Log.i(TAG, "widgets draw shown");
					setVisibility(View.VISIBLE);
				}
			});
		}

		// next after a layout pass at the same size, and after that pass's draws
		private void afterLayout(final Runnable next) {
			getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				public void onGlobalLayout() {
					getViewTreeObserver().removeOnGlobalLayoutListener(this);
					post(next);
				}
			});
			requestLayout();
		}
	}

	protected void done() {
		finish();
	}

	private static Picture textPicture() {
		Picture picture = new Picture();
		Paint text = new Paint();
		text.setTextSize(20);
		picture.beginRecording(60, 15).drawText("picture", 0, 14, text);
		picture.endRecording();
		return picture;
	}

	private static Paint paint(int color) {
		Paint paint = new Paint();
		paint.setColor(color);
		return paint;
	}

	/* Canvas(Bitmap) and pixel access as Skia's raster canvas gives them (the graphics suite's
	 * BitmapPixelsTest), for the phone; and setPixel's cost against getPixel's. */
	private void logPixels() {
		StringBuilder wrong = new StringBuilder();
		Bitmap source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(source);
		canvas.drawColor(0xffff0000);
		Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, true);
		canvas.drawColor(0xff00ff00);
		if (source.getPixel(0, 0) != 0xff00ff00 || copy.getPixel(0, 0) != 0xffff0000)
			wrong.append(" copy");
		Bitmap shifted = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888);
		canvas = new Canvas(shifted);
		canvas.translate(2, 0);
		shifted.getPixel(0, 0);
		canvas.drawRect(0, 0, 1, 1, paint(0xff0000ff));
		canvas.drawBitmap(shifted, -1, 0, null); // into itself: pixel 2 lands on 3
		if (shifted.getPixel(2, 0) != 0xff0000ff || shifted.getPixel(3, 0) != 0xff0000ff || shifted.getPixel(0, 0) != 0)
			wrong.append(" state");
		canvas.saveLayer(null, null);
		canvas.drawRect(1, 0, 2, 1, paint(0xffff0000));
		if (shifted.getPixel(3, 0) != 0xff0000ff)
			wrong.append(" layer");
		canvas.restore();
		if (shifted.getPixel(3, 0) != 0xffff0000)
			wrong.append(" restore");
		Bitmap mask = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
		mask.setPixel(0, 0, 0xff123456);
		Bitmap masked = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		new Canvas(masked).drawBitmap(mask, 0, 0, paint(0xffff0000));
		if (mask.getPixel(0, 0) != 0xff000000 || masked.getPixel(0, 0) != 0xffff0000)
			wrong.append(" alpha8");
		Bitmap colors = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		colors.eraseColor(0xff00ff00);
		Paint shaded = paint(0xffff0000);
		shaded.setShader(new BitmapShader(colors, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
		new Canvas(masked).drawBitmap(mask, 0, 0, shaded); // the shader's colors where the mask is
		if (masked.getPixel(0, 0) != 0xff00ff00)
			wrong.append(" alpha8-shader ").append(Integer.toHexString(masked.getPixel(0, 0)));
		Bitmap rgb = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
		rgb.setPixel(0, 0, 0xff1c0000);
		if (rgb.getPixel(0, 0) != 0xff180000)
			wrong.append(" rgb565 ").append(Integer.toHexString(rgb.getPixel(0, 0)));
		Log.i(TAG, "widgets pixels" + (wrong.length() > 0 ? wrong : " ok"));

		Bitmap big = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
		long start = System.nanoTime();
		for (int y = 0; y < 128; y++)
			for (int x = 0; x < 128; x++)
				big.getPixel(x, y);
		long get = (System.nanoTime() - start) / 1000000;
		start = System.nanoTime();
		for (int y = 0; y < 128; y++)
			for (int x = 0; x < 128; x++)
				big.setPixel(x, y, 0xffff0000);
		long set = (System.nanoTime() - start) / 1000000;
		Log.i(TAG, "widgets pixels 128x128 getPixel " + get + " ms setPixel " + set + " ms");
	}

	// a Canvas(Bitmap)'s pixels, read back
	private void logBitmapPixel() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		Paint red = new Paint();
		red.setColor(0xffff0000);
		new Canvas(bitmap).drawRect(1, 1, 3, 3, red);
		Log.i(TAG, "widgets bitmap pixel " + Integer.toHexString(bitmap.getPixel(1, 1)) + " " + Integer.toHexString(bitmap.getPixel(0, 0)));
	}

	private static final int SOURCE = 0xccff8040, OPAQUE = 0xff3366cc, TRANSLUCENT = 0x80336699;

	private static Shader solid(int color) {
		return new LinearGradient(0, 0, 1, 0, color, color, Shader.TileMode.CLAMP);
	}

	/* a 1x1 bitmap's pixel after one draw in mode: kind 0 a paint's, 1 a layer's, 2 a ComposeShader's,
	 * a color filter's over 3 a bitmap, 4 a shader, 5 a bitmap at half alpha */
	private static int blended(int kind, BlendMode mode) {
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		Paint paint = new Paint();
		paint.setColor(SOURCE);
		if (kind == 0) {
			bitmap.eraseColor(OPAQUE);
			paint.setBlendMode(mode);
			canvas.drawRect(0, 0, 1, 1, paint);
		} else if (kind == 1) {
			bitmap.eraseColor(OPAQUE);
			Paint layer = new Paint();
			layer.setBlendMode(mode);
			canvas.saveLayer(null, layer);
			canvas.drawRect(0, 0, 1, 1, paint);
			canvas.restore();
		} else if (kind == 2) {
			paint.setShader(new ComposeShader(solid(TRANSLUCENT), solid(SOURCE), mode));
			canvas.drawRect(0, 0, 1, 1, paint);
		} else if (kind == 4) {
			Paint shaded = new Paint(); // opaque: the paint's alpha would scale the shader
			shaded.setShader(solid(TRANSLUCENT));
			shaded.setColorFilter(new BlendModeColorFilter(SOURCE, mode));
			canvas.drawRect(0, 0, 1, 1, shaded);
		} else {
			Paint filter = new Paint();
			filter.setColorFilter(new BlendModeColorFilter(SOURCE, mode));
			if (kind == 5)
				filter.setAlpha(0x80);
			canvas.drawBitmap(Bitmap.createBitmap(new int[] {TRANSLUCENT}, 1, 1, Bitmap.Config.ARGB_8888), 0, 0, filter);
		}
		return bitmap.getPixel(0, 0);
	}

	/* Skia's results by kind and BlendMode, from BlendModeTest's formulas (headless draws them
	 * exactly); a layer's are a paint's. */
	private static final String[] EXPECTED = {
		"0 ccff8040 ff3366cc ffd67a5c ff3366cc ccff8040 cc3367cc 0 333264cd ffd67a5c cc3367cc 333264cd ffffccff cc333333 ffd6a3d6 ff5c66b8 ff33665c ffd67acc ffd6b8f5 ff331452 ffd6667a ff6666bc ffad2999 ffad7aad ff333d52 ff964f3a ff2a67e1 ffa64a2b ff6796f5",
		"0 ccff8040 ff3366cc ffd67a5c ff3366cc ccff8040 cc3367cc 0 333264cd ffd67a5c cc3367cc 333264cd ffffccff cc333333 ffd6a3d6 ff5c66b8 ff33665c ffd67acc ffd6b8f5 ff331452 ffd6667a ff6666bc ffad2999 ffad7aad ff333d52 ff964f3a ff2a67e1 ffa64a2b ff6796f5",
		"0 a3ff8040 6632679b b8e77d49 b88d7272 52ff8041 5232679b 52ff803e 15316192 66d77b53 a399746e 66d77b53 cce59980 52323225 b8e7937d b8a4725b b88d7249 b8e77d72 b8e79e88 b88d442e b8e77250 b8a97264 b8d14f56 b8d17d6c b88d5a3e b8b66540 b87d7488 b8c9602e b8a98f8f",
		"0 ccff8040 80346699 e6e87d4a e68e7171 66ff8041 6635679b 66ff803f 1a316293 80d77c52 cc99736d 7fd77a52 ffe69980 66353226 e6e8937d e6a4715b e68e714a e6e87d71 e6e89f88 e68e442d e6e87150 e6aa7164 e6d05055 e6d07c6c e68e5b3e e6b66640 e67d7489 e6c95f2d e6ab8f8f",
		"0 ccff8040 80346699 e6e87d4a e68e7171 66ff8041 6635679b 66ff803f 1a316293 80d77c52 cc99736d 7fd77a52 ffe69980 66353226 e6e8937d e6a4715b e68e714a e6e87d71 e6e89f88 e68e442d e6e87150 e6aa7164 e6d05055 e6d07c6c e68e5b3e e6b66640 e67d7489 e6c95f2d e6ab8f8f",
		"0 ccff8040 4034689b d9f37f45 d9c3795a 33ff8241 3332699b 99ff803f d3b629d 40d77c54 cccc7958 a6ee7e47 ffd9805a 33333326 d9f38b60 d9cf794f d9c37945 d9f37f5a d9f39267 d9c36036 d9f37949 d9d27953 d9e6664b d9e67f58 d9c36c3f d9d97241 d9bb7a67 d9e37036 d9d2886a",
	};

	private static boolean near(int got, int want) {
		float gotAlpha = (got >>> 24) / 255f, wantAlpha = (want >>> 24) / 255f;
		for (int shift = 0; shift <= 24; shift += 8) {
			float g = ((got >> shift) & 0xff) * (shift == 24 ? 1 : gotAlpha), w = ((want >> shift) & 0xff) * (shift == 24 ? 1 : wantAlpha);
			if (Math.abs(g - w) > 3) // premultiplied, as the GPU and CG round
				return false;
		}
		return true;
	}

	private static int want(int kind, BlendMode mode) {
		return (int)Long.parseLong(EXPECTED[kind].split(" ")[mode.ordinal()], 16);
	}

	// a 1x1 bitmap's pixel: what draws onto dst, then that over below
	private static int over(int dst, BlendMode mode, int below) {
		Bitmap top = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		top.eraseColor(dst);
		Paint paint = paint(SOURCE);
		paint.setBlendMode(mode);
		new Canvas(top).drawRect(0, 0, 1, 1, paint);
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(below);
		new Canvas(bitmap).drawBitmap(top, 0, 0, null);
		return bitmap.getPixel(0, 0);
	}

	// 0-255 premultiplied, alpha first
	private static float[] premultiplied(int color) {
		float a = (color >>> 24) / 255f;
		return new float[] {a * 255, ((color >> 16) & 0xff) * a, ((color >> 8) & 0xff) * a, (color & 0xff) * a};
	}

	/* Blend modes where a 1x1 bitmap's readback can't show them: a clipped layer, nested layers, onto a
	 * layer's own content, at an antialiased edge; and a layer's cost. */
	private void logLayerBlends() {
		StringBuilder wrong = new StringBuilder();
		for (BlendMode mode : new BlendMode[] {BlendMode.MODULATE, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN, BlendMode.SOFT_LIGHT, BlendMode.PLUS, BlendMode.MULTIPLY}) {
			Paint layer = new Paint();
			layer.setBlendMode(mode);
			Bitmap quadrant = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
			quadrant.eraseColor(OPAQUE);
			Canvas canvas = new Canvas(quadrant);
			canvas.clipRect(0, 0, 2, 2);
			canvas.saveLayer(null, layer);
			canvas.drawRect(0, 0, 4, 4, paint(SOURCE));
			canvas.restore();
			if (!near(quadrant.getPixel(1, 1), want(1, mode)) || quadrant.getPixel(3, 0) != OPAQUE || quadrant.getPixel(0, 3) != OPAQUE || quadrant.getPixel(3, 3) != OPAQUE)
				wrong.append(" clipped ").append(mode).append(' ').append(Integer.toHexString(quadrant.getPixel(1, 1)));
			// pixel 4,4 is inside the circle's bounds, outside the circle
			Bitmap round = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
			round.eraseColor(OPAQUE);
			canvas = new Canvas(round);
			Path circle = new Path();
			circle.addCircle(8, 8, 4, Path.Direction.CW);
			canvas.clipPath(circle);
			canvas.saveLayer(null, layer);
			canvas.drawRect(0, 0, 16, 16, paint(SOURCE));
			canvas.restore();
			if (!near(round.getPixel(8, 8), want(1, mode)) || round.getPixel(4, 4) != OPAQUE)
				wrong.append(" path-clipped ").append(mode).append(' ').append(Integer.toHexString(round.getPixel(4, 4)));
			Bitmap nested = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
			nested.eraseColor(OPAQUE);
			canvas = new Canvas(nested);
			canvas.saveLayer(null, null);
			canvas.saveLayer(null, layer);
			canvas.drawRect(0, 0, 1, 1, paint(SOURCE));
			canvas.restore();
			canvas.restore();
			if (!near(nested.getPixel(0, 0), over(0, mode, OPAQUE)))
				wrong.append(" nested ").append(mode).append(' ').append(Integer.toHexString(nested.getPixel(0, 0)));
			Bitmap onto = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
			onto.eraseColor(OPAQUE);
			canvas = new Canvas(onto);
			canvas.saveLayer(null, null);
			canvas.drawColor(TRANSLUCENT);
			Paint paint = paint(SOURCE);
			paint.setBlendMode(mode);
			canvas.drawRect(0, 0, 1, 1, paint);
			canvas.restore();
			if (!near(onto.getPixel(0, 0), over(TRANSLUCENT, mode, OPAQUE)))
				wrong.append(" onto ").append(mode).append(' ').append(Integer.toHexString(onto.getPixel(0, 0)));
		}
		// an antialiased edge: Skia's lerp(d, mode(s, d), coverage)
		Bitmap edge = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), coverage = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
		edge.eraseColor(OPAQUE);
		Paint modulate = new Paint(Paint.ANTI_ALIAS_FLAG);
		modulate.setColor(SOURCE);
		modulate.setBlendMode(BlendMode.MODULATE);
		new Canvas(edge).drawCircle(4, 4, 2.6f, modulate);
		Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
		white.setColor(0xffffffff);
		new Canvas(coverage).drawCircle(4, 4, 2.6f, white);
		float[] d = premultiplied(OPAQUE), b = premultiplied(want(0, BlendMode.MODULATE));
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				float c = (coverage.getPixel(x, y) >>> 24) / 255f;
				if (c == 0 || c == 1)
					continue;
				float[] got = premultiplied(edge.getPixel(x, y));
				for (int i = 0; i < 4; i++)
					if (Math.abs(got[i] - (d[i] * (1 - c) + b[i] * c)) > 4) {
						wrong.append(" edge ").append(x).append(',').append(y).append(' ').append(Integer.toHexString(edge.getPixel(x, y)));
						x = y = 8;
						break;
					}
			}
		}
		Log.i(TAG, "widgets layers" + (wrong.length() > 0 ? wrong : " ok"));

		Bitmap big = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(big);
		Paint layer = new Paint();
		layer.setBlendMode(BlendMode.MODULATE);
		long start = System.nanoTime();
		for (int i = 0; i < 200; i++) {
			canvas.saveLayer(null, layer);
			canvas.drawRect(i % 60, 0, i % 60 + 4, 64, paint(SOURCE));
			canvas.restore();
		}
		big.getPixel(0, 0);
		Log.i(TAG, "widgets layers 200 MODULATE layers on 64x64 " + (System.nanoTime() - start) / 1000000 + " ms");
	}

	// every blend mode through a Canvas(Bitmap)
	private void logBlends() {
		String[] kinds = {"paint", "layer", "compose", "filter", "shader filter", "faded filter"};
		StringBuilder mismatches = new StringBuilder();
		for (int kind = 0; kind < kinds.length; kind++) {
			String[] expected = EXPECTED[kind].split(" ");
			for (BlendMode mode : BlendMode.values()) {
				int got = blended(kind, mode), want = (int)Long.parseLong(expected[mode.ordinal()], 16);
				if (!near(got, want))
					mismatches.append(' ').append(kinds[kind]).append(' ').append(mode).append(' ').append(Integer.toHexString(got)).append(" want ").append(Integer.toHexString(want));
			}
		}
		Log.i(TAG, "widgets blend" + (mismatches.length() > 0 ? mismatches : " ok"));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logBitmapPixel();
		logPixels();
		logBlends();
		logLayerBlends();
		root = new FrameLayout(this);
		root.addView(new DrawView(this));
		View scrolled = new View(this);
		scrolled.setBackgroundResource(R.drawable.shared_selector);
		scrolled.scrollTo(0, 10);
		root.addView(scrolled, new FrameLayout.LayoutParams(40, 40, Gravity.BOTTOM));
		ImageView tinted = new ImageView(this);
		tinted.setImageResource(R.drawable.photo);
		tinted.setImageTintList(ColorStateList.valueOf(0xffff0000));
		root.addView(tinted, new FrameLayout.LayoutParams(16, 16, Gravity.BOTTOM | Gravity.RIGHT));
		setContentView(root);
	}
}
