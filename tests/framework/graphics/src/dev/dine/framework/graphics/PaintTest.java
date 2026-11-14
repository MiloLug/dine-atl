package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Xfermode;
import junit.framework.TestCase;

public class PaintTest extends TestCase {
	public void testStateRoundTrips() {
		Paint paint = new Paint();
		paint.setColor(0x80123456);
		paint.setAlpha(0x40);
		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setStrokeWidth(3.5f);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.BEVEL);
		paint.setTextSize(21);
		assertEquals(0x40, paint.getAlpha());
		assertEquals(Paint.Style.FILL_AND_STROKE, paint.getStyle());
		assertEquals(3.5f, paint.getStrokeWidth());
		assertEquals(Paint.Cap.ROUND, paint.getStrokeCap());
		assertEquals(Paint.Join.BEVEL, paint.getStrokeJoin());
		assertEquals(21f, paint.getTextSize());
	}

	public void testCopyIsIndependent() {
		Paint paint = new Paint();
		paint.setStrokeWidth(2);
		Paint copy = new Paint(paint);
		paint.setStrokeWidth(5);
		assertEquals(2f, copy.getStrokeWidth());
	}

	public void testSetSelfKeepsState() {
		Paint paint = new Paint();
		paint.setColor(0xff123456);
		paint.setStrokeWidth(3);
		paint.set(paint);
		assertEquals(0xff123456, paint.getColor());
		assertEquals(3f, paint.getStrokeWidth());
	}

	public void testCopyKeepsJavaState() {
		Paint paint = new Paint();
		Xfermode xfermode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
		Shader shader = new LinearGradient(0, 0, 1, 1, 0xff000000, 0xffffffff, Shader.TileMode.CLAMP);
		ColorFilter filter = new PorterDuffColorFilter(0xff00ff00, PorterDuff.Mode.SRC_IN);
		paint.setXfermode(xfermode);
		paint.setShader(shader);
		paint.setTextAlign(Paint.Align.RIGHT);
		paint.setColorFilter(filter);
		Paint set = new Paint();
		set.set(paint);
		for (Paint copy : new Paint[] {new Paint(paint), set}) {
			assertSame(xfermode, copy.getXfermode());
			assertSame(shader, copy.getShader());
			assertEquals(Paint.Align.RIGHT, copy.getTextAlign());
			assertSame(filter, copy.getColorFilter());
		}
	}

	public void testTextAlignStartsLeft() {
		Paint paint = new Paint();
		assertEquals(Paint.Align.LEFT, paint.getTextAlign());
		paint.setTextAlign(Paint.Align.RIGHT);
		paint.reset();
		assertEquals(Paint.Align.LEFT, paint.getTextAlign());
	}

	// 0 is Android's hairline, and what a new Paint holds
	public void testStrokeWidthStartsAtTheHairline() {
		Paint paint = new Paint();
		assertEquals(0f, paint.getStrokeWidth());
		paint.setStrokeWidth(3);
		paint.reset();
		assertEquals(0f, paint.getStrokeWidth());
	}

	private static Bitmap stroked(Paint.Cap cap, Paint.Join join, Path path) {
		Bitmap bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(4);
		paint.setStrokeCap(cap);
		paint.setStrokeJoin(join);
		paint.setColor(0xffff0000);
		new Canvas(bitmap).drawPath(path, paint);
		return bitmap;
	}

	// edges are antialiased on every build: covered is most of a pixel, clear none of it
	private static int alpha(Bitmap bitmap, int x, int y) {
		return bitmap.getPixel(x, y) >>> 24;
	}

	public void testStrokeCapsDraw() {
		Path line = new Path();
		line.moveTo(4, 8);
		line.lineTo(12, 8);
		Bitmap butt = stroked(Paint.Cap.BUTT, Paint.Join.MITER, line);
		assertEquals(0, alpha(butt, 2, 8));
		assertEquals(0xff, alpha(butt, 5, 8));
		Bitmap square = stroked(Paint.Cap.SQUARE, Paint.Join.MITER, line);
		assertTrue(alpha(square, 2, 8) > 0xc0);
		assertTrue(alpha(square, 2, 6) > 0xc0);
		Bitmap round = stroked(Paint.Cap.ROUND, Paint.Join.MITER, line);
		assertTrue(alpha(round, 2, 8) > 0xc0);
		assertTrue(alpha(round, 2, 6) < 0x80); // the square's corner, mostly outside the half circle
	}

	public void testStrokeJoinsDraw() {
		Path corner = new Path();
		corner.moveTo(4, 4);
		corner.lineTo(12, 4);
		corner.lineTo(12, 12);
		int miter = alpha(stroked(Paint.Cap.BUTT, Paint.Join.MITER, corner), 13, 2);
		int round = alpha(stroked(Paint.Cap.BUTT, Paint.Join.ROUND, corner), 13, 2);
		int bevel = alpha(stroked(Paint.Cap.BUTT, Paint.Join.BEVEL, corner), 13, 2);
		assertTrue(miter + " " + round + " " + bevel, miter > 0xc0 && miter > round + 0x40 && round > bevel + 0x20);
	}

	// Skia's miter limit is 4: a join sharper than about 29 degrees is beveled
	public void testASharpMiterIsBeveled() {
		Path corner = new Path();
		corner.moveTo(8, 60);
		corner.lineTo(16, 10);
		corner.lineTo(24, 60);
		Bitmap bitmap = stroked(Paint.Cap.BUTT, Paint.Join.MITER, corner);
		assertEquals(0xffff0000, bitmap.getPixel(16, 10));
		assertEquals(0, bitmap.getPixel(16, 3));
	}

	// SkPaint's stroke-and-fill covers the union once: a translucent paint doesn't blend twice under the stroke
	public void testFillAndStrokeBlendsOnce() {
		Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setStrokeWidth(8);
		paint.setColor(0x80ff0000);
		new Canvas(bitmap).drawRect(8, 8, 24, 24, paint);
		assertEquals(Integer.toHexString(bitmap.getPixel(16, 16)), Integer.toHexString(bitmap.getPixel(10, 10)));
		assertEquals(Integer.toHexString(bitmap.getPixel(16, 16)), Integer.toHexString(bitmap.getPixel(5, 16)));
		assertEquals(0, bitmap.getPixel(2, 16));
	}
}
