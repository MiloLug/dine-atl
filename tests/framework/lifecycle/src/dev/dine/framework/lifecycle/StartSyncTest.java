package dev.dine.framework.lifecycle;

import android.app.Activity;
import android.content.Intent;

// startActivitySync returns an activity or throws, as AOSP 10's, with its messages and the cause of a failed start
public class StartSyncTest extends android.test.InstrumentationTestCase {
	private RuntimeException startFailure(Intent intent) {
		try {
			getInstrumentation().startActivitySync(intent);
		} catch (RuntimeException e) {
			return e;
		}
		fail("startActivitySync returned for " + intent);
		return null;
	}

	// AOSP throws "Unable to resolve activity for: " here, as no manifest activity matches (ATL-181)
	public void testAClassThatIsMissingThrowsWithItsCause() {
		Intent intent = new Intent().setClassName(getInstrumentation().getTargetContext(), "dev.dine.framework.lifecycle.MissingActivity");
		RuntimeException failure = startFailure(intent);
		assertTrue(failure.getMessage(), failure.getMessage().startsWith("Unable to start activity ComponentInfo{"));
		assertTrue("cause: " + failure.getCause(), failure.getCause() instanceof ClassNotFoundException);
	}

	public void testAnIntentNothingResolvesThrows() {
		RuntimeException failure = startFailure(new Intent("dev.dine.framework.lifecycle.NOTHING"));
		assertTrue(failure.getMessage(), failure.getMessage().startsWith("Unable to resolve activity for: "));
	}

	public void testAComponentOfAnotherPackageThrows() {
		RuntimeException failure = startFailure(new Intent().setClassName("dev.dine.other", "dev.dine.other.Main"));
		assertTrue(failure.getMessage(), failure.getMessage().contains("resolved to different process dev.dine.other"));
	}

	public void testAnIntentAFilterMatchesStartsItsActivity() {
		Activity activity = getInstrumentation().startActivitySync(new Intent("dev.dine.framework.lifecycle.PLAIN"));
		assertTrue(String.valueOf(activity), activity instanceof PlainActivity);
		assertEquals(PlainActivity.class.getName(), activity.getIntent().getComponent().getClassName());
		activity.finish();
		getInstrumentation().waitForIdleSync();
	}
}
