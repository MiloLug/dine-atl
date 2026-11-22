package dev.dine.framework.lifecycle;

import android.content.Intent;

// startActivitySync returns an activity or throws, as Android's, with the cause of a failed start
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

	public void testAClassThatIsMissingThrowsWithItsCause() {
		Intent intent = new Intent().setClassName(getInstrumentation().getTargetContext(), "dev.dine.framework.lifecycle.MissingActivity");
		RuntimeException failure = startFailure(intent);
		assertTrue("cause: " + failure.getCause(), failure.getCause() instanceof ClassNotFoundException);
	}

	public void testAnIntentNothingResolvesThrows() {
		startFailure(new Intent("dev.dine.framework.lifecycle.NOTHING"));
	}
}
