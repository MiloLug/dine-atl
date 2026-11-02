package dev.dine.pkg.tests;

import android.atl.ATLInstaller;
import android.atl.ATLRegistry;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.lang.reflect.Field;

public class RegistryTest extends android.test.InstrumentationTestCase {
	// FULL, so a commit is on disk before the installer deletes the APK it replaced
	public void testWriteSyncModeIsFull() throws Exception {
		Field field = ATLRegistry.class.getDeclaredField("db");
		field.setAccessible(true);
		SQLiteDatabase db = (SQLiteDatabase)field.get(ATLRegistry.get());
		db.beginTransactionNonExclusive();
		try (Cursor c = db.rawQuery("PRAGMA synchronous", null)) {
			assertTrue(c.moveToFirst());
			assertEquals(2, c.getInt(0));
		} finally {
			db.endTransaction();
		}
	}

	private static ATLRegistry.Row install(String fixture) throws Exception {
		ATLInstaller.Result result = ATLInstaller.commit(ATLInstaller.stage(Fixtures.apk(fixture)), false, false, null);
		assertEquals(PackageInstaller.STATUS_SUCCESS, result.status);
		return ATLRegistry.get().row(result.packageName);
	}

	// rows read before a replace or an uninstall: their APKs are gone, and so are their bytes
	public void testStaleRows() throws Exception {
		ATLRegistry registry = ATLRegistry.get();
		ATLRegistry.Row v1 = registry.row("dev.dine.pkg.gone");
		ATLRegistry.Row v2 = install("gone-v2");
		ATLRegistry.Row v1again = install("gone");
		// v2's generation was never decoded here: the entry is the current row and its package, together
		ATLRegistry.Entry current = registry.entry(v2);
		assertEquals(v1again.generation, current.row.generation);
		assertEquals(1, current.row.versionCode);
		assertEquals(1, current.pkg.mVersionCode);
		assertEquals(registry.file(current.row.apks[0]).getPath(), current.pkg.applicationInfo.sourceDir);
		ATLRegistry.Row v2again = install("gone-v2");
		assertEquals(PackageInstaller.STATUS_SUCCESS, ATLInstaller.uninstall("dev.dine.pkg.gone").status);
		assertNull(registry.entry(v2again));
		// v1's generation may be in the cache (QueryTest decodes every row): its own row and package, or none
		ATLRegistry.Entry old = registry.entry(v1);
		assertTrue(old == null || (old.row.generation == v1.generation && old.pkg.mVersionCode == 1));
	}
}
