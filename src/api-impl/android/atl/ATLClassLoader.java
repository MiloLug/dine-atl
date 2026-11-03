package android.atl;

import dalvik.system.PathClassLoader;
import java.lang.ref.CleanupDomain;

/** An app's class loader: its classes' objects are finalized and cleaned on the app's cleanup thread. */
final class ATLClassLoader extends PathClassLoader implements CleanupDomain.Owner {
	volatile CleanupDomain domain; // the app's while it runs (ATLLoadedApp.start)

	ATLClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
		super(dexPath, librarySearchPath, parent);
	}

	@Override
	public CleanupDomain cleanupDomain() {
		return domain;
	}
}
