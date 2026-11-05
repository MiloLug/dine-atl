package dev.dine.framework.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

	// a Picture keeps its calls: scaled, its edges stay sharp
	public void testAScaledPictureStaysSharp() {
		Picture picture = new Picture();
		Paint red = new Paint();
		red.setColor(0xffff0000);
		picture.beginRecording(4, 4).drawRect(1, 1, 2, 2, red);
		picture.endRecording();
		Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		new Canvas(bitmap).drawPicture(picture, new Rect(0, 0, 16, 16));
		assertEquals(0, bitmap.getPixel(3, 3));
		assertEquals(0xffff0000, bitmap.getPixel(4, 4));
		assertEquals(0xffff0000, bitmap.getPixel(7, 7));
		assertEquals(0, bitmap.getPixel(8, 8));
	}

	private static Paint paint(int color) {
		Paint paint = new Paint();
		paint.setColor(color);
		return paint;
	}

	// one call of each kind a Canvas makes: state, clips, shapes, text, a bitmap, a drawable, a layer, CLEAR
	private static void drawAll(Canvas canvas, Paint red, Bitmap source, Drawable drawable) {
		Paint stroke = paint(0xff0000ff);
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeWidth(1);
		Paint clear = new Paint();
		clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		canvas.save();
		canvas.translate(1, 1);
		canvas.scale(1.5f, 1);
		canvas.rotate(90, 6, 6);
		Matrix shift = new Matrix();
		shift.setTranslate(0.5f, 0);
		canvas.concat(shift);
		canvas.clipRect(0, 0, 12, 12);
		Path round = new Path();
		round.addCircle(6, 6, 6, Path.Direction.CW);
		canvas.clipPath(round);
		canvas.drawRect(0, 0, 4, 4, red);
		canvas.drawRoundRect(4, 0, 8, 4, 1, 1, paint(0xff00ff00));
		canvas.drawLine(0, 5, 10, 5, stroke);
		canvas.drawLines(new float[] {0, 7, 10, 7, 2, 0, 2, 4}, stroke);
		Path wedge = new Path();
		wedge.moveTo(0, 8);
		wedge.lineTo(4, 12);
		wedge.lineTo(0, 12);
		wedge.close();
		canvas.drawPath(wedge, red);
		canvas.drawArc(4, 8, 8, 12, 0, 270, true, paint(0xff00ff00));
		canvas.drawText("x", 8, 12, paint(0xff000000));
		canvas.drawBitmap(source, 8, 0, null);
		drawable.setBounds(8, 4, 12, 8);
		drawable.draw(canvas);
		canvas.restore();
		canvas.saveLayer(null, null);
		canvas.drawRect(0, 0, 16, 16, paint(0x80ff0000));
		canvas.drawRect(2, 2, 6, 6, clear);
		canvas.restore();
		canvas.drawRect(10, 10, 14, 14, clear);
	}

	// replayed, a Picture draws what the same calls draw directly, with the paints and bitmaps as recorded
	public void testAPictureReplaysEveryCall() {
		Bitmap source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
		source.eraseColor(0xff00ffff);
		Bitmap tile = Bitmap.createBitmap(new int[] {0xffff00ff, 0xff00ff00, 0xff0000ff, 0xffffffff}, 2, 2, Bitmap.Config.ARGB_8888);
		Drawable drawable = new BitmapDrawable(getInstrumentation().getTargetContext().getResources(), tile);
		Bitmap direct = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		direct.eraseColor(0xff808080);
		drawAll(new Canvas(direct), paint(0xffff0000), source, drawable);

		Picture picture = new Picture();
		Paint red = paint(0xffff0000);
		drawAll(picture.beginRecording(16, 16), red, source, drawable);
		picture.endRecording();
		red.setColor(0xff0000ff);
		source.eraseColor(0xffffff00);
		drawable.setBounds(0, 0, 1, 1); // a drawable replays at the bounds it was drawn at
		Bitmap replayed = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
		replayed.eraseColor(0xff808080);
		new Canvas(replayed).drawPicture(picture);
		for (int y = 0; y < 16; y++)
			for (int x = 0; x < 16; x++)
				assertEquals("at " + x + "," + y, Integer.toHexString(direct.getPixel(x, y)), Integer.toHexString(replayed.getPixel(x, y)));
	}
}
