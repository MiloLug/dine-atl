package dev.dine.framework.graphics;

import android.atl.ATLTestHooks;
import android.graphics.Bitmap;
import java.util.Arrays;

// AndroidBitmap_lockPixels and unlockPixels (gtk; elsewhere the conversions they use): each config's
// NDK layout, written back on unlock
public class BitmapNdkTest extends android.test.InstrumentationTestCase {
	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder();
		for (byte b : bytes)
			out.append(String.format("%02x", b & 0xff));
		return out.toString();
	}

	public void testArgb8888IsPremultipliedRgba() {
		Bitmap bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(new int[] {0x80ff0000, 0xff102030}, 0, 2, 0, 0, 2, 1);
		byte[] pixels = ATLTestHooks.lockPixels(bitmap);
		assertEquals("80000080102030ff", hex(pixels));
		pixels[4] = 0x40;
		ATLTestHooks.unlockPixels(bitmap, pixels);
		assertEquals(0xff402030, bitmap.getPixel(1, 0));
		assertEquals(0x80ff0000, bitmap.getPixel(0, 0));
	}

	public void testRgb565() {
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
		bitmap.setPixel(0, 0, 0xff1f0000);
		byte[] pixels = ATLTestHooks.lockPixels(bitmap);
		assertEquals("0020", hex(pixels)); // level 4 of red, little-endian
		pixels[0] = 0x1f; // full blue
		pixels[1] = 0;
		ATLTestHooks.unlockPixels(bitmap, pixels);
		assertEquals(0xff0000ff, bitmap.getPixel(0, 0));
	}

	public void testAlpha8() {
		Bitmap bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ALPHA_8);
		bitmap.setPixel(0, 0, 0x80000000);
		byte[] pixels = ATLTestHooks.lockPixels(bitmap);
		assertEquals("8000", hex(pixels));
		pixels[1] = 0x40;
		ATLTestHooks.unlockPixels(bitmap, pixels);
		assertEquals(0x40000000, bitmap.getPixel(1, 0));
	}

	public void testArgb4444() {
		Bitmap source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		source.setPixel(0, 0, 0xff123456);
		Bitmap bitmap = source.copy(Bitmap.Config.ARGB_4444, true);
		byte[] pixels = ATLTestHooks.lockPixels(bitmap);
		assertEquals("5f13", hex(pixels)); // 15|rrrr gggg bbbb aaaa|0, little-endian
		pixels[1] = (byte)0xf3;
		ATLTestHooks.unlockPixels(bitmap, pixels);
		assertEquals(0xffff3355, bitmap.getPixel(0, 0));
	}

	public void testUnlockWithoutChangesKeepsThePixels() {
		Bitmap bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
		int[] colors = {0xff102030, 0x40ffffff, 0, 0xff000000, 0x80808080, 0xffabcdef};
		bitmap.setPixels(colors, 0, 3, 0, 0, 3, 2);
		ATLTestHooks.unlockPixels(bitmap, ATLTestHooks.lockPixels(bitmap));
		int[] back = new int[6];
		bitmap.getPixels(back, 0, 3, 0, 0, 3, 2);
		assertEquals(Arrays.toString(colors), Arrays.toString(back));
	}

	// AndroidBitmap_lockPixels fails on a recycled bitmap (ANDROID_BITMAP_RESULT_JNI_EXCEPTION)
	public void testARecycledBitmapDoesNotLock() {
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		bitmap.recycle();
		assertNull(ATLTestHooks.lockPixels(bitmap));
	}
}
