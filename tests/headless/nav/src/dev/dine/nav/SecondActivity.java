package dev.dine.nav;

import android.os.Bundle;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/* B: clears the theme's FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS before the theme is read, so its color
 * stays unused until its first onResume draws it, light. */
public class SecondActivity extends LoggingActivity {
	private boolean resumed;

	public SecondActivity() {
		super("B");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		super.onCreate(savedInstanceState);
		getWindow().setStatusBarColor(0xff2196f3);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!resumed) {
			resumed = true;
			getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		}
	}
}
