package dev.dine.nav;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

/* Logs each lifecycle callback as "nav: <name> <callback>", and the window insets its content gets:
 * "nav: <name> insets" and px for system gestures, mandatory gestures, navigation bars. */
public abstract class LoggingActivity extends Activity {
	private final String name;

	LoggingActivity(String name) {
		this.name = name;
	}

	private void log(String callback) {
		Log.i("nav", "nav: " + name + " " + callback);
	}

	private static String px(Insets insets) {
		return insets.left + " " + insets.top + " " + insets.right + " " + insets.bottom;
	}

	View content;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		content = new TextView(this);
		content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
			public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
				log("insets " + px(insets.getSystemGestureInsets()) + " " + px(insets.getMandatorySystemGestureInsets()) + " " + px(insets.getInsets(WindowInsets.Type.navigationBars())));
				return insets;
			}
		});
		setContentView(content);
		log("onCreate");
	}

	@Override
	protected void onStart() {
		super.onStart();
		log("onStart");
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		log("onRestart");
	}

	@Override
	protected void onResume() {
		super.onResume();
		log("onResume");
	}

	@Override
	protected void onPause() {
		super.onPause();
		log("onPause");
	}

	@Override
	protected void onStop() {
		super.onStop();
		log("onStop");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		log("onDestroy");
	}
}
