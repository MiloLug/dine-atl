package dev.dine.framework.graphics;

import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
}
