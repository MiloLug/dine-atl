package dev.dine.framework.graphics;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import junit.framework.TestCase;

public class PathTest extends TestCase {
	private static final float EPSILON = 1e-3f;

	private static void assertBounds(Path path, float left, float top, float right, float bottom) {
		RectF bounds = new RectF();
		path.computeBounds(bounds, true);
		assertEquals("left", left, bounds.left, EPSILON);
		assertEquals("top", top, bounds.top, EPSILON);
		assertEquals("right", right, bounds.right, EPSILON);
		assertEquals("bottom", bottom, bounds.bottom, EPSILON);
	}

	// Path has no public contains: op() with a point-sized path tests one point under the fill type
	private static boolean contains(Path path, float x, float y) {
		Path point = new Path();
		point.addRect(x - 0.1f, y - 0.1f, x + 0.1f, y + 0.1f, Path.Direction.CW);
		Path copy = new Path(path);
		copy.op(point, Path.Op.INTERSECT);
		return !copy.isEmpty();
	}

	private static Path nestedRects(Path.Direction inner) {
		Path path = new Path();
		path.addRect(0, 0, 30, 30, Path.Direction.CW);
		path.addRect(10, 10, 20, 20, inner);
		return path;
	}

	public void testNewPathIsEmpty() {
		Path path = new Path();
		assertTrue(path.isEmpty());
		assertBounds(path, 0, 0, 0, 0);
		path.moveTo(1, 1);
		assertFalse(path.isEmpty());
	}

	public void testRect() {
		Path path = new Path();
		path.addRect(10, 10, 30, 20, Path.Direction.CCW);
		assertBounds(path, 10, 10, 30, 20);
		RectF rect = new RectF();
		assertTrue(path.isRect(rect));
		assertEquals(new RectF(10, 10, 30, 20), rect);
	}

	public void testOvalCircleAndRoundRectStayInTheirRect() {
		Path oval = new Path();
		oval.addOval(0, 0, 40, 20, Path.Direction.CW);
		assertBounds(oval, 0, 0, 40, 20);
		assertFalse(oval.isRect(null));
		Path circle = new Path();
		circle.addCircle(10, 10, 5, Path.Direction.CCW);
		assertBounds(circle, 5, 5, 15, 15);
		Path round = new Path();
		round.addRoundRect(new RectF(0, 0, 40, 20), 30, 30, Path.Direction.CW); // radii shrink to fit
		assertBounds(round, 0, 0, 40, 20);
	}

	public void testQuarterArc() {
		Path path = new Path();
		path.addArc(0, 0, 20, 20, 0, 90); // from the right to the bottom, clockwise
		assertBounds(path, 10, 10, 20, 20);
	}

	public void testArcToJoinsWithALine() {
		Path path = new Path();
		path.moveTo(0, 0);
		path.arcTo(new RectF(10, 0, 30, 20), 180, 90);
		assertBounds(path, 0, 0, 20, 10);
	}

	public void testWindingAndEvenOdd() {
		Path same = nestedRects(Path.Direction.CW);
		assertTrue(contains(same, 15, 15));
		assertTrue(contains(same, 5, 5));
		same.setFillType(Path.FillType.EVEN_ODD);
		assertFalse(contains(same, 15, 15));
		assertTrue(contains(same, 5, 5));
		Path opposite = nestedRects(Path.Direction.CCW);
		assertFalse(contains(opposite, 15, 15));
		assertFalse(contains(opposite, 40, 40));
	}

	public void testCurvesContain() {
		Path circle = new Path();
		circle.addCircle(50, 50, 10, Path.Direction.CW);
		assertTrue(contains(circle, 50, 59));
		assertFalse(contains(circle, 58, 58)); // inside the box, outside the circle
	}

	public void testInverseFillTypes() {
		Path path = new Path();
		path.setFillType(Path.FillType.EVEN_ODD);
		path.toggleInverseFillType();
		assertEquals(Path.FillType.INVERSE_EVEN_ODD, path.getFillType());
		assertTrue(path.isInverseFillType());
		path.reset(); // keeps the fill type, as on Android
		assertEquals(Path.FillType.INVERSE_EVEN_ODD, path.getFillType());
	}

	public void testTransformAndOffset() {
		Path path = new Path();
		path.addRect(0, 0, 10, 10, Path.Direction.CW);
		Matrix matrix = new Matrix();
		matrix.setScale(2, 3);
		path.transform(matrix);
		assertBounds(path, 0, 0, 20, 30);
		Path moved = new Path();
		path.offset(5, 5, moved);
		assertBounds(moved, 5, 5, 25, 35);
		assertBounds(path, 0, 0, 20, 30);
	}

	public void testCopyIsIndependent() {
		Path a = new Path();
		a.addRect(0, 0, 10, 10, Path.Direction.CW);
		Path b = new Path(a);
		b.offset(100, 0);
		b.lineTo(200, 0);
		assertBounds(a, 0, 0, 10, 10);
		assertBounds(b, 100, 0, 200, 10);
	}

	public void testAddPathToItself() {
		Path path = new Path();
		path.addRect(0, 0, 10, 10, Path.Direction.CW);
		path.addPath(path, 10, 0);
		assertBounds(path, 0, 0, 20, 10);
	}

	public void testRelativeAfterCloseStartsAtTheContour() {
		Path path = new Path();
		path.moveTo(10, 10);
		path.lineTo(20, 10);
		path.lineTo(20, 20);
		path.close();
		path.rLineTo(0, -5);
		assertBounds(path, 10, 5, 20, 20);
	}

	public void testRelativeAndLastPoint() {
		Path path = new Path();
		path.moveTo(5, 5);
		path.rLineTo(10, 0);
		assertBounds(path, 5, 5, 15, 5);
		path.setLastPoint(20, 8);
		assertBounds(path, 5, 5, 20, 8);
	}
}
