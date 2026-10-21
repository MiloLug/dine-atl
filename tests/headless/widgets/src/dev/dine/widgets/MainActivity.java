package dev.dine.widgets;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;

/* Stays open for checks by eye; the ImageView's drawable doesn't draw yet, only its grey background.
 * The scrolling rows take input and log what nothing draws before M5 (run-widgets.sh). */
public class MainActivity extends Activity {
	private static final String TAG = "widgets";
	private TextView excluded;

	private int dp(int value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		column.setKeepScreenOn(true);
		final TextView state = new TextView(this);
		state.setText("widgets fullscreen");
		column.addView(state);
		ProgressBar spinner = new ProgressBar(this);
		spinner.setIndeterminate(true);
		column.addView(spinner);
		ProgressBar bar = new ProgressBar(this);
		bar.setIndeterminate(false);
		bar.setMax(100);
		bar.setProgress(50);
		column.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		ImageView image = new ImageView(this);
		image.setBackgroundColor(0xFF808080);
		image.setImageDrawable(new ColorDrawable(0xFF00C000));
		column.addView(image, new LinearLayout.LayoutParams(dp(96), dp(96)));
		column.addView(rows(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)));
		setContentView(column);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			public void run() {
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				state.setText("widgets windowed");
				Log.i(TAG, "widgets fullscreen off");
			}
		}, 5000);
	}

	private View rows() {
		ScrollView scroll = new ScrollView(this) {
			@Override
			protected void onScrollChanged(int l, int t, int oldl, int oldt) {
				super.onScrollChanged(l, t, oldl, oldt);
				Log.i(MainActivity.TAG, "widgets scroll " + l + " " + t);
				if (t == getChildAt(0).getHeight() + getPaddingTop() + getPaddingBottom() - getHeight())
					Log.i(MainActivity.TAG, "widgets scroll end");
			}
		};
		// run-widgets.sh checks that a drag stops at Java's end, padding included
		scroll.setPadding(0, 0, 0, dp(16));
		LinearLayout rows = new LinearLayout(this);
		rows.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(rows);

		TextView tap = row(rows, "tap", 48);
		tap.setOnClickListener(clickLogger("tap"));
		tap.setBackground(selector());

		TextView hold = row(rows, "hold", 48);
		hold.setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View v) {
				Log.i(TAG, "widgets longclick hold");
				return true;
			}
		});

		row(rows, "one", 48).setOnClickListener(clickLogger("one"));

		excluded = row(rows, "x", 40);
		excluded.setBackgroundColor(0xFF2196F3);
		excluded.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, dp(4000), dp(40))));

		rows.addView(new View(this), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(600)));
		return scroll;
	}

	private TextView row(LinearLayout rows, final String name, int height) {
		TextView row = new TextView(this);
		row.setText(name);
		row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				Log.i(TAG, "widgets focus " + name + " " + hasFocus);
			}
		});
		rows.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height)));
		return row;
	}

	private View.OnClickListener clickLogger(final String name) {
		return new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "widgets click " + name);
			}
		};
	}

	// logs the child it shows each time that changes
	private StateListDrawable selector() {
		StateListDrawable selector = new StateListDrawable() {
			private int shown;

			@Override
			protected boolean onStateChange(int[] state) {
				boolean changed = super.onStateChange(state);
				int color = getCurrent() instanceof ColorDrawable ? ((ColorDrawable)getCurrent()).getColor() : 0;
				if (color != shown) {
					shown = color;
					Log.i(TAG, "widgets selector " + Integer.toHexString(color));
				}
				return changed;
			}
		};
		selector.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(0xFF00C000));
		selector.addState(new int[0], new ColorDrawable(0xFF808080));
		return selector;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_T) {
			Log.i(TAG, "widgets key " + keyCode);
			excluded.setTranslationY(excluded.getTranslationY() + dp(40));
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "widgets shown");
	}
}
