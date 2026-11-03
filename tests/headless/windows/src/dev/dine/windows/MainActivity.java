package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

// the window's brightness while it shows, then the screen's own again
public class MainActivity extends Activity {
	private static final String TAG = "windows";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("windows");
		setContentView(text);
		final WindowManager.LayoutParams params = getWindow().getAttributes();
		params.screenBrightness = 0.5f;
		getWindow().setAttributes(params);
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			public void run() {
				params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
				getWindow().setAttributes(params);
				Log.i(TAG, "windows brightness reset");
			}
		}, 500);
	}
}
