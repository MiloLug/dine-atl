package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

// a Canvas(Bitmap) draws straight into the pixels, as Skia's raster canvas does
public class BitmapPixelsTest extends android.test.InstrumentationTestCase {
	private static final int RED = 0xffff0000, GREEN = 0xff00ff00, BLUE = 0xff0000ff;

	private static Paint paint(int color) {
		Paint paint = new Paint();
		paint.setColor(color);
		return paint;
	}

	private static Bitmap bitmap(int width, int height) {
		return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	}

	public void testCopyKeepsItsPixelsWhenTheSourceChanges() {
		Bitmap source = bitmap(4, 4);
		Canvas canvas = new Canvas(source);
		canvas.drawRect(0, 0, 4, 4, paint(RED));
		Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, true);
		Bitmap part = Bitmap.createBitmap(source, 0, 0, 2, 2);
		Bitmap scaled = Bitmap.createScaledBitmap(source, 8, 8, false);
		canvas.drawRect(0, 0, 4, 4, paint(GREEN));
		assertEquals(GREEN, source.getPixel(1, 1));
		assertEquals(RED, copy.getPixel(1, 1));
		assertEquals(RED, part.getPixel(1, 1));
		assertEquals(RED, scaled.getPixel(1, 1));
	}

	// a setPixel writes one pixel, not the whole bitmap
	public void testSetPixelCostsAboutAsMuchAsGetPixel() {
		Bitmap bitmap = bitmap(256, 256);
		bitmap.eraseColor(BLUE);
		long start = System.nanoTime();
		int sum = 0;
		for (int y = 0; y < 256; y++)
			for (int x = 0; x < 256; x++)
				sum += bitmap.getPixel(x, y);
		long get = (System.nanoTime() - start) / 1000000;
		start = System.nanoTime();
		for (int y = 0; y < 256; y++)
			for (int x = 0; x < 256; x++)
				bitmap.setPixel(x, y, RED);
		assertEquals(RED, bitmap.getPixel(255, 255));
		long set = (System.nanoTime() - start) / 1000000;
		Log.i("BitmapPixelsTest", "256x256 getPixel " + get + " ms, setPixel " + set + " ms (" + sum + ")");
		assertTrue("setPixel " + set + " ms, getPixel " + get + " ms", set <= 4 * get + 50);
	}

	public void testAShaderSeesLaterDrawsIntoItsBitmap() {
		Bitmap pattern = bitmap(2, 2);
		Canvas patternCanvas = new Canvas(pattern);
		patternCanvas.drawColor(RED);
		Paint shaded = new Paint();
		shaded.setShader(new BitmapShader(pattern, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
		Bitmap target = bitmap(4, 4);
		Canvas canvas = new Canvas(target);
		canvas.drawRect(0, 0, 4, 4, shaded);
		assertEquals(RED, target.getPixel(1, 1));
		patternCanvas.drawColor(BLUE);
		canvas.drawRect(0, 0, 4, 4, shaded);
		assertEquals(BLUE, target.getPixel(1, 1));
	}

	public void testMatrixAndClipSurviveAPixelRead() {
		Bitmap bitmap = bitmap(8, 8);
		Canvas canvas = new Canvas(bitmap);
		canvas.translate(4, 0);
		canvas.clipRect(0, 0, 2, 8);
		assertEquals(0, bitmap.getPixel(0, 0));
		canvas.drawRect(0, 0, 8, 8, paint(RED));
		assertEquals(RED, bitmap.getPixel(4, 0));
		assertEquals(RED, bitmap.getPixel(5, 7));
		assertEquals(0, bitmap.getPixel(0, 0));
		assertEquals(0, bitmap.getPixel(6, 0));
	}

	public void testAPixelReadDuringALayerSeesTheBase() {
		Bitmap bitmap = bitmap(4, 4);
		Canvas canvas = new Canvas(bitmap);
		canvas.saveLayer(null, null);
		canvas.drawRect(0, 0, 4, 4, paint(RED));
		assertEquals(0, bitmap.getPixel(1, 1));
		canvas.restore();
		assertEquals(RED, bitmap.getPixel(1, 1));
	}

	public void testTwoCanvasesKeepTheirOwnMatrices() {
		Bitmap bitmap = bitmap(8, 2);
		Canvas shifted = new Canvas(bitmap);
		shifted.translate(4, 0);
		Canvas plain = new Canvas(bitmap);
		plain.drawRect(0, 0, 1, 1, paint(RED));
		shifted.drawRect(0, 0, 1, 1, paint(BLUE));
		assertEquals(RED, bitmap.getPixel(0, 0));
		assertEquals(BLUE, bitmap.getPixel(4, 0));
		assertEquals(0, bitmap.getPixel(5, 0));
	}

	public void testABitmapDrawnIntoItself() {
		Bitmap bitmap = bitmap(4, 2);
		Canvas canvas = new Canvas(bitmap);
		canvas.drawRect(0, 0, 2, 2, paint(RED));
		canvas.drawBitmap(bitmap, 2, 0, null);
		assertEquals(RED, bitmap.getPixel(0, 0));
		assertEquals(RED, bitmap.getPixel(3, 1));
	}

	// a singular matrix must not break later draws
	public void testDrawsAfterAZeroScaleLand() {
		Bitmap bitmap = bitmap(4, 4);
		Canvas canvas = new Canvas(bitmap);
		canvas.save();
		canvas.scale(0, 0);
		canvas.drawRect(0, 0, 4, 4, paint(RED));
		canvas.clipRect(0, 0, 1, 1);
		canvas.restore();
		canvas.drawRect(0, 0, 1, 1, paint(BLUE));
		assertEquals(BLUE, bitmap.getPixel(0, 0));
		assertEquals(0, bitmap.getPixel(2, 2));
	}

	// Android 10's Skia (lowp): 8 bits round to 5 or 6 on a write, and their bits repeat on a read
	private static int level565(int value, int bits) {
		int q =bits == 6 ? (value * 21 + 42) / 85 : (value * 9 + 36) / 74;
		return (q << (8 - bits)) | (q >> (2 * bits - 8));
	}

	public void testRgb565() {
		Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.RGB_565);
		assertEquals(Bitmap.Config.RGB_565, bitmap.getConfig());
		assertEquals(0xff000000, bitmap.getPixel(0, 0)); // opaque black
		bitmap.setPixel(0, 0, 0x80ff0000); // a write to an opaque bitmap drops the alpha
		assertEquals(0xffff0000, bitmap.getPixel(0, 0));
		bitmap.setPixel(0, 0, 0xff3366cc);
		assertEquals(0xff3165ce, bitmap.getPixel(0, 0));
		bitmap.setPixel(0, 0, 0xff1c0000);
		assertEquals(0xff180000, bitmap.getPixel(0, 0));
		bitmap.setPixel(0, 0, 0xff1f0000); // rounds to level 4; truncating gives 3
		assertEquals(0xff210000, bitmap.getPixel(0, 0));
		ShortBuffer shorts = ShortBuffer.allocate(4);
		bitmap.copyPixelsToBuffer(shorts);
		assertEquals((short)0x2000, shorts.get(0));
		bitmap.eraseColor(0x80ff0000); // premultiplied, drawn over the opaque base
		assertEquals(0xff840000, bitmap.getPixel(1, 1));
	}

	public void testAnAntialiasedEdgeIn565HasOnly565Levels() {
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.RGB_565);
		Paint paint = paint(0xffffffff);
		paint.setAntiAlias(true);
		new Canvas(bitmap).drawCircle(8, 8, 5.3f, paint);
		boolean partial = false;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int pixel = bitmap.getPixel(x, y);
				int r = (pixel >> 16) & 0xff, g = (pixel >> 8) & 0xff, b = pixel & 0xff;
				assertEquals("alpha at " + x + "," + y, 0xff, pixel >>> 24);
				assertEquals("red at " + x + "," + y, level565(r, 5), r);
				assertEquals("green at " + x + "," + y, level565(g, 6), g);
				assertEquals("blue at " + x + "," + y, level565(b, 5), b);
				partial |= r != 0 && r != 0xff;
			}
		}
		assertTrue("an antialiased edge", partial);
	}

	public void testAlpha8() {
		Bitmap mask = Bitmap.createBitmap(2, 2, Bitmap.Config.ALPHA_8);
		assertEquals(Bitmap.Config.ALPHA_8, mask.getConfig());
		mask.setPixel(0, 0, 0x80123456);
		assertEquals(0x80000000, mask.getPixel(0, 0));
		mask.eraseColor(0xff000000);
		mask.setPixel(1, 1, 0);
		Bitmap target = bitmap(2, 2);
		new Canvas(target).drawBitmap(mask, 0, 0, paint(RED)); // a mask in the paint's color
		assertEquals(RED, target.getPixel(0, 0));
		assertEquals(0, target.getPixel(1, 1));
	}

	// Skia colors an ALPHA_8 bitmap with the paint's shader when it has one
	public void testAlpha8UnderAShader() {
		Bitmap mask = Bitmap.createBitmap(2, 1, Bitmap.Config.ALPHA_8);
		mask.eraseColor(0xff000000);
		mask.setPixel(1, 0, 0);
		Bitmap colors = bitmap(2, 1);
		colors.setPixels(new int[] {GREEN, BLUE}, 0, 2, 0, 0, 2, 1);
		Paint shaded = paint(RED);
		shaded.setShader(new BitmapShader(colors, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
		Bitmap target = bitmap(2, 1);
		new Canvas(target).drawBitmap(mask, 0, 0, shaded);
		assertEquals(GREEN, target.getPixel(0, 0));
		assertEquals(0, target.getPixel(1, 0));
	}

	// the filter colors the shader before the mask shapes it: SRC keeps the mask's shape
	public void testAlpha8FiltersBeforeTheMask() {
		Bitmap mask = Bitmap.createBitmap(2, 1, Bitmap.Config.ALPHA_8);
		mask.setPixel(0, 0, 0xff000000);
		Bitmap colors = bitmap(2, 1);
		colors.eraseColor(GREEN);
		Paint shaded = paint(RED);
		shaded.setShader(new BitmapShader(colors, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
		shaded.setColorFilter(new PorterDuffColorFilter(BLUE, PorterDuff.Mode.SRC));
		Bitmap target = bitmap(2, 1);
		new Canvas(target).drawBitmap(mask, 0, 0, shaded);
		assertEquals(BLUE, target.getPixel(0, 0));
		assertEquals(0, target.getPixel(1, 0));
	}

	// Android 10's checks: a recycled bitmap throws, its natives are never reached
	public void testARecycledBitmapThrows() throws Exception {
		Bitmap recycled = bitmap(2, 2);
		recycled.recycle();
		Bitmap target = bitmap(2, 2);
		Canvas canvas = new Canvas(target);
		try {
			new Canvas(recycled);
			fail("Canvas(Bitmap)");
		} catch (RuntimeException e) {
		}
		try {
			canvas.setBitmap(recycled);
			fail("setBitmap");
		} catch (RuntimeException e) {
		}
		try {
			canvas.drawBitmap(recycled, 0, 0, null);
			fail("drawBitmap");
		} catch (RuntimeException e) {
		}
		try {
			Paint shaded = new Paint();
			shaded.setShader(new BitmapShader(recycled, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
			canvas.drawRect(0, 0, 2, 2, shaded);
			fail("a BitmapShader");
		} catch (RuntimeException e) {
		}
		BitmapDrawable drawable = new BitmapDrawable(null, recycled);
		drawable.setBounds(0, 0, 2, 2);
		try {
			drawable.draw(canvas);
			fail("a BitmapDrawable");
		} catch (RuntimeException e) {
		}
		try {
			recycled.compress(Bitmap.CompressFormat.PNG, 100, new ByteArrayOutputStream());
			fail("compress");
		} catch (IllegalStateException e) {
		}
		try {
			recycled.copyPixelsToBuffer(ByteBuffer.allocate(16));
			fail("copyPixelsToBuffer");
		} catch (IllegalStateException e) {
		}
		canvas.drawRect(0, 0, 1, 1, paint(RED)); // still on target
		assertEquals(RED, target.getPixel(0, 0));
	}

	// Android 10 makes ARGB_4444 bitmaps as ARGB_8888; only copy() keeps 4444
	public void testArgb4444() {
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_4444);
		assertEquals(Bitmap.Config.ARGB_8888, bitmap.getConfig());
		assertEquals(4, bitmap.getRowBytes());
		bitmap.setPixel(0, 0, 0xff123456);
		Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_4444, true);
		assertEquals(Bitmap.Config.ARGB_4444, copy.getConfig());
		assertEquals(2, copy.getRowBytes());
		assertEquals(0xff113355, copy.getPixel(0, 0));
	}

	public void testExtractAlpha() {
		Bitmap bitmap = bitmap(2, 1);
		bitmap.setPixels(new int[] {0x80ff0000, 0xff00ff00}, 0, 2, 0, 0, 2, 1);
		Bitmap alpha = bitmap.extractAlpha();
		assertEquals(Bitmap.Config.ALPHA_8, alpha.getConfig());
		assertEquals(0x80000000, alpha.getPixel(0, 0));
		assertEquals(0xff000000, alpha.getPixel(1, 0));
	}

	public void testAnEmptyBitmapThrows() {
		try {
			Bitmap.createBitmap(0, 1, Bitmap.Config.ARGB_8888);
			fail("width 0");
		} catch (IllegalArgumentException e) {
		}
		try {
			Bitmap.createBitmap(1, -1, Bitmap.Config.ARGB_8888);
			fail("height -1");
		} catch (IllegalArgumentException e) {
		}
	}

	// CLEAR in a layer clears only the layer
	public void testClearInALayerKeepsTheBase() {
		Bitmap bitmap = bitmap(2, 2);
		bitmap.eraseColor(BLUE);
		Canvas canvas = new Canvas(bitmap);
		canvas.saveLayer(null, null);
		canvas.drawRect(0, 0, 2, 2, paint(RED));
		Paint clear = new Paint();
		clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		canvas.drawRect(0, 0, 1, 2, clear);
		canvas.restore();
		assertEquals(BLUE, bitmap.getPixel(0, 0));
		assertEquals(RED, bitmap.getPixel(1, 0));
	}
}
