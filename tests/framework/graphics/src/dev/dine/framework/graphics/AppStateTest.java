package dev.dine.framework.graphics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Each app keeps its own receivers, preferences and installer sessions: in a VM of several apps (test
// framework --one-vm, basics and graphics carry this class) what an earlier app left must not reach this one.
public class AppStateTest extends android.test.InstrumentationTestCase {
	private static final String ME = "dev.dine.framework.graphics";
	private static final String SHARED = "dev.dine.framework.APP_STATE";
	private static final String FOREIGN = "dev.dine.framework.APP_STATE_FOREIGN";

	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	// the first receiver stays registered and answers another app's broadcast with FOREIGN
	public void testReceiversAreTheApps() {
		Context context = context();
		context.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context sender, Intent intent) {
				if (!ME.equals(intent.getStringExtra("from")))
					sender.sendBroadcast(new Intent(FOREIGN).putExtra("from", ME));
			}
		}, new IntentFilter(SHARED));
		final List<String> heard = new ArrayList<>();
		BroadcastReceiver listener = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				heard.add(intent.getStringExtra("from"));
			}
		};
		context.registerReceiver(listener, new IntentFilter(FOREIGN));
		context.sendBroadcast(new Intent(SHARED).putExtra("from", ME));
		context.unregisterReceiver(listener);
		assertEquals("receivers of other apps heard this app's broadcast", new ArrayList<String>(), heard);
	}

	// an earlier app's instance of the name would write that app's file
	public void testPreferencesAreTheApps() {
		Context context = context();
		File file = new File(context.getDataDir(), "shared_prefs/app_state.xml");
		file.delete();
		SharedPreferences prefs = context.getSharedPreferences("app_state", 0);
		assertTrue(prefs.edit().putString("app", ME).commit());
		assertTrue("another app's preferences answered", file.exists());
		assertEquals(ME, prefs.getString("app", null));
	}

	// the session stays open: a later app's getMySessions would list it
	public void testSessionsAreTheApps() throws Exception {
		PackageInstaller installer = context().getPackageManager().getPackageInstaller();
		int id = installer.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
		List<Integer> mine = new ArrayList<>();
		for (PackageInstaller.SessionInfo info : installer.getMySessions())
			mine.add(info.getSessionId());
		assertEquals(Arrays.asList(id), mine);
		for (PackageInstaller.SessionInfo info : installer.getAllSessions()) {
			if (info.getSessionId() == id)
				continue;
			try {
				installer.openSession(info.getSessionId());
				fail("opened another app's session " + info.getSessionId());
			} catch (SecurityException expected) {
			}
			try {
				installer.abandonSession(info.getSessionId());
				fail("abandoned another app's session " + info.getSessionId());
			} catch (SecurityException expected) {
			}
		}
		try {
			installer.abandonSession(Integer.MAX_VALUE);
			fail("abandoned a session that doesn't exist");
		} catch (SecurityException expected) {
		}
	}
}
