package dev.dine.framework.basics;

import android.graphics.Insets;
import android.graphics.Rect;
import junit.framework.TestCase;

public class RectTest extends TestCase {
	public void testIntersect() {
		Rect a = new Rect(0, 0, 10, 10);
		assertTrue(a.intersect(new Rect(5, 5, 20, 20)));
		assertEquals(new Rect(5, 5, 10, 10), a);
	}

	public void testInsetsAdd() {
		assertEquals(Insets.of(3, 5, 7, 9), Insets.add(Insets.of(1, 2, 3, 4), Insets.of(2, 3, 4, 5)));
	}
}
