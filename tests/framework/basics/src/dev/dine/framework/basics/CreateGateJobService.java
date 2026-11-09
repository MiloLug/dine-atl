package dev.dine.framework.basics;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// holds the main thread in onCreate, inside a job's start and before the job counts as running;
// only JobSchedulerTest#testCancelDuringStartStopsTheJob uses it, since the scheduler creates it once
public class CreateGateJobService extends TestJobService {
	static final CountDownLatch creating = new CountDownLatch(1);
	static final CountDownLatch release = new CountDownLatch(1);

	@Override
	public void onCreate() {
		super.onCreate();
		creating.countDown();
		try {
			release.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
