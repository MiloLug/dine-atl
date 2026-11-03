package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

public class ColorFilterTest extends android.test.InstrumentationTestCase {
	private static void assertColor(int expected, int actual) {
		for (int shift = 0; shift < 32; shift += 8)
			assertTrue(String.format("#%08x is not #%08x", actual, expected), Math.abs(((expected >>> shift) & 0xff) - ((actual >>> shift) & 0xff)) <= 1);
	}

	private static int fill(int color, android.graphics.ColorFilter filter) {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setColor(color);
		paint.setColorFilter(filter);
		new Canvas(bitmap).drawRect(0, 0, 4, 4, paint);
		return bitmap.getPixel(2, 2);
	}

	public void testColorMatrixOnAPaint() {
		ColorMatrix grey = new ColorMatrix();
		grey.setSaturation(0);
		int red = fill(0xffff0000, new ColorMatrixColorFilter(grey));
		assertColor(0xff363636, red); // 0.213 of red
	}

	public void testLightingOnAPaint() {
		assertColor(0xff8080a0, fill(0xffffffff, new LightingColorFilter(0x808080, 0x000020)));
	}

	public void testColorMatrixOnABitmap() {
		ColorMatrix grey = new ColorMatrix();
		grey.setSaturation(0);
		Bitmap red = Bitmap.createBitmap(new int[] {0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000}, 2, 2, Bitmap.Config.ARGB_8888);
		BitmapDrawable drawable = new BitmapDrawable(getInstrumentation().getTargetContext().getResources(), red);
		drawable.setColorFilter(new ColorMatrixColorFilter(grey));
		Bitmap target = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		drawable.setBounds(0, 0, 2, 2);
		drawable.draw(new Canvas(target));
		assertColor(0xff363636, target.getPixel(1, 1));
	}
}
