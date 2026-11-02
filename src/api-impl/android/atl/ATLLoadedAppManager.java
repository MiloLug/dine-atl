package android.atl;

import android.annotation.Nullable;
import android.content.pm.PackageParser;
import android.util.Slog;
import java.util.HashMap;

public final class ATLLoadedAppManager {
	private static final String TAG = "ATLLoadedAppManager";
	private static final HashMap<String, ATLLoadedApp> loaded_apps = new HashMap<>();
	private static final Object load_lock = new Object();

	private ATLLoadedAppManager() {}

	@Nullable
	public static PackageParser.Package getPackageFromPackageName(String packageName) {
		ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
		if (app.pkg.packageName.equals(packageName)) {
			return app.pkg;
		}
		if ("android".equals(packageName) || "atl".equals(packageName)) {
			return ATLLoadedApp.getSystemApplication().pkg;
		}
		app = loaded_apps.get(packageName);
		if (app != null) {
			return app.pkg;
		}
		return loadPackage(packageName);
	}

	@Nullable
	public static ATLLoadedApp peekAppFromPackageName(String packageName) {
		ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
		if (app.pkg.packageName.equals(packageName)) {
			return app;
		}
		if ("android".equals(packageName) || "atl".equals(packageName)) {
			return ATLLoadedApp.getSystemApplication();
		}
		return loaded_apps.get(packageName);
	}

	@Nullable
	public static ATLLoadedApp getAppFromPackageName(String packageName) {
		ATLLoadedApp app = peekAppFromPackageName(packageName);
		if (app != null) {
			return app;
		}
		return loadApplication(packageName);
	}

	private static PackageParser.Package loadPackage(String packageName) {
		return ATLRegistry.get().pkg(packageName);
	}

	private static ATLLoadedApp loadApplication(String packageName) {
		ATLLoadedApp app;
		synchronized (load_lock) {
			app = loaded_apps.get(packageName);
			if (app != null) {
				return app;
			}
			ATLRegistry.Entry entry = ATLRegistry.get().entry(packageName);
			if (entry == null)
				return null;
			Slog.i(TAG, "Attempting to load " + packageName + " into " + ATLLoadedApp.getPrimaryApplication().pkg.packageName);
			app = entry.row.generated ? ATLGStub.load(entry.pkg) : ATLLoadedApp.load(entry.pkg);
			loaded_apps.put(packageName, app);
		}
		return app;
	}
}
