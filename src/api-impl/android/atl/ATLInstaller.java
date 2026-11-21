package android.atl;

import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.Signature;
import android.os.Process;
import android.util.Slog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.BooleanSupplier;

/**
 * Every install and uninstall: the CLI's, a run by APK path, and an app's (PackageInstaller sessions,
 * ACTION_INSTALL_PACKAGE). It is the registry's only writer.
 */
public final class ATLInstaller {
	private static final String TAG = "ATLInstaller";
	public static final String FAKE_SIGNATURE_PERMISSION = "android.permission.FAKE_PACKAGE_SIGNATURE";
	private static int stagings;

	private ATLInstaller() {}

	/** APKs in a staging dir, the base one parsed; nothing is in the registry yet. */
	public static final class Staged {
		final File dir;
		final String[] files; // in dir, the base first
		public final PackageParser.Package pkg;

		Staged(File dir, String[] files, PackageParser.Package pkg) {
			this.dir = dir;
			this.files = files;
			this.pkg = pkg;
		}

		/** The hex signature the package asks to be reported with, or null. */
		public String fakeSignature() {
			return ATLInstaller.fakeSignature(pkg);
		}
	}

	public static final class Result {
		public final int status; // PackageInstaller.STATUS_*
		public final String packageName;
		public final String message;
		public final int legacyStatus; // PackageManager.INSTALL_*, for EXTRA_INSTALL_RESULT
		public final int uid;
		public final boolean replaced;

		Result(int status, String packageName, String message, int uid, boolean replaced) {
			this(status, legacyStatus(status), packageName, message, uid, replaced);
		}

		private Result(int status, int legacyStatus, String packageName, String message, int uid, boolean replaced) {
			this.status = status;
			this.legacyStatus = legacyStatus;
			this.packageName = packageName;
			this.message = message;
			this.uid = uid;
			this.replaced = replaced;
		}

		static Result failure(int status, String packageName, String message) {
			return new Result(status, packageName, message, -1, false);
		}

		static Result failure(int status, int legacyStatus, String packageName, String message) {
			return new Result(status, legacyStatus, packageName, message, -1, false);
		}

		// the default per status; a failure with a more precise code passes its own
		private static int legacyStatus(int status) {
			switch (status) {
				case PackageInstaller.STATUS_SUCCESS:
					return PackageManager.INSTALL_SUCCEEDED;
				case PackageInstaller.STATUS_FAILURE_CONFLICT:
					return PackageManager.INSTALL_FAILED_UPDATE_INCOMPATIBLE;
				case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
					return PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE;
				default:
					return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
			}
		}
	}

	/** The hex signature a package asks to be reported with, or null; a value that isn't hex is no request. */
	public static String fakeSignature(PackageParser.Package pkg) {
		if (!pkg.requestedPermissions.contains(FAKE_SIGNATURE_PERMISSION) || pkg.mAppMetaData == null)
			return null;
		String hex = pkg.mAppMetaData.getString("fake-signature");
		try {
			return hex != null && new Signature(hex).toByteArray().length > 0 ? hex : null;
		} catch (RuntimeException e) {
			Slog.w(TAG, pkg.packageName + "'s fake-signature isn't hex: ignored");
			return null;
		}
	}

	public static synchronized File newStagingDir() throws IOException {
		File staging = ATLRegistry.get().staging;
		for (;;) {
			File dir = new File(staging, Process.myPid() + "-" + (++stagings));
			if (dir.mkdir())
				return dir;
			if (!dir.exists())
				throw new IOException("can't create " + dir);
		}
	}

	/** The file a package's i-th APK is stored as, the base first; no name an app chose reaches the ':'-joined paths. */
	public static String storedName(int i) {
		return i == 0 ? "base.apk" : "split" + i + ".apk";
	}

	/** Copies the APKs (the base first) into a new staging dir and parses the base. */
	public static Staged stage(File... apks) throws IOException {
		File dir = newStagingDir();
		try {
			String[] files = new String[apks.length];
			for (int i = 0; i < apks.length; i++) {
				files[i] = storedName(i);
				try (InputStream in = new FileInputStream(apks[i])) {
					copy(in, new File(dir, files[i]));
				}
			}
			return stageDir(dir, files);
		} catch (IOException | RuntimeException e) {
			ATLRegistry.deleteTree(dir);
			throw e;
		}
	}

	/** files: the APKs already written into dir under their stored names (synced), the base first. */
	public static Staged stageDir(File dir, String[] files) throws IOException {
		return new Staged(dir, files, ATLRegistry.parse(new File(dir, files[0])));
	}

	/** Writes the stream to file and syncs it. */
	public static void copy(InputStream in, File file) throws IOException {
		try (FileOutputStream out = new FileOutputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			for (int n; (n = in.read(buffer)) > 0;)
				out.write(buffer, 0, n);
			out.getFD().sync();
		}
	}

	public static void abandon(Staged staged) {
		ATLRegistry.deleteTree(staged.dir);
	}

	/** A staging dir that never got staged. */
	public static void deleteDir(File dir) {
		ATLRegistry.deleteTree(dir);
	}

	/**
	 * Moves the staged APKs into apps/<pkg>/ and writes the row in one transaction, then deletes the
	 * replaced version. Every install refuses a provider authority another package holds; an
	 * app-started one also keeps Android's rules: the same signer, no downgrade. A failure takes
	 * away the APKs, wherever they are by then.
	 */
	public static Result commit(Staged staged, boolean appStarted, boolean allowFakeSignature, String installer) {
		ATLRegistry registry = ATLRegistry.get();
		PackageParser.Package pkg = staged.pkg;
		String name = pkg.packageName;
		// decoded before the write lock: a row in an older format is parsed again, which takes seconds under -Xint
		ATLRegistry.Entry before;
		try {
			before = appStarted ? registry.entry(name) : null;
		} catch (RuntimeException e) {
			abandon(staged);
			return Result.failure(PackageInstaller.STATUS_FAILURE, name, "can't read the installed package: " + e);
		}
		ATLRegistry.Row old = null;
		File versionDir = null; // the APKs' dir once they moved
		long serial = -1;
		Result result = null;
		synchronized (registry) {
			registry.db.beginTransactionNonExclusive();
			try {
				old = registry.row(name);
				result = refusal(registry, staged, old, before, appStarted);
				if (result == null) {
					serial = registry.nextSerial();
					File pkgDir = new File(registry.apps, name);
					pkgDir.mkdirs();
					File dir = new File(pkgDir, pkg.mVersionCode + "-" + serial);
					if (staged.dir.renameTo(dir)) {
						versionDir = dir;
						ATLRegistry.fsync(pkgDir);
						StringBuilder apks = new StringBuilder();
						for (String file : staged.files)
							apks.append(apks.length() == 0 ? "" : ":").append("apps/" + name + "/" + dir.getName() + "/" + file);
						int uid = old != null ? old.uid : registry.nextUid();
						long now = System.currentTimeMillis();
						boolean fakeSignature = allowFakeSignature && staged.fakeSignature() != null;
						registry.putRow(pkg, uid, apks.toString(), false, fakeSignature, installer,
						                old != null && !old.generated ? old.firstInstallTime : now, now, serial);
						new File(registry.dataDir(name), "lib").mkdirs();
						registry.db.setTransactionSuccessful();
						result = new Result(PackageInstaller.STATUS_SUCCESS, name, null, uid, old != null);
					} else {
						result = Result.failure(PackageInstaller.STATUS_FAILURE_STORAGE, name, "can't move the APK to " + dir);
					}
				}
			} catch (IOException e) {
				result = Result.failure(PackageInstaller.STATUS_FAILURE_STORAGE, name, e.toString());
			} catch (RuntimeException e) {
				result = Result.failure(PackageInstaller.STATUS_FAILURE, name, e.toString());
			} finally {
				// before the lock goes: after a rollback another process may take the same serial
				boolean failed = result == null || result.status != PackageInstaller.STATUS_SUCCESS;
				if (failed)
					ATLRegistry.deleteTree(versionDir != null ? versionDir : staged.dir);
				long written = serial;
				Result ended = end(registry, result, () -> {
					ATLRegistry.Row now = registry.row(name);
					return now != null && now.generation == written;
				});
				if (!failed && ended.status != PackageInstaller.STATUS_SUCCESS)
					ATLRegistry.deleteTree(versionDir);
				result = ended;
			}
			if (result.status != PackageInstaller.STATUS_SUCCESS)
				return result;
		}
		if (old != null && !old.generated)
			ATLRegistry.deleteTree(registry.file(old.apks[0]).getParentFile());
		Slog.i(TAG, "installed " + name + " " + pkg.mVersionName + " (" + pkg.mVersionCode + ") uid " + result.uid);
		return result;
	}

	/** Under the write lock: why Android refuses this install, or null. */
	private static Result refusal(ATLRegistry registry, Staged staged, ATLRegistry.Row old, ATLRegistry.Entry before, boolean appStarted) {
		PackageParser.Package pkg = staged.pkg;
		String name = pkg.packageName;
		if (appStarted && old != null && !old.generated) {
			// the lock holds the row: entry(old) is old's own package
			PackageParser.Package installed = before != null && before.row.generation == old.generation ? before.pkg : registry.entry(old).pkg;
			if (!sameSigners(installed.mSignatures, pkg.mSignatures))
				return Result.failure(PackageInstaller.STATUS_FAILURE_CONFLICT, name, "signatures differ from the installed package's");
			if (pkg.mVersionCode < old.versionCode)
				return Result.failure(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, name,
				                      "version " + pkg.mVersionCode + " is older than the installed " + old.versionCode);
		}
		// one package per authority; an update keeps its own
		for (PackageParser.Provider provider : pkg.providers) {
			if (provider.info.authority == null)
				continue;
			for (String authority : provider.info.authority.split(";")) {
				String holder = registry.authorityHolder(authority, name);
				if (holder != null)
					return Result.failure(PackageInstaller.STATUS_FAILURE_CONFLICT, PackageManager.INSTALL_FAILED_CONFLICTING_PROVIDER,
					                      name, "provider authority " + authority + " is held by " + holder);
			}
		}
		return null;
	}

	/**
	 * Ends the write transaction; result is null when an Error is on its way. A throw from the commit
	 * doesn't say whether it landed: landed reads the row.
	 */
	private static Result end(ATLRegistry registry, Result result, BooleanSupplier landed) {
		try {
			registry.db.endTransaction();
			return result;
		} catch (RuntimeException e) {
			if (result == null || result.status != PackageInstaller.STATUS_SUCCESS || landed.getAsBoolean())
				return result;
			return Result.failure(PackageInstaller.STATUS_FAILURE, result.packageName, "the commit failed: " + e);
		}
	}

	/** Android's rule: the same set of signing certificates; unsigned matches only unsigned. */
	public static boolean sameSigners(Signature[] a, Signature[] b) {
		HashSet<Signature> sa = new HashSet<>(a == null ? Arrays.<Signature>asList() : Arrays.asList(a));
		HashSet<Signature> sb = new HashSet<>(b == null ? Arrays.<Signature>asList() : Arrays.asList(b));
		return sa.equals(sb);
	}

	/**
	 * Removes the row, the APKs and the app's data. Its dirs move to the trash inside the transaction,
	 * so a concurrent install of the same package never loses its files; a failure moves them back,
	 * and so does the registry's sweep after a kill before the commit. A Play Services name gets its
	 * generated row back.
	 */
	public static Result uninstall(String name) {
		ATLRegistry registry = ATLRegistry.get();
		File apps = new File(registry.apps, name);
		File data = registry.dataDir(name);
		File trash = null;
		long serial = -1;
		Result result = null;
		synchronized (registry) {
			registry.db.beginTransactionNonExclusive();
			try {
				ATLRegistry.Row old = registry.row(name);
				if (old == null) {
					result = Result.failure(PackageInstaller.STATUS_FAILURE, name, "not installed");
				} else if (old.generated) {
					result = Result.failure(PackageInstaller.STATUS_FAILURE_BLOCKED, name, "a preinstalled package");
				} else {
					serial = registry.nextSerial();
					File dir = new File(registry.trash, name + "-" + serial);
					if (dir.mkdir()) {
						trash = dir;
						result = toTrash(apps, data, trash, name);
						if (result == null) {
							registry.deleteRow(name);
							if (ATLGStub.PACKAGES.contains(name)) {
								long now = System.currentTimeMillis();
								registry.putRow(ATLGStub.makePackage(name), old.uid, null, true, false, null, now, now, serial);
							}
							registry.db.setTransactionSuccessful();
							result = new Result(PackageInstaller.STATUS_SUCCESS, name, null, old.uid, false);
						}
					} else {
						result = Result.failure(PackageInstaller.STATUS_FAILURE_STORAGE, name, "can't create " + dir);
					}
				}
			} catch (RuntimeException e) {
				result = Result.failure(PackageInstaller.STATUS_FAILURE, name, e.toString());
			} finally {
				// before the lock goes: after a rollback another process may install the package
				boolean failed = result == null || result.status != PackageInstaller.STATUS_SUCCESS;
				if (failed && trash != null)
					ATLRegistry.putBack(trash, apps, data);
				long written = serial;
				Result ended = end(registry, result, () -> {
					ATLRegistry.Row now = registry.row(name);
					return now == null || (now.generated && now.generation == written);
				});
				if (!failed && ended.status != PackageInstaller.STATUS_SUCCESS)
					ATLRegistry.putBack(trash, apps, data);
				result = ended;
			}
			if (result.status != PackageInstaller.STATUS_SUCCESS)
				return result;
		}
		ATLRegistry.deleteTree(trash);
		Slog.i(TAG, "uninstalled " + name);
		return result;
	}

	// on a failure the caller moves back what moved (ATLRegistry.putBack)
	private static Result toTrash(File apps, File data, File trash, String name) {
		if (apps.exists() && !apps.renameTo(new File(trash, "apps")))
			return Result.failure(PackageInstaller.STATUS_FAILURE_STORAGE, name, "can't move " + apps + " to the trash");
		if (data.exists() && !data.renameTo(new File(trash, "data")))
			return Result.failure(PackageInstaller.STATUS_FAILURE_STORAGE, name, "can't move " + data + " to the trash");
		return null;
	}

	/**
	 * A host's start of its app, after VM start: a run by package looks the row up; a run by APK path
	 * installs the APKs (with no prompt) unless a row already holds these exact files. Returns the
	 * row's app, which native_app (struct atl_app) runs from here on; a package runs once per VM.
	 */
	public static ATLLoadedApp prepareApp(String[] apks, String packageName, long native_app) throws IOException {
		ATLRegistry registry = ATLRegistry.get();
		ATLRegistry.Row row;
		if (packageName != null) {
			row = registry.row(packageName);
		} else {
			File[] files = files(apks);
			row = registry.rowHolding(files);
			if (row == null) {
				Result result = commit(stage(files), false, false, null);
				if (result.status != PackageInstaller.STATUS_SUCCESS)
					throw new IOException("can't install " + apks[0] + ": " + result.message);
				row = registry.row(result.packageName);
			}
		}
		ATLRegistry.Entry entry = row != null ? registry.entry(row) : null;
		if (entry == null || entry.row.generated)
			throw new IllegalArgumentException((packageName != null ? packageName : apks[0]) + " is not installed");
		PackageParser.Package pkg = entry.pkg;
		ATLLoadedApp app = ATLLoadedAppManager.getAppFromPackageName(pkg.packageName);
		synchronized (app) {
			if (app.native_app != 0)
				throw new IllegalStateException(pkg.packageName + " already runs in this process");
			app.native_app = native_app;
		}
		return app;
	}

	static boolean sameFile(File a, File b) {
		return native_same_file(a.getPath(), b.getPath());
	}

	private static File[] files(String[] paths) {
		File[] files = new File[paths.length];
		for (int i = 0; i < paths.length; i++)
			files[i] = new File(paths[i]);
		return files;
	}

	private static native boolean native_same_file(String a, String b);

	/** The hosts' --install: the APKs are one package, the base first. Exit status for the process. */
	public static int cliInstall(String[] apks, boolean allowFakeSignature) {
		// install runs once per version: the same bytes again change nothing, unless to grant the fake signature
		ATLRegistry.Row held = ATLRegistry.get().rowHolding(files(apks));
		ATLRegistry.Entry heldEntry = held != null ? ATLRegistry.get().entry(held) : null;
		if (heldEntry != null && (!allowFakeSignature || held.fakeSignature || fakeSignature(heldEntry.pkg) == null)) {
			System.out.println("Success: " + held.name + " uid " + held.uid + " (already installed)");
			return 0;
		}
		Result result;
		try {
			result = commit(stage(files(apks)), false, allowFakeSignature, null);
		} catch (IOException e) {
			result = Result.failure(PackageInstaller.STATUS_FAILURE_INVALID, null, e.getMessage());
		}
		return report(result);
	}

	/** The hosts' --uninstall. Exit status for the process. */
	public static int cliUninstall(String name) {
		return report(uninstall(name));
	}

	private static int report(Result result) {
		if (result.status == PackageInstaller.STATUS_SUCCESS) {
			System.out.println("Success: " + result.packageName + " uid " + result.uid);
			return 0;
		}
		System.out.println("Failure [status " + result.status + "]: " + result.packageName + ": " + result.message);
		return 1;
	}
}
