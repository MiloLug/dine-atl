package dev.dine.views;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/* Drops views, a dialog and a drawable and has them collected: during onCreate, and after the first
 * layout for a view that was laid out, with a layout pass after its free. Finishing stops the loop. */
public class MainActivity extends Activity {
	private static final String TAG = "views";

	private Handler main;
	private LinearLayout content;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		main = new Handler(Looper.getMainLooper());
		content = build();
		collect();
		removeWhenLaidOut();
	}

	// in a method of its own: the interpreter keeps every register of a live frame reachable
	private LinearLayout build() {
		setContentView(column("x", "y"));
		LinearLayout content = column("a", "b", "c", "d");
		View b = content.getChildAt(1);
		content.removeView(b);
		content.addView(b, 0);
		content.removeView(content.getChildAt(3)); // d
		setContentView(content); // drops x and y's column
		new AlertDialog.Builder(this).setMessage("never shown").create();
		new Drawable() { // draws itself: the backend keeps a record for it
			public void draw(Canvas canvas) {}
			public void setAlpha(int alpha) {}
			public void setColorFilter(ColorFilter filter) {}
			public int getOpacity() {
				return PixelFormat.TRANSLUCENT;
			}
		};
		return content;
	}

	private LinearLayout column(String... texts) {
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		for (String text : texts) {
			TextView view = new TextView(this);
			view.setText(text);
			column.addView(view);
		}
		return column;
	}

	// the frees are posted to the main loop: they run after this callback
	private static void collect() {
		for (int i = 0; i < 2; i++) {
			Runtime.getRuntime().gc();
			System.runFinalization();
		}
	}

	private void removeWhenLaidOut() {
		if (content.getWidth() == 0) {
			main.postDelayed(new Runnable() {
				public void run() {
					removeWhenLaidOut();
				}
			}, 10);
			return;
		}
		removeC();
		collect();
		// delayed, so the queued layout pass and the frees run first: this pass walks the tree after c's free
		main.postDelayed(new Runnable() {
			public void run() {
				content.requestLayout();
				main.postDelayed(new Runnable() {
					public void run() {
						Log.i(TAG, "views ui mode type " + (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK));
						Log.i(TAG, "views done");
						finish();
					}
				}, 10);
			}
		}, 10);
	}

	// out of the caller's frame, like build
	private void removeC() {
		content.removeView(content.getChildAt(2));
	}
}
