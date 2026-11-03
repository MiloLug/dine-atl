package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

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

	private static Paint paint(int color) {
		Paint paint = new Paint();
		paint.setColor(color);
		return paint;
	}

	private static void drawRedSquare(Bitmap bitmap) {
		new Canvas(bitmap).drawRect(2, 2, 8, 8, paint(0xffff0000));
	}

	public void testCanvasDrawsIntoThePixels() {
		Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
		drawRedSquare(bitmap);
		assertEquals(0xffff0000, bitmap.getPixel(5, 5));
		assertEquals(0, bitmap.getPixel(0, 0));
	}

	// a view's draw runs there: gsk renders with the display's renderer
	public void testCanvasDrawsIntoThePixelsOnTheMainThread() {
		final int[] got = new int[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
				drawRedSquare(bitmap);
				got[0] = bitmap.getPixel(5, 5);
				got[1] = bitmap.getPixel(9, 9);
			}
		});
		assertEquals(0xffff0000, got[0]);
		assertEquals(0, got[1]);
	}

	public void testDrawingAgainKeepsWhatWasDrawn() {
		Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.drawRect(0, 0, 5, 10, paint(0xff00ff00));
		assertEquals(0xff00ff00, bitmap.getPixel(1, 1));
		canvas.drawRect(5, 0, 10, 10, paint(0xff0000ff));
		assertEquals(0xff00ff00, bitmap.getPixel(1, 1));
		assertEquals(0xff0000ff, bitmap.getPixel(8, 8));
	}

	public void testEraseColorReadsBackUnpremultiplied() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		drawRedSquare(bitmap);
		bitmap.eraseColor(0x80ff0000);
		assertEquals(0x80ff0000, bitmap.getPixel(1, 1));
		bitmap.eraseColor(0);
		assertEquals(0, bitmap.getPixel(1, 1));
	}

	public void testSetAndGetPixels() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		int[] colors = {0xff00ff00, 0x00000000, 0xff0000ff, 0x7f123456};
		bitmap.setPixels(colors, 0, 2, 1, 1, 2, 2);
		int[] back = new int[4];
		bitmap.getPixels(back, 0, 2, 1, 1, 2, 2);
		assertEquals(Arrays.toString(colors), Arrays.toString(back));
		assertEquals(0, bitmap.getPixel(0, 0));
		assertEquals(0x7f123456, bitmap.getPixel(2, 2));
		// an offset and a wider stride
		int[] wide = new int[7];
		bitmap.getPixels(wide, 1, 3, 1, 1, 2, 2);
		assertEquals("[0, -16711936, 0, 0, -16776961, 2131899478, 0]", Arrays.toString(wide));
		bitmap.setPixel(3, 3, 0xffabcdef);
		assertEquals(0xffabcdef, bitmap.getPixel(3, 3));
		try {
			bitmap.getPixel(4, 0);
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	public void testCreateBitmapFromColors() {
		Bitmap bitmap = Bitmap.createBitmap(new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff}, 2, 2, Bitmap.Config.ARGB_8888);
		assertEquals(0xff00ff00, bitmap.getPixel(1, 0));
		assertEquals(0xff0000ff, bitmap.getPixel(0, 1));
		assertFalse(bitmap.isMutable());
	}

	public void testCreateBitmapFromASource() {
		Bitmap source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(source);
		canvas.drawRect(0, 0, 2, 4, paint(0xffff0000));
		canvas.drawRect(2, 0, 4, 4, paint(0xff0000ff));
		Bitmap right = Bitmap.createBitmap(source, 2, 0, 2, 4);
		assertEquals(2, right.getWidth());
		assertEquals(0xff0000ff, right.getPixel(0, 0));
		Bitmap scaled = Bitmap.createScaledBitmap(source, 8, 8, false);
		assertEquals(0xffff0000, scaled.getPixel(1, 1));
		assertEquals(0xff0000ff, scaled.getPixel(6, 6));
		Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, true);
		canvas.drawRect(0, 0, 4, 4, paint(0xff00ff00));
		assertEquals(0xffff0000, copy.getPixel(0, 0));
	}

	public void testCopyPixelsToBuffer() {
		Bitmap bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(new int[] {0xff102030, 0x80ff0000}, 0, 2, 0, 0, 2, 1);
		ByteBuffer bytes = ByteBuffer.allocate(bitmap.getByteCount());
		bitmap.copyPixelsToBuffer(bytes);
		assertEquals("[16, 32, 48, -1, -128, 0, 0, -128]", Arrays.toString(bytes.array()));
		Bitmap rgb = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
		rgb.setPixel(0, 0, 0xffff0000);
		ShortBuffer shorts = ShortBuffer.allocate(1);
		rgb.copyPixelsToBuffer(shorts);
		assertEquals((short)0xf800, shorts.get(0));
	}

	public void testCompressedPngDecodesBack() throws Exception {
		Bitmap bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(0xff00ff00);
		bitmap.setPixel(1, 1, 0xffff0000);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		byte[] png = out.toByteArray();
		Bitmap back = BitmapFactory.decodeByteArray(png, 0, png.length);
		assertEquals(3, back.getWidth());
		assertEquals(0xffff0000, back.getPixel(1, 1));
		assertEquals(0xff00ff00, back.getPixel(0, 2));
	}

	public void testPictureDrawsWhatItRecorded() {
		Picture picture = new Picture();
		picture.beginRecording(4, 4).drawRect(0, 0, 4, 4, paint(0xff0000ff));
		picture.endRecording();
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		picture.draw(new Canvas(bitmap));
		assertEquals(4, picture.getWidth());
		assertEquals(0xff0000ff, bitmap.getPixel(2, 2));
	}

	// a drawable made before the bitmap changed draws its new pixels, into a Canvas(Bitmap) too
	public void testDrawableSeesNewPixels() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		BitmapDrawable drawable = new BitmapDrawable(resources(), bitmap);
		new Canvas(bitmap).drawRect(0, 0, 4, 4, paint(0xffff0000));
		Bitmap target = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		drawable.setBounds(0, 0, 4, 4);
		drawable.draw(new Canvas(target));
		assertEquals(0xffff0000, target.getPixel(1, 1));
	}
}
