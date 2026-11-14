package dev.dine.framework.graphics;

import android.atl.ATLCanvas;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import junit.framework.TestCase;

public class CanvasTest extends TestCase {
	// AdaptiveIconDrawable makes one: it draws nowhere but keeps its state
	public void testCanvasWithoutBitmap() {
		Canvas canvas = new Canvas();
		assertEquals(1, canvas.save());
		canvas.scale(2, 3);
		canvas.translate(2, 3);
		canvas.drawRect(0, 0, 4, 4, new Paint());
		canvas.drawText("x", 0, 0, new Paint());
		Matrix expected = new Matrix();
		expected.setScale(2, 3);
		expected.preTranslate(2, 3);
		assertEquals(expected, canvas.getMatrix());
		assertEquals(2, canvas.getSaveCount());
		canvas.restore();
		assertEquals(1, canvas.getSaveCount());
	}

	// Android's clipRect: whether any clip is left
	public void testClipRectReturnsWhetherTheClipIsEmpty() {
		ATLCanvas canvas = new ATLCanvas(0, 100, 50); // a view's draw canvas
		assertEquals(new Rect(0, 0, 100, 50), canvas.getClipBounds());
		assertTrue(canvas.clipRect(10, 10, 60, 40));
		assertEquals(new Rect(10, 10, 60, 40), canvas.getClipBounds());
		canvas.save();
		canvas.translate(50, 0);
		assertEquals(new Rect(-40, 10, 10, 40), canvas.getClipBounds());
		assertFalse(canvas.clipRect(20, 0, 30, 50));
		assertTrue(canvas.getClipBounds().isEmpty());
		canvas.restore();
		assertEquals(new Rect(10, 10, 60, 40), canvas.getClipBounds());
		assertFalse(canvas.clipRect(0, 0, 100, 50, Region.Op.DIFFERENCE));
	}

	public void testCanvasWithoutBitmapClipsEverything() {
		Canvas canvas = new Canvas();
		assertFalse(canvas.clipRect(0, 0, 10, 10));
		assertFalse(canvas.getClipBounds(new Rect()));
	}

	// a DIFFERENCE by a shape that isn't exactly a rect leaves the bounds
	public void testDifferenceKeepsTheBounds() {
		ATLCanvas canvas = new ATLCanvas(0, 100, 50);
		Path circle = new Path();
		circle.addCircle(200, 200, 10, Path.Direction.CW);
		assertTrue(canvas.clipPath(circle, Region.Op.DIFFERENCE));
		assertEquals(new Rect(0, 0, 100, 50), canvas.getClipBounds());
	}

	private static int strokedArcPixel(float sweep, int x, int y) {
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		Paint stroke = new Paint();
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeWidth(2);
		stroke.setColor(0xff00ff00);
		new Canvas(bitmap).drawArc(2, 2, 14, 14, 0, sweep, true, stroke);
		return bitmap.getPixel(x, y);
	}

	// a sweep of 360 degrees or more is the whole oval: no line to the center, as SkCanvas::drawArc
	public void testFullSweepArcIsTheOval() {
		assertTrue("a wedge strokes its radius", strokedArcPixel(350, 11, 8) != 0);
		for (float sweep : new float[] {360, -360, 400}) {
			assertEquals("radius, sweep " + sweep, 0, strokedArcPixel(sweep, 11, 8));
			assertTrue("rim, sweep " + sweep, strokedArcPixel(sweep, 13, 8) != 0);
		}
	}
}
