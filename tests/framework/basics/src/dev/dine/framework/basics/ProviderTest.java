package dev.dine.framework.basics;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class ProviderTest extends android.test.InstrumentationTestCase {
	// android:authorities is a ';'-separated list
	public void testEachAuthorityReachesTheProvider() {
		ContentResolver resolver = getInstrumentation().getTargetContext().getContentResolver();
		for (String authority : new String[] {"dev.dine.framework.basics.one", "dev.dine.framework.basics.two"}) {
			Cursor cursor = resolver.query(Uri.parse("content://" + authority + "/x"), null, null, null, null);
			assertNotNull(authority, cursor);
			assertTrue(cursor.moveToFirst());
			assertEquals(authority, cursor.getString(0));
			cursor.close();
		}
	}
}
