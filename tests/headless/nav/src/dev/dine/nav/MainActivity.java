package dev.dine.nav;

import android.content.Intent;

/* A: starts B on its first onResume, so Back has an activity to finish before the task root. */
public class MainActivity extends LoggingActivity {
	private static boolean startedSecond;

	public MainActivity() {
		super("A");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!startedSecond) {
			startedSecond = true;
			startActivity(new Intent(this, SecondActivity.class));
		}
	}
}
