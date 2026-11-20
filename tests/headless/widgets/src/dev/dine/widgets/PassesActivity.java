package dev.dine.widgets;

import android.app.Activity;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewTreeObserver;

/* The frames of AOSP's ViewRootImpl, one stage after another on a drawing view. 1: an
 * OnPreDrawListener cancels the first three frames: it draws with the fourth. 2: an invalidate with
 * nothing else asked cancels one frame the same way. 3: a layout asked for from a global layout
 * call is done in the same pass, ahead of the next frame's callbacks. 4: an OnGlobalLayoutListener
 * asks for another layout in each of its calls for 500 ms: the passes come at the frame rate, and the
 * message posted for the end of that time still runs. Then it finishes. */
public class PassesActivity extends Activity {
	private static final String TAG = "widgets";

	private int preDraws, invalidatePreDraws;
	private int stage = 1;

	private final class Drawing extends View {
		Drawing(Activity activity) {
			super(activity);
		}

		@Override
		public void onDraw(Canvas canvas) {
			canvas.drawColor(0xff00ff00);
			if (stage == 1) {
				Log.i(TAG, "passes drawn after " + preDraws + " calls");
				stage = 2;
				post(new Runnable() {
					public void run() {
						invalidateOnly(Drawing.this);
					}
				});
			} else if (stage == 2) {
				Log.i(TAG, "passes invalidate drawn after " + invalidatePreDraws + " calls");
				stage = 3;
				post(new Runnable() {
					public void run() {
						secondRound(Drawing.this);
					}
				});
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final View view = new Drawing(this);
		setContentView(view);
		view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			public boolean onPreDraw() {
				boolean proceed = ++preDraws > 3;
				Log.i(TAG, "passes predraw " + preDraws + (proceed ? " go" : " cancel"));
				if (proceed)
					view.getViewTreeObserver().removeOnPreDrawListener(this);
				return proceed;
			}
		});
	}

	// a draw pass with no layout asked for: the listener cancels the first
	private void invalidateOnly(final View view) {
		view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			public boolean onPreDraw() {
				boolean proceed = ++invalidatePreDraws > 1;
				Log.i(TAG, "passes invalidate predraw " + invalidatePreDraws + (proceed ? " go" : " cancel"));
				if (proceed)
					view.getViewTreeObserver().removeOnPreDrawListener(this);
				return proceed;
			}
		});
		view.invalidate();
	}

	// the first global layout call asks for a layout and for a frame callback: the layout comes first
	private void secondRound(final View view) {
		final int[] calls = new int[1];
		view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				if (++calls[0] == 1) {
					Log.i(TAG, "passes round 1");
					Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
						public void doFrame(long frameTimeNanos) {
							Log.i(TAG, "passes frame");
						}
					});
					view.requestLayout();
				} else {
					Log.i(TAG, "passes round 2");
					view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					view.post(new Runnable() {
						public void run() {
							layoutLoop(view);
						}
					});
				}
			}
		});
		view.requestLayout();
	}

	private void layoutLoop(final View view) {
		final long start = SystemClock.uptimeMillis();
		final int[] passes = new int[1];
		final ViewTreeObserver.OnGlobalLayoutListener again = new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				passes[0]++;
				view.requestLayout();
			}
		};
		view.getViewTreeObserver().addOnGlobalLayoutListener(again);
		view.requestLayout();
		view.postDelayed(new Runnable() {
			public void run() {
				view.getViewTreeObserver().removeOnGlobalLayoutListener(again);
				Log.i(TAG, "passes layouts " + passes[0] + " in " + (SystemClock.uptimeMillis() - start) + " ms");
				finish();
			}
		}, 500);
	}
}
