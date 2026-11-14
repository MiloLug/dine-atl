package dev.dine.nav;

import android.os.Bundle;

// X: finishes inside the callback its intent names
public class FinishingActivity extends LoggingActivity {
	public FinishingActivity() {
		super("X");
	}

	private void finishAt(String callback) {
		if (callback.equals(getIntent().getStringExtra("at")))
			finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		finishAt("create");
	}

	@Override
	protected void onStart() {
		super.onStart();
		finishAt("start");
	}

	@Override
	protected void onResume() {
		super.onResume();
		finishAt("resume");
	}
}
