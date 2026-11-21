package dev.dine.pkg.tests;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.util.Arrays;

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

	// Mihon reads an extension's own APK file with both flags
	public void testArchiveSigners() throws Exception {
		String path = Fixtures.apk("ext").getPath();
		Signature[] installed = pm().getPackageInfo("dev.dine.pkg.ext", PackageManager.GET_SIGNATURES).signatures;
		PackageInfo both = pm().getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES | PackageManager.GET_SIGNING_CERTIFICATES);
		assertEquals(Arrays.asList(installed), Arrays.asList(both.signatures));
		assertNotNull(both.signingInfo.getApkContentsSigners());
		assertEquals(Arrays.asList(installed), Arrays.asList(both.signingInfo.getApkContentsSigners()));
		PackageInfo certificates = pm().getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES);
		assertNull(certificates.signatures);
		assertNotNull(certificates.signingInfo.getApkContentsSigners());
		assertEquals(Arrays.asList(installed), Arrays.asList(certificates.signingInfo.getApkContentsSigners()));
	}
}
