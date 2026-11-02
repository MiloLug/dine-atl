package dev.dine.pkg.tests;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Process;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/** PackageManager over the registry; the harness installed the fixtures (package.py JUNIT_FIXTURES). */
public class QueryTest extends android.test.InstrumentationTestCase {
	private static final String APP = "dev.dine.pkg.app";
	private static final String EXT = "dev.dine.pkg.ext";

	private PackageManager pm() {
		return getInstrumentation().getTargetContext().getPackageManager();
	}

	private static PackageInfo find(List<PackageInfo> infos, String name) {
		for (PackageInfo info : infos)
			if (info.packageName.equals(name))
				return info;
		return null;
	}

	// Mihon's discovery call: an extension is a package with this feature
	public void testInstalledPackagesReqFeatures() {
		List<PackageInfo> infos = pm().getInstalledPackages(PackageManager.GET_META_DATA | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS);
		PackageInfo ext = find(infos, EXT);
		assertNotNull(ext);
		assertNotNull(ext.reqFeatures);
		boolean found = false;
		for (FeatureInfo feature : ext.reqFeatures)
			found |= "dine.test.extension".equals(feature.name);
		assertTrue(found);
		assertEquals(".Ext", ext.applicationInfo.metaData.getString("dine.test.ext.class"));
		assertNotNull(ext.signatures);
		assertNull(find(infos, APP).reqFeatures);
		assertNotNull(find(infos, "dev.dine.pkg.tests"));
	}

	public void testInstalledApplications() {
		boolean app = false, ext = false;
		for (ApplicationInfo info : pm().getInstalledApplications(0)) {
			app |= APP.equals(info.packageName);
			ext |= EXT.equals(info.packageName);
		}
		assertTrue(app && ext);
	}

	public void testPackageInfo() throws Exception {
		PackageInfo info = pm().getPackageInfo(APP, PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS |
		                                                PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
		assertEquals(2, info.versionCode);
		assertEquals(2, info.activities.length);
		assertEquals(1, info.receivers.length);
		assertEquals(1, info.services.length);
		assertEquals(1, info.providers.length);
		File prefix = new File(System.getenv("DINE_PREFIX")).getCanonicalFile();
		assertTrue(info.applicationInfo.sourceDir, new File(info.applicationInfo.sourceDir).getCanonicalPath().startsWith(new File(prefix, "apps/" + APP).getPath()));
		assertEquals(new File(prefix, "data/" + APP).getPath(), new File(info.applicationInfo.dataDir).getCanonicalPath());
		assertTrue(info.firstInstallTime > 0);
		try {
			pm().getPackageInfo("dev.dine.pkg.missing", 0);
			fail("a missing package has info");
		} catch (PackageManager.NameNotFoundException expected) {
		}
	}

	public void testUids() throws Exception {
		int uid = pm().getPackageUid(APP, 0);
		assertTrue(uid >= 10000);
		assertEquals(uid, pm().getApplicationInfo(APP, 0).uid);
		assertEquals(Arrays.asList(APP), Arrays.asList(pm().getPackagesForUid(uid)));
		assertEquals(APP, pm().getNameForUid(uid));
		assertTrue(uid != pm().getPackageUid(EXT, 0));
		assertEquals(pm().getApplicationInfo("dev.dine.pkg.tests", 0).uid, Process.myUid());
	}

	public void testCheckSignatures() {
		assertEquals(PackageManager.SIGNATURE_MATCH, pm().checkSignatures(APP, EXT));
		assertEquals(PackageManager.SIGNATURE_NO_MATCH, pm().checkSignatures(APP, "dev.dine.pkg.extb"));
		assertEquals(PackageManager.SIGNATURE_SECOND_NOT_SIGNED, pm().checkSignatures(APP, "dev.dine.pkg.plain"));
		assertEquals(PackageManager.SIGNATURE_UNKNOWN_PACKAGE, pm().checkSignatures(APP, "dev.dine.pkg.missing"));
	}

	public void testUnsignedHasNoSignatures() throws Exception {
		assertNull(pm().getPackageInfo("dev.dine.pkg.plain", PackageManager.GET_SIGNATURES).signatures);
	}

	// spoof asks to be reported with key B's certificate; the CLI granted it, not spoofdeny's
	public void testFakeSignature() throws Exception {
		Signature keyB = pm().getPackageInfo("dev.dine.pkg.extb", PackageManager.GET_SIGNATURES).signatures[0];
		Signature keyA = pm().getPackageInfo(EXT, PackageManager.GET_SIGNATURES).signatures[0];
		PackageInfo spoof = pm().getPackageInfo("dev.dine.pkg.spoof", PackageManager.GET_SIGNATURES | PackageManager.GET_SIGNING_CERTIFICATES);
		assertEquals(Arrays.asList(keyB), Arrays.asList(spoof.signatures));
		assertEquals(Arrays.asList(keyB), Arrays.asList(spoof.signingInfo.getApkContentsSigners()));
		PackageInfo deny = pm().getPackageInfo("dev.dine.pkg.spoofdeny", PackageManager.GET_SIGNATURES);
		assertEquals(Arrays.asList(keyA), Arrays.asList(deny.signatures));
	}

	public void testPreinstalledPlayServices() throws Exception {
		PackageInfo gms = pm().getPackageInfo("com.google.android.gms", PackageManager.GET_SIGNATURES | PackageManager.GET_META_DATA);
		assertTrue((gms.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
		assertEquals(250932030, gms.versionCode);
		assertTrue(gms.signatures.length > 0);
		assertTrue(gms.applicationInfo.uid >= 10000);
	}

	public void testLaunchIntent() {
		Intent launch = pm().getLaunchIntentForPackage(APP);
		assertEquals(new ComponentName(APP, APP + ".Main"), launch.getComponent());
		assertEquals(Intent.ACTION_MAIN, launch.getAction());
		assertTrue((launch.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
		assertNull(pm().getLaunchIntentForPackage(EXT));
	}

	private static java.util.Set<String> packages(List<ResolveInfo> infos) {
		java.util.Set<String> names = new java.util.HashSet<>();
		for (ResolveInfo info : infos)
			names.add((info.activityInfo != null ? info.activityInfo : info.serviceInfo).packageName);
		return names;
	}

	// plain is app under another name: each filter matches in both
	public void testResolveAcrossPackages() throws Exception {
		java.util.Set<String> both = new java.util.HashSet<>(Arrays.asList(APP, "dev.dine.pkg.plain"));
		List<ResolveInfo> viewers = pm().queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("dineapp:x")), 0);
		assertEquals(both, packages(viewers));
		assertEquals(APP + ".Viewer", viewers.get(0).activityInfo.name);
		assertTrue(pm().queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("other:x")), 0).isEmpty());
		assertEquals(both, packages(pm().queryBroadcastReceivers(new Intent("dev.dine.pkg.PING"), 0)));
		ResolveInfo service = pm().resolveService(new Intent("dev.dine.pkg.BIND").setPackage(APP), 0);
		assertEquals(APP, service.serviceInfo.packageName);
		assertEquals(APP + ".Svc", service.serviceInfo.name);
		assertNull(service.activityInfo);
		assertEquals("svc", pm().getServiceInfo(new ComponentName(APP, APP + ".Svc"), PackageManager.GET_META_DATA).metaData.getString("dine.test.svc"));
		ProviderInfo provider = pm().resolveContentProvider("dev.dine.pkg.app.prov", PackageManager.GET_META_DATA);
		assertEquals(APP + ".Prov", provider.name);
		assertEquals("prov", provider.metaData.getString("dine.test.prov"));
	}

	// the harness set this row to an older parse version
	public void testStaleRowParsedAgain() throws Exception {
		assertEquals(1, pm().getPackageInfo(EXT, 0).versionCode);
	}
}
