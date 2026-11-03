package dev.dine.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/* DrawActivity for a look on the phone: it stays, and a bar then redraws itself for 2 s and logs how
 * many draws ran, which the display's frame rate bounds. It also logs Paint's widths and line
 * spacing beside a TextView's measured size for the same texts, and how long 1000 measureText
 * calls take, and draws 50 times into a bitmap an ImageView shows while the view redraws it. */
public class DrawStayActivity extends DrawActivity {
	private static class SpinView extends View {
		private final Paint paint = new Paint();
		private long start = -1;
		private int draws;

		SpinView(Context context) {
			super(context);
			paint.setColor(0xff0080ff);
		}

		@Override
		public void onDraw(Canvas canvas) {
			canvas.drawRect(0, 0, getWidth() * (draws % 60) / 60f, getHeight(), paint);
			long now = SystemClock.uptimeMillis();
			if (start < 0)
				start = now;
			draws++;
			if (now - start < 2000)
				invalidate();
			else
				Log.i(TAG, "widgets spin " + draws + " in " + (now - start) + " ms");
		}
	}

	private void logMetrics() {
		String[] texts = {"Hello world", "The quick brown fox", "0123456789", "WWWWW iiiii"};
		for (int sp : new int[] {14, 18}) {
			for (String text : texts) {
				TextView label = new TextView(this);
				label.setTextSize(sp);
				label.setText(text);
				label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
				Paint paint = new Paint();
				paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
				Log.i(TAG, "widgets metric \"" + text + "\" " + sp + "sp paint " + (int)Math.ceil(paint.measureText(text)) + " " + paint.getFontMetrics(null)
				               + " label " + label.getMeasuredWidth() + "x" + label.getMeasuredHeight());
			}
		}
	}

	private void logMeasureTime() {
		Paint paint = new Paint();
		paint.setTextSize(42);
		long start = System.nanoTime();
		for (int i = 0; i < 1000; i++)
			paint.measureText("The quick brown fox");
		Log.i(TAG, "widgets measure 1000 in " + (System.nanoTime() - start) / 1000000 + " ms");
	}

	// each pass swaps the bitmap's pixels while uikit's layers may be replaying the last frame
	private void redrawBitmap(final ImageView view, final Bitmap bitmap, final int pass) {
		if (pass == 50) {
			Log.i(TAG, "widgets bitmap redraw done");
			return;
		}
		Paint paint = new Paint();
		paint.setColor(pass % 2 == 0 ? 0xffff8000 : 0xff0080ff);
		new Canvas(bitmap).drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), paint);
		view.invalidate();
		view.post(new Runnable() {
			public void run() {
				redrawBitmap(view, bitmap, pass + 1);
			}
		});
	}

	@Override
	protected void done() {
		logMetrics();
		logMeasureTime();
		Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
		ImageView redrawn = new ImageView(this);
		redrawn.setImageBitmap(bitmap);
		root.addView(redrawn, new FrameLayout.LayoutParams(64, 64, Gravity.TOP | Gravity.RIGHT));
		redrawBitmap(redrawn, bitmap, 0);
		int height = (int)(40 * getResources().getDisplayMetrics().density);
		root.addView(new SpinView(this), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM));
	}
}
