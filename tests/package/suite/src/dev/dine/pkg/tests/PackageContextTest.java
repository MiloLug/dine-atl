package dev.dine.pkg.tests;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import java.io.File;

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

	public void testUnknownPackage() throws Exception {
		try {
			getInstrumentation().getTargetContext().createPackageContext("dev.dine.pkg.none", 0);
			fail("a context of a package that isn't installed");
		} catch (PackageManager.NameNotFoundException expected) {
		}
	}
}
