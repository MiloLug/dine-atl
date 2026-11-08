package android.view;

import android.view.WindowInsets.Type.InsetsType;

public interface WindowInsetsController {
	int APPEARANCE_LIGHT_STATUS_BARS = 8;
	int BEHAVIOR_SHOW_BARS_BY_TOUCH = 0;
	int BEHAVIOR_SHOW_BARS_BY_SWIPE = 1;
	int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2;
	int BEHAVIOR_DEFAULT = 1;

	void setSystemBarsAppearance(int appearance, int mask);
	int getSystemBarsAppearance();
	void setSystemBarsBehavior(int behavior);
	int getSystemBarsBehavior();
	void show(@InsetsType int types);
	void hide(@InsetsType int types);
}
