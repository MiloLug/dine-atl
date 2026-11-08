package android.view;

public class WindowManagerGlobal {
	private static final WindowManagerGlobal instance = new WindowManagerGlobal();

	public static WindowManagerGlobal getInstance() {
		return instance;
	}

	// the root view of the window the token stands for, as AOSP's: null for a token of no window
	public View getWindowView(android.os.IBinder token) {
		return token instanceof Window.Token ? ((Window.Token)token).window.atlDecor() : null;
	}
}
