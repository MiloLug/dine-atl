package dev.dine.framework.basics;

import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

// the app's objects are finalized and cleaned on a thread of the app, which sees it
public class CleanupTest extends TestCase {
	static volatile String finalizedIn;
	static volatile boolean finalizerHasLooper;

	static class Finalizable {
		final CountDownLatch done;

		Finalizable(CountDownLatch done) {
			this.done = done;
		}

		@Override
		protected void finalize() {
			finalizedIn = Thread.currentThread().getThreadGroup().getName();
			finalizerHasLooper = Looper.getMainLooper() != null;
			done.countDown();
		}
	}

	public void testFinalizerRunsInTheApp() throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		makeFinalizable(done);
		collectUntil(done);
		assertEquals("dev.dine.framework.basics", finalizedIn);
		assertTrue(finalizerHasLooper);
	}

	public void testCleanerRunsInTheApp() throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		String[] cleanedIn = new String[1];
		makeCleaner(done, cleanedIn);
		collectUntil(done);
		assertEquals("dev.dine.framework.basics", cleanedIn[0]);
	}

	private static void makeFinalizable(CountDownLatch done) {
		new Finalizable(done);
	}

	// sun.misc.Cleaner isn't on the test APK's compile class path
	private static void makeCleaner(final CountDownLatch done, final String[] cleanedIn) throws Exception {
		Class.forName("sun.misc.Cleaner").getMethod("create", Object.class, Runnable.class).invoke(null, new Object(), new Runnable() {
			public void run() {
				cleanedIn[0] = Thread.currentThread().getThreadGroup().getName();
				done.countDown();
			}
		});
	}

	private static void collectUntil(CountDownLatch done) throws InterruptedException {
		for (int i = 0; i < 50 && !done.await(100, TimeUnit.MILLISECONDS); i++) {
			Runtime.getRuntime().gc();
			System.runFinalization();
		}
	}
}
