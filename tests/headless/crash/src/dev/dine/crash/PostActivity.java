package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class PostActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		new Handler(Looper.getMainLooper()).post(() -> {
			throw new IllegalStateException("crash in a posted Runnable");
		});
	}
}
