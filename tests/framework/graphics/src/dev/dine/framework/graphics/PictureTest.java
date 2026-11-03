package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;

// a recorded Picture plays back through Canvas.drawPicture and PictureDrawable
public class PictureTest extends android.test.InstrumentationTestCase {
	private static Picture redSquare() {
		Picture picture = new Picture();
		Paint red = new Paint();
		red.setColor(0xffff0000);
		picture.beginRecording(2, 2).drawRect(0, 0, 2, 2, red);
		picture.endRecording();
		return picture;
	}

	public void testDrawPicture() {
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.translate(2, 2);
		canvas.drawPicture(redSquare());
		assertEquals(0xffff0000, bitmap.getPixel(3, 3));
		assertEquals(0, bitmap.getPixel(1, 1));
	}

	public void testPictureDrawable() {
		PictureDrawable drawable = new PictureDrawable(redSquare());
		assertEquals(2, drawable.getIntrinsicWidth());
		Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
		drawable.setBounds(1, 1, 3, 3);
		drawable.draw(new Canvas(bitmap));
		assertEquals(0xffff0000, bitmap.getPixel(2, 2));
		assertEquals(0, bitmap.getPixel(3, 3));
	}

	public void testImmutableSourceIsReturned() {
		Bitmap mutable = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		Bitmap immutable = mutable.copy(Bitmap.Config.ARGB_8888, false);
		assertSame(immutable, Bitmap.createBitmap(immutable));
		assertNotSame(mutable, Bitmap.createBitmap(mutable));
	}
}
