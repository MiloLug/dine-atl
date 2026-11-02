package dev.dine.framework.basics;

import junit.framework.TestCase;

public class AppThreadTest extends TestCase {
	// threads carry their app: its thread group, named after the package
	public void testThreadsJoinTheAppsGroup() throws Exception {
		final String[] group = new String[1];
		Thread thread = new Thread(new Runnable() {
			public void run() {
				group[0] = Thread.currentThread().getThreadGroup().getName();
			}
		});
		thread.start();
		thread.join();
		assertEquals("dev.dine.framework.basics", group[0]);
	}
}
