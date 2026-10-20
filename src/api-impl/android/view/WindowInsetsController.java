package android.view;

import android.view.WindowInsets.Type.InsetsType;

public interface WindowInsetsController {
	int APPEARANCE_LIGHT_STATUS_BARS = 8;

	void setSystemBarsAppearance(int appearance, int mask);
	int getSystemBarsAppearance();
	void setSystemBarsBehavior(int behavior);
	void show(@InsetsType int types);
	void hide(@InsetsType int types);
}
