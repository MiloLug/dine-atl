package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class BitmapShaderTest extends android.test.InstrumentationTestCase {
	private static final int RED = 0xffff0000, GREEN = 0xff00ff00, BLUE = 0xff0000ff, WHITE = 0xffffffff;

	private static Bitmap quad() {
		return Bitmap.createBitmap(new int[] {RED, GREEN, BLUE, WHITE}, 2, 2, Bitmap.Config.ARGB_8888);
	}

	private static Bitmap fill(Shader shader, int size) {
		Bitmap target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setShader(shader);
		new Canvas(target).drawRect(0, 0, size, size, paint);
		return target;
	}

	public void testClampDrawsTheBitmap() {
		Bitmap target = fill(new BitmapShader(quad(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), 2);
		assertEquals(RED, target.getPixel(0, 0));
		assertEquals(GREEN, target.getPixel(1, 0));
		assertEquals(WHITE, target.getPixel(1, 1));
	}

	public void testRepeatTiles() {
		Bitmap target = fill(new BitmapShader(quad(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT), 4);
		assertEquals(RED, target.getPixel(2, 0));
		assertEquals(BLUE, target.getPixel(2, 3));
	}

	public void testLocalMatrixMoves() {
		BitmapShader shader = new BitmapShader(quad(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		Matrix matrix = new Matrix();
		matrix.setTranslate(1, 1);
		shader.setLocalMatrix(matrix);
		Bitmap target = fill(shader, 3);
		assertEquals(RED, target.getPixel(1, 1));
		assertEquals(WHITE, target.getPixel(2, 2));
	}

	// SRC_IN: the source (B) where the destination (A) is
	public void testComposeSrcIn() {
		Bitmap mask = Bitmap.createBitmap(new int[] {RED, 0, RED, 0}, 2, 2, Bitmap.Config.ARGB_8888);
		Bitmap blue = Bitmap.createBitmap(new int[] {BLUE, BLUE, BLUE, BLUE}, 2, 2, Bitmap.Config.ARGB_8888);
		Shader shader = new ComposeShader(new BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
		                                  new BitmapShader(blue, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), PorterDuff.Mode.SRC_IN);
		Bitmap target = fill(shader, 2);
		assertEquals(BLUE, target.getPixel(0, 1));
		assertEquals(0, target.getPixel(1, 1) >>> 24);
	}

	// the layers through the icon mask: the middle shows the foreground, a corner nothing
	public void testAdaptiveIconIsMasked() {
		final int[] got = new int[2];
		final Object[] kind = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Drawable icon = getInstrumentation().getTargetContext().getResources().getDrawable(R.drawable.adaptive);
				kind[0] = icon;
				Bitmap target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
				icon.setBounds(0, 0, 100, 100);
				icon.draw(new Canvas(target));
				got[0] = target.getPixel(50, 50);
				got[1] = target.getPixel(1, 1);
			}
		});
		assertTrue(kind[0] instanceof AdaptiveIconDrawable);
		assertEquals(getInstrumentation().getTargetContext().getResources().getColor(R.color.pressed), got[0]);
		assertEquals(0, got[1] >>> 24);
	}

	// its layers draw in its own space, wherever its bounds are
	public void testAdaptiveIconAwayFromTheOrigin() {
		final int[] got = new int[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Drawable icon = getInstrumentation().getTargetContext().getResources().getDrawable(R.drawable.adaptive);
				Bitmap target = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
				icon.setBounds(200, 200, 300, 300);
				icon.draw(new Canvas(target));
				got[0] = target.getPixel(250, 250);
				got[1] = target.getPixel(201, 201);
			}
		});
		assertEquals(getInstrumentation().getTargetContext().getResources().getColor(R.color.pressed), got[0]);
		assertEquals(0, got[1] >>> 24);
	}

	public void testBitmapDrawablePaintDraws() {
		Bitmap bitmap = Bitmap.createBitmap(new int[] {RED, RED, RED, RED}, 2, 2, Bitmap.Config.ARGB_8888);
		BitmapDrawable drawable = new BitmapDrawable(getInstrumentation().getTargetContext().getResources(), bitmap);
		assertSame(drawable.getPaint(), drawable.getPaint());
		drawable.getPaint().setAlpha(128);
		assertEquals(128, drawable.getAlpha());
		Bitmap target = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		drawable.setBounds(0, 0, 2, 2);
		drawable.draw(new Canvas(target));
		int alpha = target.getPixel(1, 1) >>> 24;
		assertTrue("alpha " + alpha, Math.abs(alpha - 128) <= 1);
	}
}
