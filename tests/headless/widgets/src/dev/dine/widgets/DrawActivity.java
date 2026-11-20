package dev.dine.widgets;

import android.app.Activity;
import android.atl.ATLCanvas;
import android.atl.ATLTestHooks;
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
 * it. It redraws after invalidate(), not after a layout at the same size (gtk does: ATL-131), and after showing again
 * from INVISIBLE with an invalidate() while hidden; then it finishes. Beside it, a view scrolled by
 * 10 px keeps its selector background in place. onCreate also logs what the bitmap canvases and the
 * view's own replay (a recording read back through ATLTestHooks: gtk's GSK, the phone's CoreGraphics)
 * draw: `widgets pixels`, `blend`, `layers` and `view pixels` are `ok` or name what read wrong. */
public class DrawActivity extends Activity {
	static final String TAG = "widgets";
	protected FrameLayout root;
	private View scrolled;

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
			canvas.drawBitmap(photo, new Rect(0, 0, 0, 64), new Rect(260, 70, 324, 134), null); // a source with no area: not drawn

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
						resize();
					}
				});
			}
		}

		// the 40x40 background view gets a wider frame and nothing invalidates it: the draw pass
		// finds the new size
		private void resize() {
			scrolled.setLayoutParams(new FrameLayout.LayoutParams(60, 40, Gravity.BOTTOM));
			afterLayout(new Runnable() {
				public void run() {
					Log.i(TAG, "widgets draw resized");
					Log.i(TAG, "widgets draw done");
					done();
				}
			});
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
		// SkRRect shrinks a corner's two radii alike: a radius past half the height gives round ends
		Bitmap pill = Bitmap.createBitmap(200, 40, Bitmap.Config.ARGB_8888);
		new Canvas(pill).drawRoundRect(0, 0, 200, 40, 40, 40, paint(0xffff0000));
		if (pill.getPixel(12, 4) != 0xffff0000 || pill.getPixel(2, 2) != 0)
			wrong.append(" pill ").append(Integer.toHexString(pill.getPixel(12, 4)));
		// stops of different alpha blend premultiplied: white that fades out stays white
		Bitmap fade = Bitmap.createBitmap(16, 1, Bitmap.Config.ARGB_8888);
		fade.eraseColor(0xffffffff);
		Paint fading = new Paint();
		fading.setShader(new LinearGradient(0, 0, 16, 0, 0xffffffff, 0x00000000, Shader.TileMode.CLAMP));
		new Canvas(fade).drawRect(0, 0, 16, 1, fading);
		if ((fade.getPixel(8, 0) & 0xff) < 0xf8)
			wrong.append(" fade ").append(Integer.toHexString(fade.getPixel(8, 0)));
		// Skia's miter limit is 4: a sharper join is beveled
		Bitmap spike = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888);
		Paint stroke = paint(0xffff0000);
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeWidth(4);
		Path corner = new Path();
		corner.moveTo(8, 60);
		corner.lineTo(16, 10);
		corner.lineTo(24, 60);
		new Canvas(spike).drawPath(corner, stroke);
		if (spike.getPixel(16, 3) != 0)
			wrong.append(" miter");
		// SkPaint's fill-and-stroke covers the union once
		Bitmap both = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
		Paint fillAndStroke = paint(0x80ff0000);
		fillAndStroke.setStyle(Paint.Style.FILL_AND_STROKE);
		fillAndStroke.setStrokeWidth(8);
		new Canvas(both).drawRect(8, 8, 24, 24, fillAndStroke);
		if (both.getPixel(10, 10) != both.getPixel(16, 16))
			wrong.append(" fill-and-stroke ").append(Integer.toHexString(both.getPixel(10, 10)));
		// a scaled bitmap is sampled nearest without Paint's FILTER_BITMAP_FLAG, bilinear with it
		Bitmap two = Bitmap.createBitmap(new int[] {0xffff0000, 0xff0000ff}, 2, 1, Bitmap.Config.ARGB_8888);
		for (boolean filter : new boolean[] {false, true}) {
			Paint sampling = new Paint();
			sampling.setFilterBitmap(filter);
			Bitmap scaled = Bitmap.createBitmap(8, 1, Bitmap.Config.ARGB_8888);
			new Canvas(scaled).drawBitmap(two, null, new Rect(0, 0, 8, 1), sampling);
			boolean blended = false;
			for (int x = 0; x < 8; x++)
				blended |= scaled.getPixel(x, 0) != 0xffff0000 && scaled.getPixel(x, 0) != 0xff0000ff;
			if (blended != filter)
				wrong.append(filter ? " bilinear " : " nearest ").append(Integer.toHexString(scaled.getPixel(3, 0))).append(',').append(Integer.toHexString(scaled.getPixel(4, 0)));
		}
		// and a BitmapShader samples as the paint's flag says too
		for (boolean filter : new boolean[] {false, true}) {
			BitmapShader shader = new BitmapShader(two, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
			Matrix stretch = new Matrix();
			stretch.setScale(4, 1);
			shader.setLocalMatrix(stretch);
			Paint sampling = new Paint();
			sampling.setShader(shader);
			sampling.setFilterBitmap(filter);
			Bitmap scaled = Bitmap.createBitmap(8, 1, Bitmap.Config.ARGB_8888);
			new Canvas(scaled).drawRect(0, 0, 8, 1, sampling);
			boolean blended = false;
			for (int x = 0; x < 8; x++)
				blended |= scaled.getPixel(x, 0) != 0xffff0000 && scaled.getPixel(x, 0) != 0xff0000ff;
			if (blended != filter)
				wrong.append(filter ? " shader-bilinear " : " shader-nearest ").append(Integer.toHexString(scaled.getPixel(3, 0))).append(',').append(Integer.toHexString(scaled.getPixel(4, 0)));
		}
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

	// the display list names a bitmap by its size only: the decoded PNG's four quarters
	private void logPhotoPixels() {
		Bitmap photo = BitmapFactory.decodeResource(getResources(), R.drawable.photo);
		StringBuilder pixels = new StringBuilder();
		for (int y = 16; y < 64; y += 32)
			for (int x = 16; x < 64; x += 32)
				pixels.append(' ').append(Integer.toHexString(photo.getPixel(x, y)));
		Log.i(TAG, "widgets photo pixels" + pixels);
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
		boolean partial = false;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				float c = (coverage.getPixel(x, y) >>> 24) / 255f;
				if (c == 0 || c == 1)
					continue;
				partial = true;
				float[] got = premultiplied(edge.getPixel(x, y));
				for (int i = 0; i < 4; i++)
					if (Math.abs(got[i] - (d[i] * (1 - c) + b[i] * c)) > 4) {
						wrong.append(" edge ").append(x).append(',').append(y).append(' ').append(Integer.toHexString(edge.getPixel(x, y)));
						x = y = 8;
						break;
					}
			}
		}
		if (!partial) // nothing was compared
			wrong.append(" edge none");
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

	private interface Draw {
		void draw(Canvas canvas);
	}

	/* What a view's recording draws, read back through the test hook: the canvas's px as a layer shows
	 * them at `scale` px per point (CoreGraphics only: the others draw at 1); null: this build draws no
	 * views, or none at that scale. */
	private static Bitmap rendered(int width, int height, float scale, Draw draw) {
		ATLCanvas canvas = ATLTestHooks.newViewCanvas(width, height);
		draw.draw(canvas);
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		return ATLTestHooks.drawViewCanvas(canvas, bitmap, scale) ? bitmap : null;
	}

	private static Bitmap rendered(int width, int height, Draw draw) {
		return rendered(width, height, 1, draw);
	}

	private static void expect(StringBuilder wrong, String what, Bitmap bitmap, int x, int y, int want) {
		if (!near(bitmap.getPixel(x, y), want))
			wrong.append(' ').append(what).append(' ').append(x).append(',').append(y).append(' ').append(Integer.toHexString(bitmap.getPixel(x, y)));
	}

	/* A view's draw as the backend replays it (gtk: GSK's nodes, the phone: CoreGraphics' replay of the
	 * list; headless draws no views): at scale 1, clips and the restore that ends them, a PNG, a drawable
	 * through a color filter and a gradient; at each scale the backend draws (1 on gtk, 1 and 3 on the
	 * phone) a clip edge, a stroke, a hairline and text. */
	private void logViewPixels() {
		StringBuilder wrong = new StringBuilder();
		Bitmap clips = rendered(32, 16, new Draw() {
			public void draw(Canvas canvas) {
				Paint red = paint(0xffff0000);
				canvas.save();
				canvas.clipRect(0, 0, 8, 8);
				canvas.drawRect(0, 0, 32, 16, red);
				canvas.restore();
				canvas.drawRect(24, 0, 32, 8, paint(0xff00ff00)); // outside the first clip, which the restore ended
				canvas.save();
				canvas.clipRect(8, 8, 16, 16, Region.Op.DIFFERENCE);
				canvas.drawRect(0, 8, 32, 16, paint(0xff0000ff));
				canvas.restore();
			}
		});
		if (clips == null) {
			Log.i(TAG, "widgets view pixels none: this build draws no views");
			return;
		}
		expect(wrong, "intersect", clips, 4, 4, 0xffff0000);
		expect(wrong, "intersect-outside", clips, 20, 4, 0);
		expect(wrong, "unclip", clips, 28, 4, 0xff00ff00);
		expect(wrong, "difference", clips, 4, 12, 0xff0000ff);
		expect(wrong, "difference-after", clips, 20, 12, 0xff0000ff);
		expect(wrong, "difference-inside", clips, 12, 12, 0);

		final Bitmap photo = BitmapFactory.decodeResource(getResources(), R.drawable.photo);
		Bitmap png = rendered(64, 64, new Draw() {
			public void draw(Canvas canvas) {
				canvas.drawBitmap(photo, 0, 0, null);
			}
		});
		for (int[] at : new int[][] {{16, 16}, {48, 16}, {16, 48}, {48, 48}})
			expect(wrong, "png", png, at[0], at[1], photo.getPixel(at[0], at[1]));

		Bitmap tinted = rendered(16, 16, new Draw() {
			public void draw(Canvas canvas) {
				BitmapDrawable drawable = new BitmapDrawable(getResources(), photo);
				drawable.setColorFilter(0xff0000ff, PorterDuff.Mode.SRC_ATOP);
				drawable.setBounds(0, 0, 16, 16);
				drawable.draw(canvas);
			}
		});
		expect(wrong, "filtered-drawable", tinted, 8, 8, 0xff0000ff);

		Bitmap shaded = rendered(32, 8, new Draw() {
			public void draw(Canvas canvas) {
				Paint gradient = new Paint();
				gradient.setShader(new LinearGradient(0, 0, 32, 0, 0xffff0000, 0xff0000ff, Shader.TileMode.CLAMP));
				canvas.drawRect(0, 0, 32, 8, gradient);
			}
		});
		int left = shaded.getPixel(1, 4), right = shaded.getPixel(30, 4);
		if (left >>> 24 != 0xff || (left >> 16 & 0xff) < 0xe0 || (left & 0xff) > 0x20 || right >>> 24 != 0xff || (right & 0xff) < 0xe0 || (right >> 16 & 0xff) > 0x20)
			wrong.append(" shader ").append(Integer.toHexString(left)).append(',').append(Integer.toHexString(right));

		StringBuilder scales = new StringBuilder();
		for (int scale : new int[] {1, 3}) {
			if (scaledViewPixels(scale, wrong))
				scales.append(' ').append(scale);
		}
		Log.i(TAG, "widgets view pixels" + (wrong.length() > 0 ? wrong : " ok scales" + scales));
	}

	/* A clip's edge, a stroke, a hairline and text on white, drawn as a layer at `scale` px per point
	 * draws them (the list's px go through cg_list_draw's and cg_text_line's 1/scale): the clip and the
	 * lines on whole device pixels, the text with its ink's extent the glyph box's, and the white
	 * background where nothing was drawn. false: this build draws none at that scale. */
	private boolean scaledViewPixels(final int scale, StringBuilder wrong) {
		final Paint ink = paint(0xff000000);
		ink.setTextSize(24);
		final Rect box = new Rect();
		ink.getTextBounds("Dine", 0, 4, box);
		box.offset(4, 58); // drawn at 4, 58
		Bitmap bitmap = rendered(96, 64, scale, new Draw() {
			public void draw(Canvas canvas) {
				canvas.drawRect(0, 0, 96, 64, paint(0xffffffff));
				canvas.save();
				canvas.clipRect(0, 0, 46, 12);
				canvas.drawRect(0, 0, 96, 12, paint(0xffff0000));
				canvas.restore();
				Paint stroke = paint(0xffff0000);
				stroke.setStrokeWidth(4);
				canvas.drawLine(2, 18, 40, 18, stroke);
				canvas.drawLine(2, 22.5f, 40, 22.5f, paint(0xffff0000)); // a hairline
				canvas.drawText("Dine", 4, 58, ink);
			}
		});
		if (bitmap == null)
			return false;
		String at = "@" + scale;
		expect(wrong, "clip-in" + at, bitmap, 45, 6, 0xffff0000);
		expect(wrong, "clip-out" + at, bitmap, 46, 6, 0xffffffff);
		expect(wrong, "stroke-top" + at, bitmap, 20, 16, 0xffff0000);
		expect(wrong, "stroke-bottom" + at, bitmap, 20, 19, 0xffff0000);
		expect(wrong, "stroke-above" + at, bitmap, 20, 15, 0xffffffff);
		expect(wrong, "stroke-below" + at, bitmap, 20, 20, 0xffffffff);
		expect(wrong, "hairline" + at, bitmap, 20, 22, 0xffff0000);
		expect(wrong, "hairline-above" + at, bitmap, 20, 21, 0xffffffff);
		expect(wrong, "hairline-below" + at, bitmap, 20, 23, 0xffffffff);
		expect(wrong, "background" + at, bitmap, 92, 62, 0xffffffff);
		/* the ink's extent is the glyph box's, within 10 px (a line is laid out in points, so another scale changes
		 * its advances: 5 px of 50 at scale 1 on the phone). A text at the wrong scale is off by more: a third of
		 * the size by 34 px at the right edge and 12 at the top, one drawn at a wrong place by its offset. The
		 * measured extents go to a line of their own. */
		int left = 96, top = 64, right = -1, bottom = -1;
		for (int y = 28; y < 64; y++) {
			for (int x = 0; x < 96; x++) {
				int pixel = bitmap.getPixel(x, y);
				if (pixel == 0xffffffff || pixel >>> 24 == 0) // the background, or nothing drawn
					continue;
				left = Math.min(left, x);
				top = Math.min(top, y);
				right = Math.max(right, x + 1);
				bottom = Math.max(bottom, y + 1);
			}
		}
		Log.i(TAG, "widgets view text scale " + scale + " ink [" + left + "," + top + "][" + right + "," + bottom + "] box " + box.toShortString());
		if (right < 0 || Math.abs(left - box.left) > 10 || Math.abs(top - box.top) > 10 || Math.abs(right - box.right) > 10 || Math.abs(bottom - box.bottom) > 10)
			wrong.append(" text").append(at).append(" ink [").append(left).append(',').append(top).append("][").append(right).append(',').append(bottom).append("] box ").append(box.toShortString());
		return true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logBitmapPixel();
		logPhotoPixels();
		logPixels();
		logBlends();
		logLayerBlends();
		logViewPixels();
		root = new FrameLayout(this);
		root.addView(new DrawView(this));
		scrolled = new View(this);
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
