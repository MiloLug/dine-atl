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
	public void setSystemBarsBehavior(int behavior) {
		window.setBarsBehavior(behavior);
	}
	public int getSystemBarsBehavior() {
		return window.getBarsBehavior();
	}
	// the bars only: the IME follows the focused field
	public void show(@InsetsType int types) {
		window.setControllerHidden(types & (WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()), false);
	}
	public void hide(@InsetsType int types) {
		window.setControllerHidden(types & (WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()), true);
	}
}
