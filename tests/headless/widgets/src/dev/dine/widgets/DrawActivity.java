package dev.dine.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/* A view drawn through Canvas, text and a Layout included, for run-draw.sh: headless compares its
 * display lists with draw.displaylist, gtk draws it. It redraws after invalidate(), not after a
 * layout at the same size, and after showing again from INVISIBLE with an invalidate() while
 * hidden; then it finishes. Beside it, a view scrolled by 10 px keeps its selector background in
 * place. */
public class DrawActivity extends Activity {
	static final String TAG = "widgets";
	protected FrameLayout root;

	private class DrawView extends View {
		private final Bitmap photo = BitmapFactory.decodeResource(getResources(), R.drawable.photo);
		private int draws;

		DrawView(Context context) {
			super(context);
		}

		@Override
		public void onDraw(Canvas canvas) {
			Paint fill = new Paint();
			fill.setColor(0xffff0000);
			canvas.drawRect(10, 10, 110, 60, fill);

			Paint stroke = new Paint();
			stroke.setColor(0xff0000ff);
			stroke.setStyle(Paint.Style.STROKE);
			stroke.setStrokeWidth(4);
			canvas.drawLine(0, 0, 100, 100, stroke);
			canvas.drawLines(new float[] {0, 120, 50, 120, 50, 130, 100, 130}, stroke);

			Matrix matrix = new Matrix();
			matrix.preScale(2, 2, 10, 10);
			matrix.preRotate(90, 50, 50);
			canvas.save();
			canvas.concat(matrix);
			canvas.drawRect(0, 0, 20, 10, fill);
			canvas.restore();

			Paint copy = new Paint(fill);
			fill.setColor(0xff00ff00);
			canvas.drawRect(120, 10, 170, 60, copy);

			Paint blue = new Paint();
			blue.setColor(0xff0000ff);
			canvas.save();
			canvas.clipRect(180, 10, 200, 30);
			canvas.drawRect(170, 0, 210, 40, blue);
			canvas.restore();
			canvas.save();
			canvas.clipRect(220, 10, 240, 30, Region.Op.DIFFERENCE);
			canvas.drawRect(210, 0, 250, 40, blue);
			canvas.restore();
			canvas.drawBitmap(photo, null, new Rect(260, 0, 324, 64), null);

			// a paint with an xfermode draws no rect (ATLCanvas.drawRect), its copy neither
			Paint clear = new Paint(fill);
			clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			canvas.drawRect(0, 140, 10, 150, new Paint(clear));

			Paint text = new Paint();
			text.setTextSize(30);
			text.setTypeface(Typeface.DEFAULT_BOLD);
			canvas.drawText("Dine bold", 10, 190, text);
			Paint serif = new Paint(text);
			serif.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
			canvas.drawText("serif italic", 10, 230, serif);
			Paint mono = new Paint(text);
			mono.setTypeface(Typeface.MONOSPACE);
			mono.setTextAlign(Paint.Align.CENTER);
			canvas.drawText("mono, centered", 160, 270, mono);
			Paint outline = new Paint(stroke);
			outline.setStrokeWidth(1);
			outline.setTextSize(30);
			canvas.drawText("outline", 10, 310, outline);
			StaticLayout layout = new StaticLayout("two lines from a Layout", new TextPaint(text), 300, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
			canvas.save();
			canvas.translate(330, 160);
			layout.draw(canvas);
			canvas.restore();

			draws++;
			Log.i(TAG, "widgets draw layout lines " + layout.getLineCount());
			Log.i(TAG, "widgets draw " + draws);
			if (draws == 1) {
				post(new Runnable() {
					public void run() {
						invalidate();
					}
				});
			} else if (draws == 2) {
				post(new Runnable() {
					public void run() {
						afterLayout(new Runnable() {
							public void run() {
								hide();
							}
						});
					}
				});
			} else if (draws == 3) {
				post(new Runnable() {
					public void run() {
						Log.i(TAG, "widgets draw done");
						done();
					}
				});
			}
		}

		private void hide() {
			Log.i(TAG, "widgets draw hidden");
			setVisibility(View.INVISIBLE);
			invalidate();
			afterLayout(new Runnable() {
				public void run() {
					Log.i(TAG, "widgets draw shown");
					setVisibility(View.VISIBLE);
				}
			});
		}

		// next after a layout pass at the same size, and after that pass's draws
		private void afterLayout(final Runnable next) {
			getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				public void onGlobalLayout() {
					getViewTreeObserver().removeOnGlobalLayoutListener(this);
					post(next);
				}
			});
			requestLayout();
		}
	}

	protected void done() {
		finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		root = new FrameLayout(this);
		root.addView(new DrawView(this));
		View scrolled = new View(this);
		scrolled.setBackgroundResource(R.drawable.shared_selector);
		scrolled.scrollTo(0, 10);
		root.addView(scrolled, new FrameLayout.LayoutParams(40, 40, Gravity.BOTTOM));
		setContentView(root);
	}
}
