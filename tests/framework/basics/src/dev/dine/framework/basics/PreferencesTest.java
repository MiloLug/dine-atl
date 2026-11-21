package dev.dine.framework.basics;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PreferencesTest extends android.test.InstrumentationTestCase {
	// two instances of one name would each write their own map to the file, and one write would lose the other's keys
	public void testOneInstanceUnderConcurrentFirstUse() throws Exception {
		final Context context = getInstrumentation().getTargetContext();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			for (int round = 0; round < 50; round++) {
				final String name = "race_" + round;
				final CountDownLatch start = new CountDownLatch(1);
				List<Future<SharedPreferences>> got = new ArrayList<>();
				for (int i = 0; i < 8; i++) {
					got.add(pool.submit(new Callable<SharedPreferences>() {
						public SharedPreferences call() throws Exception {
							start.await();
							return context.getSharedPreferences(name, 0);
						}
					}));
				}
				start.countDown();
				SharedPreferences first = got.get(0).get();
				for (Future<SharedPreferences> prefs : got)
					assertSame(name, first, prefs.get());
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
