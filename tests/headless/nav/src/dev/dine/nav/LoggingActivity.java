package dev.dine.nav;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/* Logs each lifecycle callback as "nav: <name> <callback>". */
public abstract class LoggingActivity extends Activity {
	private final String name;

	LoggingActivity(String name) {
		this.name = name;
	}

	private void log(String callback) {
		Log.i("nav", "nav: " + name + " " + callback);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
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
