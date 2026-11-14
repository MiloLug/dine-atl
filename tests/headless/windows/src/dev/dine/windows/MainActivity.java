package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

/* The window's brightness while it shows, the screen's own while its task is in the back. A new
 * brightness set there reaches the host only once the activity shows again. */
public class MainActivity extends Activity {
	private static final String TAG = "windows";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("windows");
		setContentView(text);
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.screenBrightness = 0.5f;
		getWindow().setAttributes(params);
		Log.i(TAG, "windows brightness set");
	}

	@Override
	protected void onStop() {
		super.onStop();
		new Handler().post(new Runnable() {
			public void run() {
				WindowManager.LayoutParams params = getWindow().getAttributes();
				params.screenBrightness = 0.7f;
				getWindow().setAttributes(params);
				Log.i(TAG, "windows brightness set while stopped");
			}
		});
	}
}
