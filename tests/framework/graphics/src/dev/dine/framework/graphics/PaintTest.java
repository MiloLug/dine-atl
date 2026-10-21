package dev.dine.framework.graphics;

import android.graphics.Paint;
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
}
