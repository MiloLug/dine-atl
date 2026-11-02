package dev.dine.crash;

import android.app.Activity;

public class ResumeActivity extends Activity {
	@Override
	protected void onResume() {
		super.onResume();
		throw new IllegalStateException("crash in onResume");
	}
}
