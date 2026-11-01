package dev.dine.framework.graphics;

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
}
