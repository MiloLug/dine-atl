package android.view;

import android.view.WindowInsets.Type.InsetsType;

public class InsetsController implements WindowInsetsController {
	private final Window window;

	InsetsController(Window window) {
		this.window = window;
	}

	public void setSystemBarsAppearance(int appearance, int mask) {
		if ((mask & APPEARANCE_LIGHT_STATUS_BARS) != 0)
			window.setLightStatusBar((appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0, true);
	}
	public int getSystemBarsAppearance() {
		return window.isLightStatusBar() ? APPEARANCE_LIGHT_STATUS_BARS : 0;
	}
	public void setSystemBarsBehavior(int behavior) {}
	public void show(@InsetsType int types) {}
	public void hide(@InsetsType int types) {}
}
