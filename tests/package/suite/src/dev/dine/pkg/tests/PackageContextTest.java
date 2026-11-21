package dev.dine.pkg.tests;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/** A context of another installed package: its own data and code, its creator's running app (main Looper); no Application while the package isn't running. */
public class PackageContextTest extends android.test.InstrumentationTestCase {
	private static final String EXT = "dev.dine.pkg.ext";

	private Context ext() throws Exception {
		return getInstrumentation().getTargetContext().createPackageContext(EXT, Context.CONTEXT_INCLUDE_CODE);
	}

	private File extDataDir() throws Exception {
		return new File(getInstrumentation().getTargetContext().getPackageManager().getApplicationInfo(EXT, 0).dataDir);
	}

	public void testNoApplicationWhileNotRunning() throws Exception {
		assertNull(ext().getApplicationContext());
	}

	public void testFilesDirIsThePackages() throws Exception {
		assertEquals(new File(extDataDir(), "files"), ext().getFilesDir());
	}

	public void testPreferencesAreThePackages() throws Exception {
		SharedPreferences prefs = ext().getSharedPreferences("context_test", 0);
		assertTrue(prefs.edit().putInt("written", 1).commit());
		assertTrue(new File(extDataDir(), "shared_prefs/context_test.xml").exists());
	}

	// a package context and the contexts made from it live in their creator's running app
	public void testMainLooperIsTheCreators() throws Exception {
		Context creator = getInstrumentation().getTargetContext();
		assertNotNull(creator.getMainLooper());
		assertSame(creator.getMainLooper(), ext().getMainLooper());
		assertSame(creator.getMainLooper(), ext().createConfigurationContext(new Configuration()).getMainLooper());
		assertSame(creator.getMainLooper(), creator.createPackageContext("android", 0).getMainLooper());
	}

	// no other test touches extb's files: its files dir doesn't exist before this call
	public void testFileOutputMakesTheFilesDir() throws Exception {
		Context extb = getInstrumentation().getTargetContext().createPackageContext("dev.dine.pkg.extb", 0);
		try (FileOutputStream out = extb.openFileOutput("state", Context.MODE_PRIVATE)) {
			out.write(1);
		}
		assertEquals(1, new File(extb.getFilesDir(), "state").length());
	}

	public void testFileOutputAppends() throws Exception {
		Context ext = ext();
		for (int i = 0; i < 2; i++) {
			try (FileOutputStream out = ext.openFileOutput("appended", Context.MODE_APPEND)) {
				out.write(i);
			}
		}
		assertEquals(2, ext.getFileStreamPath("appended").length());
		try (FileOutputStream out = ext.openFileOutput("appended", Context.MODE_PRIVATE)) {
			out.write(9);
		}
		assertEquals(1, ext.getFileStreamPath("appended").length());
		assertTrue(Arrays.asList(ext.fileList()).contains("appended"));
		assertTrue(ext.deleteFile("appended"));
		assertFalse(ext.getFileStreamPath("appended").exists());
	}

	public void testFileNamesHaveNoSeparator() throws Exception {
		Context ext = ext();
		try {
			ext.openFileOutput("../escaped", 0).close();
			fail("openFileOutput took a path");
		} catch (IllegalArgumentException expected) {
		}
		try {
			ext.openFileInput("../escaped").close();
			fail("openFileInput took a path");
		} catch (IllegalArgumentException expected) {
		}
		assertFalse(new File(extDataDir(), "escaped").exists());
	}

	public void testDatabaseIsInTheDatabasesDir() throws Exception {
		Context ext = ext();
		assertEquals(new File(extDataDir(), "databases/context_test.db"), ext.getDatabasePath("context_test.db"));
		SQLiteDatabase db = ext.openOrCreateDatabase("context_test.db", 0, null);
		try {
			assertEquals(ext.getDatabasePath("context_test.db").getPath(), db.getPath());
		} finally {
			db.close();
		}
		assertTrue(ext.deleteDatabase("context_test.db"));
	}

	public void testDirIsInTheDataDir() throws Exception {
		File dir = ext().getDir("context_test", 0);
		assertEquals(new File(extDataDir(), "app_context_test"), dir);
		assertTrue(dir.isDirectory());
	}

	public void testUnknownPackage() throws Exception {
		try {
			getInstrumentation().getTargetContext().createPackageContext("dev.dine.pkg.none", 0);
			fail("a context of a package that isn't installed");
		} catch (PackageManager.NameNotFoundException expected) {
		}
	}
}
