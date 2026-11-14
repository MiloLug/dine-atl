package dev.dine.framework.lifecycle;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* An app ends with its last activity, except under an instrumentation, which ends it when it
 * finishes. This suite's tests leave no activity behind, so the stack is empty in between. */
public class TaskEndTest extends android.test.InstrumentationTestCase {
	private Activity start() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		return getInstrumentation().startActivitySync(intent);
	}

	// the main thread ran what was queued when the call was made: twice, for what that queued
	private boolean mainThreadAnswers() throws InterruptedException {
		for (int i = 0; i < 2; i++) {
			final CountDownLatch ran = new CountDownLatch(1);
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				public void run() {
					ran.countDown();
				}
			});
			if (!ran.await(5, TimeUnit.SECONDS))
				return false;
		}
		return true;
	}

	public void testTheAppGoesOnAfterItsLastActivityFinishes() throws Exception {
		Activity first = start();
		first.finish();
		assertTrue("the main loop stopped with the last activity", mainThreadAnswers());
		assertTrue(first.isDestroyed());
		Activity second = start();
		assertFalse(second.isDestroyed());
		second.finish();
		assertTrue("the main loop stopped with the last activity", mainThreadAnswers());
	}
}
