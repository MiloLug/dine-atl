package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

// the crash comes on the way down, inside the finish
public class PauseActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CreateActivity.logCheckJni();
		new Handler(Looper.getMainLooper()).post(this::finish);
	}

	@Override
	protected void onPause() {
		super.onPause();
		throw new IllegalStateException("crash in onPause");
	}
}
