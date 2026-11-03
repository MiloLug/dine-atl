package dev.dine.framework.basics;

import android.os.AsyncTask;
import java.util.concurrent.ThreadPoolExecutor;
import junit.framework.TestCase;

// AsyncTask's pool is the app's: in a VM of several apps (test framework --one-vm) a shared pool
// started by another app would run this task on that app's thread
public class AsyncTaskTest extends TestCase {
	public void testPoolThreadsAreTheApps() throws Exception {
		ThreadPoolExecutor pool = (ThreadPoolExecutor) AsyncTask.THREAD_POOL_EXECUTOR;
		pool.prestartAllCoreThreads();
		final String[] group = new String[1];
		pool.submit(new Runnable() {
			public void run() {
				group[0] = Thread.currentThread().getThreadGroup().getName();
			}
		}).get();
		assertEquals("dev.dine.framework.basics", group[0]);
	}
}
