package dev.dine.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

/* A view drawn through Canvas, for run-draw.sh: headless compares its display lists with
 * draw.displaylist, gtk draws it. It redraws after invalidate(), not after a layout at the same
 * size, and after showing again from INVISIBLE with an invalidate() while hidden; then it finishes. */
public class DrawActivity extends Activity {
	private static final String TAG = "widgets";

	private class DrawView extends View {
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

			// a paint with an xfermode draws no rect (ATLCanvas.drawRect), its copy neither
			Paint clear = new Paint(fill);
			clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			canvas.drawRect(0, 140, 10, 150, new Paint(clear));

			draws++;
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
						finish();
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new DrawView(this));
	}
}
