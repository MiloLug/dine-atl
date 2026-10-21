package dev.dine.widgets;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/* Stays open for checks by eye; the ImageView's drawable doesn't draw yet, only its grey background. */
public class MainActivity extends Activity {
	private static final String TAG = "widgets";

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
		int side = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 96, getResources().getDisplayMetrics()));
		column.addView(image, new LinearLayout.LayoutParams(side, side));
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

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "widgets shown");
	}
}
