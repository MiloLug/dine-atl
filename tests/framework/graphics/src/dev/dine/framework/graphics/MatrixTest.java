package dev.dine.framework.graphics;

import android.graphics.Matrix;
import android.graphics.RectF;
import junit.framework.TestCase;

public class MatrixTest extends TestCase {
	private static final float EPSILON = 1e-4f;

	private static void assertValues(Matrix matrix, float... expected) {
		float[] values = new float[9];
		matrix.getValues(values);
		for (int i = 0; i < 9; i++)
			assertEquals("value " + i, expected[i], values[i], EPSILON);
	}

	// with the perspective divide
	private static float[] map(Matrix matrix, float x, float y) {
		float[] v = new float[9];
		matrix.getValues(v);
		float w = v[6] * x + v[7] * y + v[8];
		return new float[] {(v[0] * x + v[1] * y + v[2]) / w, (v[3] * x + v[4] * y + v[5]) / w};
	}

	public void testCopyKeepsValues() {
		Matrix src = new Matrix();
		src.setTranslate(3, 4);
		assertValues(new Matrix(src), 1, 0, 3, 0, 1, 4, 0, 0, 1);
	}

	public void testSetRotateReplaces() {
		Matrix matrix = new Matrix();
		matrix.setTranslate(5, 5);
		matrix.setRotate(90);
		assertValues(matrix, 0, -1, 0, 1, 0, 0, 0, 0, 1);
	}

	public void testSetSinCos() {
		Matrix matrix = new Matrix();
		matrix.setSinCos(1, 0, 2, 3);
		assertValues(matrix, 0, -1, 5, 1, 0, 1, 0, 0, 1);
	}

	public void testSetSkew() {
		Matrix matrix = new Matrix();
		matrix.setSkew(0.5f, 0.25f, 4, 8);
		assertValues(matrix, 1, 0.5f, -4, 0.25f, 1, -1, 0, 0, 1);
	}

	public void testSetConcatAppliesSecondFirst() {
		Matrix scale = new Matrix();
		scale.setScale(2, 2);
		Matrix translate = new Matrix();
		translate.setTranslate(10, 0);
		Matrix matrix = new Matrix();
		assertTrue(matrix.setConcat(scale, translate));
		assertValues(matrix, 2, 0, 20, 0, 2, 0, 0, 0, 1);
	}

	public void testPreAndPostSkew() {
		Matrix pre = new Matrix();
		pre.setTranslate(1, 2);
		pre.preSkew(1, 0);
		assertValues(pre, 1, 1, 1, 0, 1, 2, 0, 0, 1);
		Matrix post = new Matrix();
		post.setTranslate(1, 2);
		post.postSkew(1, 0);
		assertValues(post, 1, 1, 3, 0, 1, 2, 0, 0, 1);
	}

	public void testPolyToPolyTranslate() {
		Matrix matrix = new Matrix();
		assertTrue(matrix.setPolyToPoly(new float[] {1, 1}, 0, new float[] {4, 6}, 0, 1));
		assertValues(matrix, 1, 0, 3, 0, 1, 5, 0, 0, 1);
	}

	public void testPolyToPolyAffine() {
		Matrix matrix = new Matrix();
		float[] src = {0, 0, 1, 0, 0, 1};
		float[] dst = {10, 10, 12, 10, 10, 13};
		assertTrue(matrix.setPolyToPoly(src, 0, dst, 0, 3));
		assertValues(matrix, 2, 0, 10, 0, 3, 10, 0, 0, 1);
	}

	public void testPolyToPolyPerspective() {
		Matrix matrix = new Matrix();
		float[] src = {0, 0, 100, 0, 100, 100, 0, 100};
		float[] dst = {10, 0, 90, 0, 100, 100, 0, 100};
		assertTrue(matrix.setPolyToPoly(src, 0, dst, 0, 4));
		for (int i = 0; i < 8; i += 2) {
			float[] p = map(matrix, src[i], src[i + 1]);
			assertEquals(dst[i], p[0], 1e-2f);
			assertEquals(dst[i + 1], p[1], 1e-2f);
		}
	}

	public void testMapPointsPerspective() {
		Matrix matrix = new Matrix();
		matrix.setValues(new float[] {1, 0, 0, 0, 1, 0, 0.01f, 0, 1});
		float[] points = {100, 50};
		matrix.mapPoints(points);
		assertEquals(50f, points[0], 1e-3f);
		assertEquals(25f, points[1], 1e-3f);
		float[] vectors = {100, 0};
		matrix.mapVectors(vectors);
		assertEquals(50f, vectors[0], 1e-3f);
	}

	public void testMapRadius() {
		Matrix matrix = new Matrix();
		matrix.setScale(2, 3);
		assertEquals((float)Math.sqrt(6) * 5, matrix.mapRadius(5), EPSILON);
	}

	public void testPreScaleKeepsPivot() {
		Matrix matrix = new Matrix();
		matrix.preScale(2, 2, 10, 10);
		assertValues(matrix, 2, 0, -10, 0, 2, -10, 0, 0, 1);
	}

	public void testPreRotateKeepsPivot() {
		Matrix matrix = new Matrix();
		matrix.setTranslate(5, 0);
		matrix.preRotate(90, 50, 50);
		assertValues(matrix, 0, -1, 105, 1, 0, 0, 0, 0, 1);
		float[] p = map(matrix, 50, 50);
		assertEquals(55f, p[0], EPSILON);
		assertEquals(50f, p[1], EPSILON);
	}

	public void testMapRectPerspective() {
		Matrix matrix = new Matrix();
		matrix.setValues(new float[] {1, 0, 0, 0, 1, 0, 0.01f, 0, 1});
		RectF rect = new RectF(0, 0, 100, 50);
		assertFalse(matrix.mapRect(rect));
		// the corners divide by 1, 2, 2 and 1
		assertEquals(0f, rect.left, EPSILON);
		assertEquals(0f, rect.top, EPSILON);
		assertEquals(50f, rect.right, EPSILON);
		assertEquals(50f, rect.bottom, EPSILON);
	}

	public void testMapRectReturnsRectStaysRect() {
		Matrix matrix = new Matrix();
		matrix.setScale(2, 3);
		assertTrue(matrix.mapRect(new RectF(0, 0, 1, 1)));
		matrix.setRotate(45);
		assertFalse(matrix.mapRect(new RectF(0, 0, 1, 1)));
	}

	public void testRectStaysRect() {
		Matrix matrix = new Matrix();
		matrix.setValues(new float[] {0, 2, 0, 3, 0, 0, 0, 0, 1});
		assertTrue(matrix.rectStaysRect());
		matrix.setValues(new float[] {1, 0, 0, 0, 1, 0, 0.01f, 0, 1});
		assertFalse(matrix.rectStaysRect());
		matrix.setValues(new float[] {0, 0, 0, 0, 0, 0, 0, 0, 1});
		assertFalse(matrix.rectStaysRect());
	}
}
