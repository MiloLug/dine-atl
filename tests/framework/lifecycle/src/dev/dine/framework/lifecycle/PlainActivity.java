package dev.dine.framework.lifecycle;

import android.app.Activity;
import android.os.Bundle;

public class PlainActivity extends Activity {
	static volatile int created; // in this app
	volatile int focusGains;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		created++;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			focusGains++;
	}
}
