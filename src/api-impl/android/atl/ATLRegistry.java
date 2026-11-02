package android.atl;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageCodec;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.util.Slog;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The package registry: one SQLite database in the prefix that every app's PackageManager reads and
 * only ATLInstaller writes (notes/multi-app-support/design.md "Registry"). A row holds a package's
 * install settings and its parsed manifest as PackageCodec bytes; paths are relative to the prefix.
 */
public final class ATLRegistry {
	private static final String TAG = "ATLRegistry";
	static final int SCHEMA_VERSION = 1;
	static final int FIRST_UID = 10000; // Android's FIRST_APPLICATION_UID
	static final long STAGING_MAX_AGE_MS = 24 * 60 * 60 * 1000;

	public static final class Row {
		public final String name;
		public final int uid;
		public final int versionCode;
		public final String versionName;
		/** prefix-relative, the base APK first; null for a generated row */
		public final String[] apks;
		public final boolean generated;
		public final boolean fakeSignature;
		public final String installer;
		public final long firstInstallTime;
		public final long lastUpdateTime;
		public final long generation;

		Row(Cursor c) {
			name = c.getString(0);
			uid = c.getInt(1);
			versionCode = c.getInt(2);
			versionName = c.getString(3);
			apks = c.isNull(4) ? null : c.getString(4).split(":");
			generated = c.getInt(5) != 0;
			fakeSignature = c.getInt(6) != 0;
			installer = c.getString(7);
			firstInstallTime = c.getLong(8);
			lastUpdateTime = c.getLong(9);
			generation = c.getLong(10);
		}
	}

	private static final String ROW_COLUMNS = "name, uid, version_code, version_name, apks, generated, fake_signature, " +
	                                          "installer, first_install_time, last_update_time, generation";

	private static ATLRegistry instance;

	final File prefix;
	final File apps;
	final File data;
	final File staging;
	final File trash;
	final SQLiteDatabase db;
	private final HashMap<String, Decoded> decoded = new HashMap<>();

	private static final class Decoded {
		final long generation;
		final PackageParser.Package pkg;

		Decoded(long generation, PackageParser.Package pkg) {
			this.generation = generation;
			this.pkg = pkg;
		}
	}

	public static synchronized ATLRegistry get() {
		if (instance == null)
			instance = new ATLRegistry(ATLPaths.prefix_dir);
		return instance;
	}

	private ATLRegistry(File prefix) {
		this.prefix = prefix;
		apps = new File(prefix, "apps");
		data = new File(prefix, "data");
		staging = new File(apps, ".staging");
		trash = new File(prefix, ".trash");
		for (File dir : new File[] {apps, data, staging, trash})
			dir.mkdirs();
		// synchronous=FULL: a commit is on disk before the installer deletes the APK it replaced
		db = SQLiteDatabase.openDatabase(new File(prefix, "registry.sqlite").getPath(),
		                                 SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING |
		                                     SQLiteDatabase.NO_LOCALIZED_COLLATORS,
		                                 "FULL");
		db.beginTransactionNonExclusive();
		try {
			createSchema();
			sweep();
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	private void createSchema() {
		db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value)");
		db.execSQL("CREATE TABLE IF NOT EXISTS packages(name TEXT PRIMARY KEY, uid INTEGER NOT NULL UNIQUE, " +
		           "version_code INTEGER NOT NULL, version_name TEXT, apks TEXT, generated INTEGER NOT NULL, " +
		           "fake_signature INTEGER NOT NULL, installer TEXT, first_install_time INTEGER NOT NULL, " +
		           "last_update_time INTEGER NOT NULL, parse_version INTEGER NOT NULL, parsed BLOB NOT NULL, " +
		           "generation INTEGER NOT NULL)");
		db.execSQL("CREATE TABLE IF NOT EXISTS component_actions(pkg TEXT NOT NULL, kind TEXT NOT NULL, " +
		           "component TEXT NOT NULL, action TEXT NOT NULL)");
		db.execSQL("CREATE INDEX IF NOT EXISTS component_actions_action ON component_actions(action)");
		db.execSQL("CREATE INDEX IF NOT EXISTS component_actions_pkg ON component_actions(pkg)");
		db.execSQL("CREATE TABLE IF NOT EXISTS authorities(pkg TEXT NOT NULL, authority TEXT NOT NULL)");
		db.execSQL("CREATE INDEX IF NOT EXISTS authorities_authority ON authorities(authority)");
		Long version = meta("schema_version");
		if (version == null) {
			setMeta("schema_version", SCHEMA_VERSION);
			setMeta("next_uid", FIRST_UID);
			setMeta("next_serial", 1);
			long now = System.currentTimeMillis();
			for (String name : ATLGStub.PACKAGES)
				putRow(ATLGStub.makePackage(name), nextUid(), null, true, false, null, now, now, nextSerial());
		} else if (version != SCHEMA_VERSION) {
			throw new IllegalStateException("registry schema " + version + ", this build reads " + SCHEMA_VERSION);
		}
	}

	private Long meta(String key) {
		try (Cursor c = db.rawQuery("SELECT value FROM meta WHERE key = ?", new String[] {key})) {
			return c.moveToFirst() ? c.getLong(0) : null;
		}
	}

	private void setMeta(String key, long value) {
		db.execSQL("INSERT OR REPLACE INTO meta(key, value) VALUES(?, ?)", new Object[] {key, value});
	}

	/** Inside a write transaction. */
	int nextUid() {
		long uid = meta("next_uid");
		setMeta("next_uid", uid + 1);
		return (int)uid;
	}

	/** Inside a write transaction: unique across rows and writes, so it names version dirs and generations. */
	long nextSerial() {
		long serial = meta("next_serial");
		setMeta("next_serial", serial + 1);
		return serial;
	}

	/** Inside a write transaction. apks: prefix-relative, ':'-joined, base first. */
	void putRow(PackageParser.Package pkg, int uid, String apks, boolean generated, boolean fakeSignature,
	            String installer, long firstInstallTime, long lastUpdateTime, long generation) {
		String name = pkg.packageName;
		db.execSQL("INSERT OR REPLACE INTO packages(" + ROW_COLUMNS + ", parse_version, parsed) " +
		               "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
		           new Object[] {name, uid, pkg.mVersionCode, pkg.mVersionName, apks, generated ? 1 : 0,
		                         fakeSignature ? 1 : 0, installer, firstInstallTime, lastUpdateTime, generation,
		                         PackageCodec.PARSE_VERSION, PackageCodec.marshall(pkg)});
		deleteIndex(name);
		addActions(name, "activity", pkg.activities);
		addActions(name, "receiver", pkg.receivers);
		addActions(name, "service", pkg.services);
		addActions(name, "provider", pkg.providers);
		for (PackageParser.Provider provider : pkg.providers) {
			if (provider.info.authority == null)
				continue;
			for (String authority : provider.info.authority.split(";"))
				db.execSQL("INSERT INTO authorities(pkg, authority) VALUES(?, ?)", new Object[] {name, authority});
		}
	}

	/** Inside a write transaction. */
	void deleteRow(String name) {
		db.execSQL("DELETE FROM packages WHERE name = ?", new Object[] {name});
		deleteIndex(name);
	}

	private void deleteIndex(String name) {
		db.execSQL("DELETE FROM component_actions WHERE pkg = ?", new Object[] {name});
		db.execSQL("DELETE FROM authorities WHERE pkg = ?", new Object[] {name});
	}

	private void addActions(String name, String kind, List<? extends PackageParser.Component<? extends PackageParser.IntentInfo>> components) {
		for (PackageParser.Component<? extends PackageParser.IntentInfo> component : components) {
			for (PackageParser.IntentInfo intent : component.intents) {
				for (int i = 0; i < intent.countActions(); i++)
					db.execSQL("INSERT INTO component_actions(pkg, kind, component, action) VALUES(?, ?, ?, ?)",
					           new Object[] {name, kind, component.className, intent.getAction(i)});
			}
		}
	}

	public synchronized Row row(String name) {
		try (Cursor c = db.rawQuery("SELECT " + ROW_COLUMNS + " FROM packages WHERE name = ?", new String[] {name})) {
			return c.moveToFirst() ? new Row(c) : null;
		}
	}

	public synchronized List<Row> rows() {
		return rowsWhere("1", new String[0]);
	}

	public synchronized List<Row> rowsForUid(int uid) {
		return rowsWhere("uid = ?", new String[] {Integer.toString(uid)});
	}

	private List<Row> rowsWhere(String where, String[] args) {
		List<Row> rows = new ArrayList<>();
		try (Cursor c = db.rawQuery("SELECT " + ROW_COLUMNS + " FROM packages WHERE " + where + " ORDER BY name", args)) {
			while (c.moveToNext())
				rows.add(new Row(c));
		}
		return rows;
	}

	/** The installed row whose stored APKs are byte for byte these files, or null. */
	public synchronized Row rowHolding(File[] apks) {
		for (Row row : rowsWhere("generated = 0", new String[0])) {
			if (row.apks.length != apks.length)
				continue;
			boolean same = true;
			for (int i = 0; same && i < apks.length; i++)
				same = ATLInstaller.sameFile(file(row.apks[i]), apks[i]);
			if (same)
				return row;
		}
		return null;
	}

	/** Packages with a component of this kind (activity, receiver, service, provider) that has a filter for action. */
	public synchronized Set<String> packagesWithAction(String kind, String action) {
		Set<String> names = new HashSet<>();
		try (Cursor c = db.rawQuery("SELECT DISTINCT pkg FROM component_actions WHERE kind = ? AND action = ?",
		                            new String[] {kind, action})) {
			while (c.moveToNext())
				names.add(c.getString(0));
		}
		return names;
	}

	public synchronized String packageForAuthority(String authority) {
		try (Cursor c = db.rawQuery("SELECT pkg FROM authorities WHERE authority = ?", new String[] {authority})) {
			return c.moveToFirst() ? c.getString(0) : null;
		}
	}

	/** A row and its parsed package, read together, so that its fields and the package agree. */
	public static final class Entry {
		public final Row row;
		/** with this prefix's paths and the row's uid */
		public final PackageParser.Package pkg;

		Entry(Row row, PackageParser.Package pkg) {
			this.row = row;
			this.pkg = pkg;
		}
	}

	/** null when the package isn't installed */
	public synchronized Entry entry(String name) {
		Row row = row(name);
		return row == null ? null : entry(row);
	}

	public synchronized PackageParser.Package pkg(String name) {
		Entry entry = entry(name);
		return entry == null ? null : entry.pkg;
	}

	/**
	 * A row read earlier may have been replaced or removed since, here or by another process: then
	 * the entry is the package's current row, or null when the package is gone. A generation this
	 * process decoded before comes back from its cache as it was, removed or not.
	 */
	public synchronized Entry entry(Row row) {
		Decoded cached = decoded.get(row.name);
		if (cached != null && cached.generation == row.generation)
			return new Entry(row, cached.pkg);
		PackageParser.Package pkg;
		try (Cursor c = db.rawQuery("SELECT parse_version, parsed FROM packages WHERE name = ? AND generation = ?",
		                            new String[] {row.name, Long.toString(row.generation)})) {
			if (!c.moveToFirst())
				return current(row);
			pkg = c.getInt(0) == PackageCodec.PARSE_VERSION ? PackageCodec.unmarshall(c.getBlob(1)) : null;
		}
		if (pkg == null) {
			try {
				pkg = reparse(row);
			} catch (IOException e) {
				// another process replaced or removed the row, and its APK, after the read above
				if (generationGone(row))
					return current(row);
				throw new IllegalStateException("can't parse " + row.name + " again", e);
			}
		}
		fillPaths(pkg, row);
		decoded.put(row.name, new Decoded(row.generation, pkg));
		return new Entry(row, pkg);
	}

	private Entry current(Row stale) {
		Row row = row(stale.name);
		return row == null ? null : entry(row);
	}

	private boolean generationGone(Row row) {
		Row now = row(row.name);
		return now == null || now.generation != row.generation;
	}

	// a row written by a build with another PackageCodec format
	private PackageParser.Package reparse(Row row) throws IOException {
		PackageParser.Package pkg = row.generated ? ATLGStub.makePackage(row.name) : parse(file(row.apks[0]));
		// another process may have replaced the row meanwhile: then its write wins
		db.execSQL("UPDATE packages SET parse_version = ?, parsed = ? WHERE name = ? AND generation = ?",
		           new Object[] {PackageCodec.PARSE_VERSION, PackageCodec.marshall(pkg), row.name, row.generation});
		Slog.i(TAG, "parsed " + row.name + " again for parse version " + PackageCodec.PARSE_VERSION);
		return pkg;
	}

	private void fillPaths(PackageParser.Package pkg, Row row) {
		ApplicationInfo info = pkg.applicationInfo;
		info.uid = row.uid;
		if (row.generated) {
			info.sourceDir = info.publicSourceDir = ATLPaths.gstub_jar.getPath();
		} else {
			info.sourceDir = info.publicSourceDir = file(row.apks[0]).getPath();
			String[] splits = new String[row.apks.length - 1];
			for (int i = 1; i < row.apks.length; i++)
				splits[i - 1] = file(row.apks[i]).getPath();
			info.splitSourceDirs = info.splitPublicSourceDirs = splits;
		}
		info.dataDir = dataDir(row.name).getPath();
		info.nativeLibraryDir = new File(dataDir(row.name), "lib").getPath();
	}

	public File file(String prefixRelative) {
		return new File(prefix, prefixRelative);
	}

	public File dataDir(String name) {
		return new File(data, name);
	}

	/** Parses the manifest and reads the v1 certificates; an unsigned APK keeps null signatures. */
	static PackageParser.Package parse(File apk) throws IOException {
		PackageParser parser = new PackageParser(apk.getPath());
		DisplayMetrics metrics = new DisplayMetrics();
		metrics.setToDefaults();
		PackageParser.Package pkg = parser.parsePackage(apk, apk.getPath(), metrics, 0);
		if (pkg == null || parser.getParseError() != PackageManager.INSTALL_SUCCEEDED)
			throw new IOException("can't parse " + apk + " (error " + parser.getParseError() + ")");
		parser.collectCertificates(pkg, 0);
		return pkg;
	}

	/**
	 * Inside a write transaction, so it never races an install or an uninstall: removes version dirs
	 * no row names, trash, and staging dirs older than a day (younger ones may be live sessions).
	 */
	private void sweep() {
		Set<String> referenced = new HashSet<>();
		for (Row row : rowsWhere("generated = 0", new String[0]))
			for (String apk : row.apks)
				referenced.add(new File(apk).getParent());
		for (String name : list(apps)) {
			if (name.startsWith("."))
				continue;
			File pkgDir = new File(apps, name);
			for (String version : list(pkgDir))
				if (!referenced.contains("apps/" + name + "/" + version))
					deleteTree(new File(pkgDir, version));
			if (list(pkgDir).length == 0)
				pkgDir.delete();
		}
		long now = System.currentTimeMillis();
		for (String name : list(staging)) {
			File dir = new File(staging, name);
			if (now - dir.lastModified() > STAGING_MAX_AGE_MS)
				deleteTree(dir);
		}
		for (String name : list(trash))
			deleteTree(new File(trash, name));
	}

	static String[] list(File dir) {
		String[] names = dir.list();
		return names == null ? new String[0] : names;
	}

	static void deleteTree(File file) {
		if (file.isDirectory() && !isSymlink(file))
			for (String name : list(file))
				deleteTree(new File(file, name));
		if (!file.delete() && file.exists())
			Slog.w(TAG, "can't delete " + file);
	}

	private static boolean isSymlink(File file) {
		try {
			return OsConstants.S_ISLNK(Os.lstat(file.getPath()).st_mode);
		} catch (ErrnoException e) {
			return false;
		}
	}

	static void fsyncDir(File dir) throws IOException {
		try {
			FileDescriptor fd = Os.open(dir.getPath(), OsConstants.O_RDONLY, 0);
			try {
				Os.fsync(fd);
			} finally {
				Os.close(fd);
			}
		} catch (ErrnoException e) {
			throw new IOException("fsync " + dir, e);
		}
	}
}
