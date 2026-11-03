package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;

// VectorDrawable draws through a bitmap of its own
public class VectorDrawableTest extends android.test.InstrumentationTestCase {
	public void testDrawsItsPaths() {
		final int[] got = new int[2];
		final Object[] kind = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Drawable drawable = getInstrumentation().getTargetContext().getResources().getDrawable(R.drawable.half);
				kind[0] = drawable;
				Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
				drawable.setBounds(0, 0, 10, 10);
				drawable.draw(new Canvas(bitmap));
				got[0] = bitmap.getPixel(2, 5);
				got[1] = bitmap.getPixel(8, 5);
			}
		});
		assertTrue(kind[0] instanceof VectorDrawable);
		assertEquals(0xffff0000, got[0]);
		assertEquals(0, got[1]);
	}
}
