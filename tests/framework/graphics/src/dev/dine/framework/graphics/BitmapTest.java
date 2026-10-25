package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

public class BitmapTest extends android.test.InstrumentationTestCase {
	private Resources resources() {
		return getInstrumentation().getTargetContext().getResources();
	}

	public void testDecodesAPngResource() {
		Bitmap bitmap = BitmapFactory.decodeResource(resources(), R.drawable.photo);
		assertNotNull(bitmap);
		assertEquals(64, bitmap.getWidth());
		assertEquals(64, bitmap.getHeight());
		assertEquals(Bitmap.Config.ARGB_8888, bitmap.getConfig());
	}

	public void testCopyKeepsTheSize() {
		Bitmap copy = Bitmap.createBitmap(BitmapFactory.decodeResource(resources(), R.drawable.photo));
		assertEquals(64, copy.getWidth());
		assertEquals(64, copy.getHeight());
	}

	// a resource decodes once: the drawables made from its constant state share the Bitmap
	public void testLoadsShareTheBitmap() {
		final BitmapDrawable[] loads = new BitmapDrawable[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				loads[0] = (BitmapDrawable)resources().getDrawable(R.drawable.photo);
				loads[1] = (BitmapDrawable)resources().getDrawable(R.drawable.photo);
			}
		});
		assertNotSame(loads[0], loads[1]);
		assertSame(loads[0].getBitmap(), loads[1].getBitmap());
		assertEquals(64, loads[1].getIntrinsicWidth());
	}
}
