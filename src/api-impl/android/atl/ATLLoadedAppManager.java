package android.atl;

import android.annotation.Nullable;
import android.content.pm.PackageParser;
import java.util.HashMap;

/* The apps loaded into this VM, by package: the ones a host runs and the ones only loaded for a
 * package context or a service. */
public final class ATLLoadedAppManager {
	private static final HashMap<String, ATLLoadedApp> loaded_apps = new HashMap<>();

	private ATLLoadedAppManager() {}

	@Nullable
	public static PackageParser.Package getPackageFromPackageName(String packageName) {
		ATLLoadedApp app = peekAppFromPackageName(packageName);
		return app != null ? app.pkg : ATLRegistry.get().pkg(packageName);
	}

	@Nullable
	public static ATLLoadedApp peekAppFromPackageName(String packageName) {
		if ("android".equals(packageName) || "atl".equals(packageName)) {
			return ATLLoadedApp.getSystemApplication();
		}
		synchronized (loaded_apps) {
			return loaded_apps.get(packageName);
		}
	}

	@Nullable
	public static ATLLoadedApp getAppFromPackageName(String packageName) {
		ATLLoadedApp app = peekAppFromPackageName(packageName);
		if (app != null) {
			return app;
		}
		return loadApplication(packageName);
	}

	public static ATLLoadedApp[] loaded() {
		synchronized (loaded_apps) {
			return loaded_apps.values().toArray(new ATLLoadedApp[0]);
		}
	}

	private static ATLLoadedApp loadApplication(String packageName) {
		synchronized (loaded_apps) {
			ATLLoadedApp app = loaded_apps.get(packageName);
			if (app != null) {
				return app;
			}
			ATLRegistry.Entry entry = ATLRegistry.get().entry(packageName);
			if (entry == null)
				return null;
			app = entry.row.generated ? ATLGStub.load(entry.pkg) : ATLLoadedApp.load(entry.pkg);
			loaded_apps.put(packageName, app);
			return app;
		}
	}
}
