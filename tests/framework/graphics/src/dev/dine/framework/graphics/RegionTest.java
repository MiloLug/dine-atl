package dev.dine.framework.graphics;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import junit.framework.TestCase;

public class RegionTest extends TestCase {
	// rows of a 10x10 grid from (0, 0): '#' where the region contains the pixel
	private static void assertPixels(Region region, String... rows) {
		for (int y = 0; y < rows.length; y++)
			for (int x = 0; x < rows[y].length(); x++)
				assertEquals("(" + x + ", " + y + ")", rows[y].charAt(x) == '#', region.contains(x, y));
	}

	private static Region overlapped(Region.Op op, boolean expected) {
		Region region = new Region(0, 0, 4, 4);
		assertEquals(expected, region.op(new Rect(2, 2, 6, 6), op));
		return region;
	}

	public void testEmpty() {
		Region region = new Region();
		assertTrue(region.isEmpty());
		assertFalse(region.isRect());
		assertFalse(region.isComplex());
		Rect bounds = new Rect(1, 2, 3, 4);
		assertFalse(region.getBounds(bounds));
		assertEquals(new Rect(), bounds);
		assertFalse(region.set(5, 5, 5, 9));
		assertTrue(region.isEmpty());
	}

	public void testRect() {
		Region region = new Region(new Rect(1, 1, 3, 4));
		assertTrue(region.isRect());
		assertFalse(region.isComplex());
		assertEquals(new Rect(1, 1, 3, 4), region.getBounds());
		assertPixels(region,
		             "....",
		             ".##.",
		             ".##.",
		             ".##.",
		             "....");
		assertEquals(region, new Region(region));
	}

	public void testOps() {
		assertPixels(overlapped(Region.Op.UNION, true),
		             "####..",
		             "####..",
		             "######",
		             "######",
		             "..####",
		             "..####");
		assertPixels(overlapped(Region.Op.INTERSECT, true),
		             "......",
		             "......",
		             "..##..",
		             "..##..",
		             "......",
		             "......");
		assertPixels(overlapped(Region.Op.DIFFERENCE, true),
		             "####..",
		             "####..",
		             "##....",
		             "##....",
		             "......",
		             "......");
		assertPixels(overlapped(Region.Op.REVERSE_DIFFERENCE, true),
		             "......",
		             "......",
		             "....##",
		             "....##",
		             "..####",
		             "..####");
		assertPixels(overlapped(Region.Op.XOR, true),
		             "####..",
		             "####..",
		             "##..##",
		             "##..##",
		             "..####",
		             "..####");
		assertEquals(new Region(2, 2, 6, 6), overlapped(Region.Op.REPLACE, true));
		assertTrue(overlapped(Region.Op.UNION, true).isComplex());
		assertFalse(new Region(0, 0, 4, 4).op(new Rect(5, 5, 6, 6), Region.Op.INTERSECT));
	}

	public void testOpOfTwoRegionsIntoAThird() {
		Region region = new Region(9, 9, 10, 10);
		assertTrue(region.op(new Region(0, 0, 4, 4), new Region(2, 0, 6, 4), Region.Op.INTERSECT));
		assertEquals(new Region(2, 0, 4, 4), region);
		assertTrue(region.op(new Rect(0, 0, 2, 4), new Region(2, 0, 6, 4), Region.Op.UNION));
		assertEquals(new Region(0, 0, 6, 4), region);
	}

	public void testTouchingRectsMergeIntoOne() {
		Region side = new Region(0, 0, 5, 5);
		side.union(new Rect(5, 0, 10, 5));
		assertTrue(side.isRect());
		assertEquals(new Region(0, 0, 10, 5), side);

		Region below = new Region(0, 0, 5, 5);
		below.union(new Rect(0, 5, 5, 10));
		assertTrue(below.isRect());
		assertEquals(new Region(0, 0, 5, 10), below);

		Region split = new Region(0, 0, 10, 10);
		split.op(new Rect(4, 0, 6, 10), Region.Op.DIFFERENCE);
		split.op(new Rect(4, 0, 6, 10), Region.Op.UNION);
		assertTrue(split.isRect());
	}

	public void testEqualsComparesArea() {
		Region a = new Region(0, 0, 4, 2);
		a.union(new Rect(0, 2, 2, 4));
		Region b = new Region(0, 0, 2, 4);
		b.union(new Rect(2, 0, 4, 2));
		assertEquals(a, b);
		assertFalse(a.equals(new Region(0, 0, 4, 4)));
	}

	public void testQuickChecks() {
		Region region = new Region(0, 0, 4, 4);
		assertTrue(region.quickContains(1, 1, 3, 3));
		assertFalse(region.quickContains(1, 1, 5, 3));
		assertTrue(region.quickReject(4, 0, 6, 4));
		assertFalse(region.quickReject(3, 3, 6, 6));
		assertTrue(region.quickReject(new Region(5, 5, 6, 6)));
		assertFalse(region.quickReject(new Region(3, 0, 6, 1)));
		assertTrue(new Region().quickReject(0, 0, 1, 1));
	}

	public void testTranslateAndScale() {
		Region region = new Region(0, 0, 2, 2);
		region.union(new Rect(4, 0, 5, 1));
		Region moved = new Region();
		region.translate(1, 2, moved);
		assertEquals(new Rect(0, 0, 5, 2), region.getBounds());
		assertPixels(moved,
		             "......",
		             "......",
		             ".##..#",
		             ".##...");
		region.translate(-1, 0);
		assertEquals(new Rect(-1, 0, 4, 2), region.getBounds());
		Region scaled = new Region();
		new Region(1, 1, 2, 3).scale(2, scaled);
		assertEquals(new Region(2, 2, 4, 6), scaled);
	}

	public void testBoundaryPathCoversTheRegion() {
		Region region = new Region(0, 0, 2, 2);
		region.union(new Rect(3, 3, 5, 5));
		Path path = region.getBoundaryPath();
		RectF bounds = new RectF();
		path.computeBounds(bounds, true);
		assertEquals(new RectF(0, 0, 5, 5), bounds);
		assertFalse(new Region().getBoundaryPath(new Path()));
	}

	public void testSetPathOfARect() {
		Path path = new Path();
		path.addRect(1, 1, 4, 3, Path.Direction.CW);
		Region region = new Region();
		assertTrue(region.setPath(path, new Region(0, 0, 10, 10)));
		assertEquals(new Region(1, 1, 4, 3), region);
		assertTrue(region.setPath(path, new Region(2, 0, 10, 10)));
		assertEquals(new Region(2, 1, 4, 3), region);
		assertFalse(region.setPath(path, new Region(5, 5, 10, 10)));
		assertTrue(region.isEmpty());
	}

	// pixels whose centre is inside the circle, as Skia's non-antialiased fill
	public void testSetPathOfACircle() {
		Path path = new Path();
		path.addCircle(5, 5, 5, Path.Direction.CW);
		Region region = new Region();
		assertTrue(region.setPath(path, new Region(0, 0, 10, 10)));
		assertTrue(region.isComplex());
		assertEquals(new Rect(0, 0, 10, 10), region.getBounds());
		assertTrue(region.contains(5, 5));
		assertTrue(region.contains(5, 0));
		assertTrue(region.contains(0, 4));
		assertTrue(region.contains(9, 5));
		assertFalse(region.contains(0, 0));
		assertFalse(region.contains(9, 9));
		assertFalse(region.contains(1, 0));
	}

	public void testSetPathEvenOddAndInverse() {
		Path ring = new Path();
		ring.addRect(0, 0, 6, 6, Path.Direction.CW);
		ring.addRect(2, 2, 4, 4, Path.Direction.CW);
		ring.setFillType(Path.FillType.EVEN_ODD);
		Region region = new Region();
		region.setPath(ring, new Region(0, 0, 10, 10));
		assertPixels(region,
		             "######",
		             "######",
		             "##..##",
		             "##..##",
		             "######",
		             "######");

		Path hole = new Path();
		hole.addRect(1, 1, 3, 3, Path.Direction.CW);
		hole.setFillType(Path.FillType.INVERSE_WINDING);
		assertTrue(region.setPath(hole, new Region(0, 0, 4, 4)));
		assertPixels(region,
		             "####.",
		             "#..#.",
		             "#..#.",
		             "####.",
		             ".....");
	}

	public void testPathOpGoesThroughRegions() {
		Path a = new Path();
		a.addRect(0, 0, 4, 4, Path.Direction.CW);
		Path b = new Path();
		b.addRect(2, 2, 6, 6, Path.Direction.CW);
		Path result = new Path();
		assertTrue(result.op(a, b, Path.Op.INTERSECT));
		RectF bounds = new RectF();
		result.computeBounds(bounds, true);
		assertEquals(new RectF(2, 2, 4, 4), bounds);
		assertTrue(result.op(a, b, Path.Op.UNION));
		result.computeBounds(bounds, true);
		assertEquals(new RectF(0, 0, 6, 6), bounds);
	}

	public void testOpsOfComplexRegions() {
		Region bars = new Region(0, 0, 1, 4);
		bars.union(new Rect(2, 0, 3, 4));
		bars.union(new Rect(4, 0, 5, 4));
		Region cross = new Region(0, 1, 6, 3);
		bars.op(bars, cross, Region.Op.XOR);
		assertPixels(bars,
		             "#.#.#.",
		             ".#.#.#",
		             ".#.#.#",
		             "#.#.#.");
	}

	// a pixel is in when its centre is past the start edge and not past the end one
	public void testSetPathHalfPixelEdges() {
		Path path = new Path();
		path.addRect(0.5f, 0.5f, 2.5f, 2.5f, Path.Direction.CW);
		Region region = new Region();
		region.setPath(path, new Region(0, 0, 4, 4));
		assertEquals(new Region(1, 1, 3, 3), region);
	}

	// Path.op passes no clip: the path's bounds then, and nothing for an inverse fill
	public void testSetPathWithoutClip() {
		Path path = new Path();
		path.addRect(1, 1, 3, 3, Path.Direction.CW);
		Region region = new Region();
		assertTrue(region.setPath(path, null));
		assertEquals(new Region(1, 1, 3, 3), region);
		path.setFillType(Path.FillType.INVERSE_WINDING);
		assertFalse(region.setPath(path, null));
	}

	public void testScaleToNothingAndText() {
		Region region = new Region(1, 1, 3, 3);
		region.union(new Rect(4, 1, 5, 2));
		assertEquals("Region([1,1,3,2] [4,1,5,2] [1,2,3,3])", region.toString());
		region.scale(0);
		assertTrue(region.isEmpty());
	}

	public void testIteratorWalksTheRects() {
		Region region = new Region(0, 0, 10, 10);
		region.op(20, 0, 30, 10, Region.Op.UNION);
		RegionIterator iterator = new RegionIterator(region);
		region.setEmpty(); // the iterator keeps what it saw
		Rect r = new Rect();
		assertTrue(iterator.next(r));
		assertEquals(new Rect(0, 0, 10, 10), r);
		assertTrue(iterator.next(r));
		assertEquals(new Rect(20, 0, 30, 10), r);
		assertFalse(iterator.next(r));
	}
}
