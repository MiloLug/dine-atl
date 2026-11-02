package android.atl;

import java.io.File;

public final class ATLPaths {
	/** $DINE_PREFIX or ~/.dine: the registry, apps/<pkg>/, data/<pkg>/ */
	public static final File prefix_dir;
	public static final File api_impl_jar;
	public static final File gstub_jar;
	public static final File ghax_jar;

	static {
		// The main executable set api-impl.jar as the first element of "java.class.path"
		String cp = System.getProperty("java.class.path");
		api_impl_jar = new File(cp.substring(0, cp.indexOf(':'))).getAbsoluteFile();
		// additional .jar files are always next to api-impl.jar both when installed and when running from builddir
		File extraJarsDirectory = ATLPaths.api_impl_jar.getParentFile();
		gstub_jar = new File(extraJarsDirectory, "gstub.jar");
		ghax_jar = new File(extraJarsDirectory, "ghax.jar");
		prefix_dir = new File(native_prefix_dir()).getAbsoluteFile();
	}

	private static native String native_prefix_dir();
}
