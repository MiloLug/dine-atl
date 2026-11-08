package dev.dine.framework.windows;

import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;

// its manifest entry covers uiMode; it holds drawables from both caches, which a change prunes
public class ConfigModeActivity extends PlainActivity {
	Drawable box, tint;
	volatile int heard_night = -1;
	volatile boolean heard_on_main;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		box = getResources().getDrawable(R.drawable.box);
		tint = getResources().getDrawable(R.color.tint);
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		heard_night = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		heard_on_main = Looper.myLooper() == Looper.getMainLooper();
	}
}
