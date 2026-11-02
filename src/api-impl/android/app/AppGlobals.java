package android.app;

public class AppGlobals {

	public static Application getInitialApplication() {
		return ActivityThread.currentApplication();
	}
}
