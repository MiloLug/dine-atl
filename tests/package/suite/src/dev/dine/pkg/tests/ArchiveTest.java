package dev.dine.pkg.tests;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class ArchiveTest extends android.test.InstrumentationTestCase {
	private PackageManager pm() {
		return getInstrumentation().getTargetContext().getPackageManager();
	}

	public void testArchiveReqFeatures() {
		PackageInfo info = pm().getPackageArchiveInfo(Fixtures.apk("ext").getPath(), PackageManager.GET_CONFIGURATIONS);
		assertNotNull(info);
		assertEquals("dev.dine.pkg.ext", info.packageName);
		assertNotNull(info.reqFeatures);
		boolean found = false;
		for (FeatureInfo f : info.reqFeatures)
			found |= "dine.test.extension".equals(f.name);
		assertTrue(found);
	}
}
