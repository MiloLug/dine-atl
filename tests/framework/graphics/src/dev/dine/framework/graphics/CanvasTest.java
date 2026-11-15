package dev.dine.framework.graphics;

import android.atl.ATLCanvas;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;
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

	// AOSP: the size of what the canvas draws into: a view's, a bitmap's, a Picture's
	public void testGetWidthAndHeight() {
		ATLCanvas view = new ATLCanvas(0, 100, 50);
		assertEquals(100, view.getWidth());
		assertEquals(50, view.getHeight());
		view.atlReset(30, 20);
		assertEquals(30, view.getWidth());
		assertEquals(20, view.getHeight());
		Canvas canvas = new Canvas(Bitmap.createBitmap(7, 9, Bitmap.Config.ARGB_8888));
		assertEquals(7, canvas.getWidth());
		assertEquals(9, canvas.getHeight());
		canvas.setBitmap(Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888));
		assertEquals(3, canvas.getWidth());
		assertEquals(4, canvas.getHeight());
		Canvas recording = new Picture().beginRecording(11, 12);
		assertEquals(11, recording.getWidth());
		assertEquals(12, recording.getHeight());
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

	// setBitmap starts over at the new bitmap's size: no saves, no clip, no matrix
	public void testSetBitmapResetsTheState() {
		Canvas canvas = new Canvas(Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888));
		assertEquals(new Rect(0, 0, 10, 6), canvas.getClipBounds());
		canvas.save();
		canvas.translate(3, 2);
		canvas.clipRect(1, 1, 4, 4);
		canvas.save();
		assertEquals(3, canvas.getSaveCount());
		canvas.setBitmap(Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888));
		assertEquals(1, canvas.getSaveCount());
		assertTrue(canvas.getMatrix().isIdentity());
		assertEquals(new Rect(0, 0, 20, 12), canvas.getClipBounds());
		canvas.setBitmap(null);
		assertFalse(canvas.getClipBounds(new Rect()));
	}

	// a DIFFERENCE only empties the bounds when its shape is exactly a rect in device space that covers them
	public void testDifferenceEmptiesTheBoundsOnlyForACoveringRect() {
		ATLCanvas covered = new ATLCanvas(0, 100, 50);
		assertFalse(covered.clipRect(-10, -10, 110, 60, Region.Op.DIFFERENCE));
		assertTrue(covered.getClipBounds().isEmpty());
		ATLCanvas circle = new ATLCanvas(0, 100, 50);
		Path round = new Path();
		round.addCircle(50, 25, 200, Path.Direction.CW); // covers the bounds, but isn't a rect
		assertTrue(circle.clipPath(round, Region.Op.DIFFERENCE));
		assertEquals(new Rect(0, 0, 100, 50), circle.getClipBounds());
		ATLCanvas turned = new ATLCanvas(0, 100, 50);
		turned.rotate(45);
		assertTrue(turned.clipRect(-500, -500, 500, 500, Region.Op.DIFFERENCE)); // a rect, but not under this matrix
		assertFalse(turned.getClipBounds().isEmpty());
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

	// SkRRect sorts its rect
	public void testAnInvertedRoundRectDraws() {
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		Paint fill = new Paint();
		fill.setColor(0xff00ff00);
		new Canvas(bitmap).drawRoundRect(14, 14, 2, 2, 4, 4, fill);
		assertEquals(0xff00ff00, bitmap.getPixel(8, 8));
		assertEquals("the corner is cut", 0, bitmap.getPixel(2, 2));
	}

	// SkCanvas::drawCircle draws nothing for a radius of 0 or less
	public void testACircleOfNoRadiusDrawsNothing() {
		Paint fill = new Paint();
		fill.setColor(0xff00ff00);
		for (float radius : new float[] {-4, 0}) {
			Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
			new Canvas(bitmap).drawCircle(8, 8, radius, fill);
			assertEquals("radius " + radius, 0, bitmap.getPixel(8, 8));
			assertEquals("radius " + radius, 0, bitmap.getPixel(5, 5));
		}
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		new Canvas(bitmap).drawCircle(8, 8, 4, fill);
		assertEquals(0xff00ff00, bitmap.getPixel(8, 8));
	}

	// SkCanvas::drawOval sorts its rect, as SkRRect does
	public void testAnInvertedOvalDrawsTheOval() {
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		Paint fill = new Paint();
		fill.setColor(0xff00ff00);
		new Canvas(bitmap).drawOval(14, 14, 2, 2, fill);
		assertEquals(0xff00ff00, bitmap.getPixel(8, 8));
		assertEquals("the corner is cut", 0, bitmap.getPixel(2, 2));
	}

	public void testAnInvertedOvalRectFDrawsTheOval() {
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		Paint fill = new Paint();
		fill.setColor(0xff00ff00);
		new Canvas(bitmap).drawOval(new RectF(14, 14, 2, 2), fill);
		assertEquals(0xff00ff00, bitmap.getPixel(8, 8));
		assertEquals("the corner is cut", 0, bitmap.getPixel(2, 2));
	}

	public void testDrawARGBFillsTheClip() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.clipRect(0, 0, 2, 4);
		canvas.drawARGB(255, 10, 20, 30);
		assertEquals(0xff0a141e, bitmap.getPixel(1, 2));
		assertEquals(0, bitmap.getPixel(3, 2));
	}

	private static Paint pointPaint(float width, Paint.Cap cap) {
		Paint paint = new Paint();
		paint.setColor(0xffff0000);
		paint.setStrokeWidth(width);
		paint.setStrokeCap(cap);
		return paint;
	}

	// Skia's point: a hairline is one device pixel, else a square of the stroke width, or a circle with a round cap
	public void testAHairlinePointIsOneDevicePixel() {
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.drawPoint(5.5f, 6.5f, pointPaint(0, Paint.Cap.BUTT));
		assertEquals(0xffff0000, bitmap.getPixel(5, 6));
		for (int[] at : new int[][] {{4, 6}, {6, 6}, {5, 5}, {5, 7}})
			assertEquals(at[0] + "," + at[1], 0, bitmap.getPixel(at[0], at[1]));
		Bitmap scaled = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		canvas = new Canvas(scaled);
		canvas.scale(3, 3);
		canvas.drawPoint(2, 2, pointPaint(0, Paint.Cap.BUTT)); // device 6, 6
		assertEquals(0xffff0000, scaled.getPixel(6, 6));
		for (int[] at : new int[][] {{5, 6}, {7, 6}, {6, 5}, {6, 7}})
			assertEquals(at[0] + "," + at[1], 0, scaled.getPixel(at[0], at[1]));
	}

	public void testAWidePointIsASquareOrACircle() {
		for (Paint.Cap cap : new Paint.Cap[] {Paint.Cap.BUTT, Paint.Cap.SQUARE}) {
			Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
			new Canvas(bitmap).drawPoint(8, 8, pointPaint(4, cap));
			for (int[] at : new int[][] {{6, 6}, {9, 9}, {6, 9}, {9, 6}})
				assertEquals(cap + " " + at[0] + "," + at[1], 0xffff0000, bitmap.getPixel(at[0], at[1]));
			for (int[] at : new int[][] {{5, 8}, {10, 8}, {8, 5}, {8, 10}})
				assertEquals(cap + " " + at[0] + "," + at[1], 0, bitmap.getPixel(at[0], at[1]));
		}
		Bitmap round = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		new Canvas(round).drawPoint(8, 8, pointPaint(8, Paint.Cap.ROUND));
		assertEquals(0xffff0000, round.getPixel(8, 8));
		assertEquals(0xffff0000, round.getPixel(6, 6));
		assertEquals("a circle has no corner", 0, round.getPixel(4, 4));
	}

	public void testDrawPointsTakesAnOffsetAndACountOfValues() {
		float[] pts = {2.5f, 2.5f, 5.5f, 5.5f, 9.5f, 9.5f, 12.5f, 12.5f};
		Paint paint = pointPaint(0, Paint.Cap.BUTT);
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		new Canvas(bitmap).drawPoints(pts, 2, 4, paint);
		assertEquals(0, bitmap.getPixel(2, 2));
		assertEquals(0xffff0000, bitmap.getPixel(5, 5));
		assertEquals(0xffff0000, bitmap.getPixel(9, 9));
		assertEquals(0, bitmap.getPixel(12, 12));
		Bitmap all = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		new Canvas(all).drawPoints(pts, paint);
		assertEquals(0xffff0000, all.getPixel(2, 2));
		assertEquals(0xffff0000, all.getPixel(12, 12));
		try {
			new Canvas(all).drawPoints(pts, 4, 8, paint);
			fail("past the array");
		} catch (ArrayIndexOutOfBoundsException expected) {
		}
	}

	public void testDrawLinesChecksItsRange() {
		Canvas canvas = new Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888));
		Paint paint = new Paint();
		float[] points = new float[8];
		canvas.drawLines(points, 0, 8, paint);
		canvas.drawLines(points, 4, 4, paint);
		canvas.drawLines(points, 8, 0, paint);
		int[][] bad = {{-4, 8}, {0, -4}, {-4, -4}, {4, 8}, {0, 12}, {Integer.MAX_VALUE, 8}};
		for (int[] range : bad) {
			try {
				canvas.drawLines(points, range[0], range[1], paint);
				fail("offset " + range[0] + ", count " + range[1]);
			} catch (ArrayIndexOutOfBoundsException expected) {
			}
		}
		Canvas recording = new Picture().beginRecording(16, 16);
		try {
			recording.drawLines(points, -4, 8, paint);
			fail("a recording's range");
		} catch (ArrayIndexOutOfBoundsException expected) {
		}
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
