package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import java.util.Arrays;
import org.xmlpull.v1.XmlPullParserException;

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

	// the frame at 24x20: its fixed corners kept, its 8x8 body's gradient stretched between them
	private Bitmap drawn(Drawable drawable) {
		Bitmap bitmap = Bitmap.createBitmap(24, 20, Bitmap.Config.ARGB_8888);
		drawable.setBounds(0, 0, 24, 20);
		drawable.draw(new Canvas(bitmap));
		return bitmap;
	}

	public void testDrawnPixels() {
		Bitmap source = BitmapFactory.decodeResource(resources(), R.drawable.frame);
		Bitmap drawn = drawn(resources().getDrawable(R.drawable.frame));
		for (int[] corner : new int[][] {{0, 0, 0, 0}, {23, 0, 7, 0}, {0, 19, 0, 7}, {23, 19, 7, 7}}) // fixed 2x2 corners
			assertEquals(String.format("corner %d,%d", corner[0], corner[1]), source.getPixel(corner[2], corner[3]), drawn.getPixel(corner[0], corner[1]));
		assertEquals(source.getPixel(0, 3), drawn.getPixel(0, 10)); // the left edge, stretched down
		// the body's red grows to the right and its green downward
		assertTrue(Color.red(drawn.getPixel(8, 10)) < Color.red(drawn.getPixel(16, 10)));
		assertTrue(Color.green(drawn.getPixel(12, 6)) < Color.green(drawn.getPixel(12, 14)));
		assertEquals(0xff, Color.alpha(drawn.getPixel(12, 10)));
	}

	public void testTint() {
		Drawable drawable = resources().getDrawable(R.drawable.frame);
		drawable.setTint(Color.RED);
		Bitmap tinted = drawn(drawable);
		for (int[] at : new int[][] {{0, 0}, {23, 19}, {12, 10}})
			assertEquals(Color.RED, tinted.getPixel(at[0], at[1]));
		drawable.setTintList(null);
		assertEquals(drawn(resources().getDrawable(R.drawable.frame)).getPixel(0, 0), drawn(drawable).getPixel(0, 0));
		new NinePatchDrawable(resources(), null, null, null, null).setTint(Color.RED); // nothing to tint
	}

	// AOSP: a nine-patch element needs a source with a chunk
	public void testNinePatchXmlWithoutAChunk() throws Exception {
		try {
			Drawable.createFromXml(resources(), resources().getXml(R.drawable.framed_plain));
			fail("a plain PNG is no nine-patch");
		} catch (XmlPullParserException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("<nine-patch> requires a valid 9-patch source image"));
		}
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
