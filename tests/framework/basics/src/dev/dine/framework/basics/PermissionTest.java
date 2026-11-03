package dev.dine.framework.basics;

import android.content.Intent;
import android.content.pm.PackageManager;
import java.util.Arrays;

@SuppressWarnings("deprecation")
public class PermissionTest extends android.test.InstrumentationTestCase {
	public void testRequestIsAnsweredAfterItReturns() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PermissionActivity.class);
		final PermissionActivity activity = (PermissionActivity)getInstrumentation().startActivitySync(intent);
		final Object[] got = new Object[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				activity.requestPermissions(new String[] {"android.permission.ACCESS_NETWORK_STATE", "android.permission.CAMERA"}, 7);
				got[0] = activity.results;
				try {
					activity.requestPermissions(new String[] {"android.permission.CAMERA"}, -1);
					got[1] = "no exception";
				} catch (IllegalArgumentException e) {
					got[1] = "IllegalArgumentException";
				}
			}
		});
		// the answer was posted to the main thread before this
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {}
		});
		assertNull(got[0]);
		assertEquals("IllegalArgumentException", got[1]);
		assertEquals(7, activity.code);
		assertEquals("[android.permission.ACCESS_NETWORK_STATE, android.permission.CAMERA]", Arrays.toString(activity.permissions));
		assertEquals(Arrays.toString(new int[] {PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED}), Arrays.toString(activity.results));
		assertTrue(activity.on_main);
		assertFalse(activity.shouldShowRequestPermissionRationale("android.permission.CAMERA"));
	}
}
