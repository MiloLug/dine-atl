package android.atl;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageParser;
import android.content.res.Resources;
import dalvik.system.PathClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * The Play Services stand-ins: preinstalled registry rows (ATLRegistry) whose code is gstub.jar.
 * An installed package of the same name (microG) replaces its row.
 */
final class ATLGStub {
	static final List<String> PACKAGES = Arrays.asList(
	    "com.google.android.gms", "com.google.android.gsf", "com.android.vending");

	// These values were extracted from MicroG.
	private static final String GMS_VERSION = "25.09.32";
	private static final String VENDING_VERSION = "40.2.26";
	private static final int GMS_VERSION_CODE = 250932030;
	private static final int VENDING_VERSION_CODE = 84022630;
	private static final int GOOGLE_PLAY_SERVICES_VERSION_INT = 12451000;

	private static PathClassLoader classLoader;

	static PackageParser.Package makePackage(String packageName) {
		PackageParser.Package pkg = new PackageParser.Package(packageName);
		pkg.applicationInfo.metaData.putInt(
		    "com.google.android.gms.version",
		    GOOGLE_PLAY_SERVICES_VERSION_INT);
		pkg.applicationInfo.sourceDir = ATLPaths.gstub_jar.getPath();
		ATLSigHelper.addGMSSignatures(pkg);
		pkg.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
		switch (packageName) {
			case "com.google.android.gms":
				pkg.mVersionName = GMS_VERSION;
				pkg.mVersionCode = GMS_VERSION_CODE;
				break;
			case "com.android.vending":
				pkg.mVersionName = VENDING_VERSION;
				pkg.mVersionCode = VENDING_VERSION_CODE;
				break;
			case "com.google.android.gsf":
				break;
			default:
				throw new IllegalArgumentException("Invalid package name: " + packageName);
		}
		return pkg;
	}

	static synchronized ATLLoadedApp load(PackageParser.Package pkg) {
		if (classLoader == null)
			classLoader = new PathClassLoader(ATLPaths.gstub_jar.getPath(), null, ATLGStub.class.getClassLoader());
		return new ATLLoadedApp(Resources.getSystem(), classLoader, pkg);
	}
}
