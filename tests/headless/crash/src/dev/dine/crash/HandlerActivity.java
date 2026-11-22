package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

// the app's own default handler returns, where Android's ends the process
public class HandlerActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CreateActivity.logCheckJni();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable error) {
				Log.i("crash", "crash handled " + error.getMessage());
			}
		});
		throw new IllegalStateException("crash under a handler");
	}
}
