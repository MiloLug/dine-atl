package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import java.util.Arrays;

// frame.9.png: 8x8 inside its border, stretching 2..6 both ways, padding 1, 2, 1, 3
public class NinePatchTest extends android.test.InstrumentationTestCase {
	private Resources resources() {
		return getInstrumentation().getTargetContext().getResources();
	}

	public void testResourceIsANinePatch() {
		Drawable drawable = resources().getDrawable(R.drawable.frame);
		assertTrue(drawable instanceof NinePatchDrawable);
		Rect padding = new Rect();
		assertTrue(drawable.getPadding(padding));
		assertEquals(new Rect(1, 2, 1, 3), padding);
		assertEquals(8, drawable.getIntrinsicWidth());
		assertEquals(8, drawable.getIntrinsicHeight());
	}

	public void testNinePatchXml() {
		Drawable drawable = resources().getDrawable(R.drawable.framed);
		assertTrue(drawable instanceof NinePatchDrawable);
		Rect padding = new Rect();
		assertTrue(drawable.getPadding(padding));
		assertEquals(new Rect(1, 2, 1, 3), padding);
		assertEquals(8, drawable.getIntrinsicWidth());
	}

	public void testPlainPngIsABitmap() {
		assertTrue(resources().getDrawable(R.drawable.photo) instanceof BitmapDrawable);
	}

	public void testBitmapKeepsTheChunk() {
		Bitmap bitmap = BitmapFactory.decodeResource(resources(), R.drawable.frame);
		assertEquals(8, bitmap.getWidth());
		assertTrue(NinePatch.isNinePatchChunk(bitmap.getNinePatchChunk()));
		Rect padding = new Rect();
		BitmapFactory.decodeResourceStream(resources(), null, resources().openRawResource(R.drawable.photo), padding, null);
		assertEquals(new Rect(-1, -1, -1, -1), padding);
		assertNull(BitmapFactory.decodeResource(resources(), R.drawable.photo).getNinePatchChunk());
	}

	public void testTruncatedChunk() {
		byte[] chunk = BitmapFactory.decodeResource(resources(), R.drawable.frame).getNinePatchChunk();
		byte[] truncated = Arrays.copyOf(chunk, chunk.length - 4);
		assertFalse(NinePatch.isNinePatchChunk(truncated));
		assertFalse(NinePatch.isNinePatchChunk(new byte[8]));
		try {
			new NinePatchDrawable(resources(), Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), truncated, new Rect(), null);
			fail("a truncated chunk");
		} catch (RuntimeException expected) {
		}
	}

	public void testTint() {
		Drawable drawable = resources().getDrawable(R.drawable.frame);
		drawable.setTint(Color.RED);
		new NinePatchDrawable(resources(), null, null, null, null).setTint(Color.RED); // nothing to tint
	}

	public void testCopiesShareTheState() {
		Drawable drawable = resources().getDrawable(R.drawable.frame);
		Drawable copy = drawable.getConstantState().newDrawable();
		assertNotSame(drawable, copy);
		assertTrue(copy instanceof NinePatchDrawable);
		Rect padding = new Rect();
		copy.getPadding(padding);
		assertEquals(new Rect(1, 2, 1, 3), padding);
	}
}
