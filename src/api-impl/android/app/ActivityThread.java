package android.app;

import android.app.Application;
import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.res.Configuration;
import java.util.List;

public class ActivityThread {
	public static ActivityThread currentActivityThread() {
		return new ActivityThread();
	}
	public static String currentPackageName() {
		ATLLoadedApp app = ATLLoadedApp.ofCurrentThread();
		return app != null ? app.pkg.packageName : null;
	}
	public static String currentProcessName() {
		return Application.getProcessName();
	}
	public static Application currentApplication() {
		ATLLoadedApp app = ATLLoadedApp.ofCurrentThread();
		return app != null ? app.getApplication() : null;
	}
	public Application getApplication() {
		return currentApplication();
	}

	public Configuration getConfiguration() {
		ATLLoadedApp app = ATLLoadedApp.ofCurrentThread();
		return app != null ? app.config : Context.sys_config;
	}
}
