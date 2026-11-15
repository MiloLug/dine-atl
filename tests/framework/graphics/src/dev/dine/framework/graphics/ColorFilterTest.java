package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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

	// AOSP's draws put the color filter on the paint and the tint only where there is none
	public void testAColorFilterWinsOverATint() {
		Bitmap white = Bitmap.createBitmap(new int[] {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff}, 2, 2, Bitmap.Config.ARGB_8888);
		BitmapDrawable drawable = new BitmapDrawable(getInstrumentation().getTargetContext().getResources(), white);
		drawable.setTint(0xffff0000);
		drawable.setColorFilter(new PorterDuffColorFilter(0xff0000ff, PorterDuff.Mode.SRC_IN));
		drawable.setBounds(0, 0, 2, 2);
		Bitmap target = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		drawable.draw(new Canvas(target));
		assertColor(0xff0000ff, target.getPixel(1, 1));
		drawable.setColorFilter(null);
		target.eraseColor(0);
		drawable.draw(new Canvas(target));
		assertColor(0xffff0000, target.getPixel(1, 1));
	}

	// AOSP: a BitmapDrawable's paint carries the flag its setFilterBitmap sets
	public void testBitmapDrawableSetsItsPaintsFilterFlag() {
		BitmapDrawable drawable = new BitmapDrawable(getInstrumentation().getTargetContext().getResources(), Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888));
		assertTrue(drawable.getPaint().isFilterBitmap());
		assertTrue(drawable.isFilterBitmap());
		drawable.setFilterBitmap(false);
		assertFalse(drawable.getPaint().isFilterBitmap());
		assertFalse(drawable.isFilterBitmap());
		drawable.getPaint().setFilterBitmap(true);
		assertTrue(drawable.isFilterBitmap());
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
