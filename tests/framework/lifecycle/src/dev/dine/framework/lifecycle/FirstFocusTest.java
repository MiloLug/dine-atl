package dev.dine.framework.lifecycle;

import android.content.Intent;

/* The app's first activity resumes before its window's first layout, and has window focus only
 * from then on. The runner takes the classes in name order, so this one starts that first activity;
 * the test says so when it isn't the first. */
public class FirstFocusTest extends android.test.InstrumentationTestCase {
	public void testTheFirstActivityGainsWindowFocusOnce() throws Exception {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		PlainActivity activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
		assertEquals("activities this app started", 1, PlainActivity.created);
		for (int i = 0; i < 100 && activity.focusGains == 0; i++)
			Thread.sleep(50);
		getInstrumentation().waitForIdleSync();
		assertEquals(1, activity.focusGains);
		activity.finish();
		getInstrumentation().waitForIdleSync();
	}
}
