package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

// the window's brightness while it shows, the screen's own while it doesn't (Home, then finished)
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
}
