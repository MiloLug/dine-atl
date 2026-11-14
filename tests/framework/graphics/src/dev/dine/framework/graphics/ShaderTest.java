package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import junit.framework.TestCase;

public class ShaderTest extends TestCase {
	private static final int[] COLORS = {Color.RED, Color.BLUE};

	public void testLocalMatrix() {
		Shader shader = new LinearGradient(0, 0, 10, 0, COLORS, null, Shader.TileMode.CLAMP);
		Matrix matrix = new Matrix();
		assertFalse(shader.getLocalMatrix(matrix));
		Matrix scale = new Matrix();
		scale.setScale(2, 3);
		shader.setLocalMatrix(scale);
		scale.setScale(5, 5); // the shader keeps its own copy
		assertTrue(shader.getLocalMatrix(matrix));
		float[] values = new float[9];
		matrix.getValues(values);
		assertEquals(2f, values[Matrix.MSCALE_X]);
		assertEquals(3f, values[Matrix.MSCALE_Y]);
		shader.setLocalMatrix(new Matrix());
		assertFalse(shader.getLocalMatrix(matrix));
	}

	public void testGradientArguments() {
		try {
			new LinearGradient(0, 0, 1, 1, new int[] {Color.RED}, null, Shader.TileMode.CLAMP);
			fail("one color");
		} catch (IllegalArgumentException expected) {
		}
		try {
			new RadialGradient(0, 0, 5, COLORS, new float[] {0}, Shader.TileMode.REPEAT);
			fail("positions of another length");
		} catch (IllegalArgumentException expected) {
		}
		try {
			new RadialGradient(0, 0, 0, COLORS, null, Shader.TileMode.CLAMP);
			fail("radius 0");
		} catch (IllegalArgumentException expected) {
		}
	}

	public void testPaintsKeepTheirShader() {
		Paint paint = new Paint();
		Shader sweep = new SweepGradient(5, 5, Color.RED, Color.GREEN);
		assertSame(sweep, paint.setShader(sweep));
		Paint copy = new Paint(paint);
		assertSame(sweep, copy.getShader());
		paint.setShader(null);
		assertNull(paint.getShader());
		assertSame(sweep, copy.getShader());
		copy.reset();
		assertNull(copy.getShader());
	}

	public void testLocalMatrixAfterSetShader() {
		Paint paint = new Paint();
		Shader shader = new LinearGradient(0, 0, 10, 0, COLORS, new float[] {0.25f, 0.75f}, Shader.TileMode.MIRROR);
		paint.setShader(shader);
		Matrix rotate = new Matrix();
		rotate.setRotate(90);
		shader.setLocalMatrix(rotate);
		assertSame(shader, paint.getShader());
		assertTrue(shader.getLocalMatrix(new Matrix()));
	}

	private static Bitmap filled(Shader shader, int width, int height) {
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setShader(shader);
		new Canvas(bitmap).drawRect(0, 0, width, height, paint);
		return bitmap;
	}

	private static void assertSameColor(String what, int want, int got) {
		for (int shift = 0; shift < 32; shift += 8)
			assertTrue(what + ": " + Integer.toHexString(got) + ", want " + Integer.toHexString(want),
			           Math.abs((want >> shift & 0xff) - (got >> shift & 0xff)) <= 2);
	}

	// REPEAT starts the ramp again every period
	public void testLinearGradientRepeats() {
		Bitmap bitmap = filled(new LinearGradient(0, 0, 4, 0, COLORS, null, Shader.TileMode.REPEAT), 16, 1);
		assertTrue(Color.red(bitmap.getPixel(0, 0)) > Color.red(bitmap.getPixel(3, 0)) + 100);
		assertSameColor("second period's start", bitmap.getPixel(0, 0), bitmap.getPixel(4, 0));
		assertSameColor("third period's end", bitmap.getPixel(3, 0), bitmap.getPixel(11, 0));
	}

	// MIRROR runs every second period backwards
	public void testLinearGradientMirrors() {
		Bitmap bitmap = filled(new LinearGradient(0, 0, 4, 0, COLORS, null, Shader.TileMode.MIRROR), 16, 1);
		assertTrue(Color.red(bitmap.getPixel(0, 0)) > Color.red(bitmap.getPixel(3, 0)) + 100);
		assertSameColor("second period's start", bitmap.getPixel(3, 0), bitmap.getPixel(4, 0));
		assertSameColor("second period's end", bitmap.getPixel(0, 0), bitmap.getPixel(7, 0));
		assertSameColor("third period's start", bitmap.getPixel(0, 0), bitmap.getPixel(8, 0));
	}

	public void testRadialGradientRunsFromItsCenterAndClamps() {
		Bitmap bitmap = filled(new RadialGradient(8, 8, 6, COLORS, null, Shader.TileMode.CLAMP), 16, 16);
		assertTrue(Color.red(bitmap.getPixel(8, 8)) > 200);
		assertTrue(Color.red(bitmap.getPixel(8, 8)) > Color.red(bitmap.getPixel(11, 8)) + 50);
		assertEquals(Integer.toHexString(Color.BLUE), Integer.toHexString(bitmap.getPixel(15, 8)));
		assertEquals(Integer.toHexString(Color.BLUE), Integer.toHexString(bitmap.getPixel(0, 0)));
	}

	// from 3 o'clock, clockwise
	public void testSweepGradientTurnsClockwise() {
		Bitmap bitmap = filled(new SweepGradient(8, 8, Color.RED, Color.BLUE), 16, 16);
		int[] reds = {Color.red(bitmap.getPixel(14, 9)), Color.red(bitmap.getPixel(8, 14)), Color.red(bitmap.getPixel(1, 8)),
		              Color.red(bitmap.getPixel(8, 1)), Color.red(bitmap.getPixel(14, 6))};
		for (int i = 1; i < reds.length; i++)
			assertTrue(java.util.Arrays.toString(reds), reds[i - 1] > reds[i] + 30);
	}

	// stops of different alpha blend premultiplied (Android's gradients): white that fades out stays white
	public void testStopsBlendPremultiplied() {
		Shader[] shaders = {new LinearGradient(0, 0, 16, 0, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP),
		                    new RadialGradient(8, 8, 8, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP),
		                    new SweepGradient(8, 8, Color.WHITE, Color.TRANSPARENT)};
		int[][] halfway = {{8, 8}, {12, 8}, {1, 8}};
		for (int i = 0; i < shaders.length; i++) {
			Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
			bitmap.eraseColor(Color.WHITE);
			Paint paint = new Paint();
			paint.setShader(shaders[i]);
			new Canvas(bitmap).drawRect(0, 0, 16, 16, paint);
			assertSameColor("shader " + i, Color.WHITE, bitmap.getPixel(halfway[i][0], halfway[i][1]));
		}
	}
}
