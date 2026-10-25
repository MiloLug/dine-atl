package dev.dine.framework.graphics;

import android.graphics.Paint;
import android.graphics.Rect;
import junit.framework.TestCase;

/* getTextBounds is the glyphs' ink, not the line's box. Headless fails it: its fixed metrics
 * have no ink. */
public class InkBoundsTest extends TestCase {
	public void testBoundsAreInk() {
		Paint paint = new Paint();
		paint.setTextSize(30);
		Rect bounds = new Rect();
		paint.getTextBounds(".", 0, 1, bounds);
		assertTrue(bounds.width() < paint.measureText("."));
		assertTrue(bounds.top > paint.ascent());
	}
}
