package dev.dine.framework.basics;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// androidx's lifecycle at API 29 runs on these: the pre and post calls around each lifecycle call
@SuppressWarnings("deprecation")
public class LifecycleCallbacksTest extends android.test.InstrumentationTestCase {
	private static class Recorder implements Application.ActivityLifecycleCallbacks {
		final List<Activity> activities = new ArrayList<>();
		final List<String> calls = new ArrayList<>();

		private void add(Activity activity, String call) {
			activities.add(activity);
			calls.add(call);
		}

		// an earlier test's activity pauses and stops in between
		List<String> of(Activity activity) {
			List<String> of = new ArrayList<>();
			for (int i = 0; i < calls.size(); i++)
				if (activities.get(i) == activity)
					of.add(calls.get(i));
			return of;
		}

		public void onActivityPreCreated(Activity a, Bundle b) {
			add(a, "pre-created");
		}
		public void onActivityCreated(Activity a, Bundle b) {
			add(a, "created");
		}
		public void onActivityPostCreated(Activity a, Bundle b) {
			add(a, "post-created");
		}
		public void onActivityStarted(Activity a) {
			add(a, "started");
		}
		public void onActivityPostStarted(Activity a) {
			add(a, "post-started");
		}
		public void onActivityResumed(Activity a) {
			add(a, "resumed");
		}
		public void onActivityPostResumed(Activity a) {
			add(a, "post-resumed");
		}
		public void onActivityPrePaused(Activity a) {
			add(a, "pre-paused");
		}
		public void onActivityPaused(Activity a) {
			add(a, "paused");
		}
		public void onActivityStopped(Activity a) {
			add(a, "stopped");
		}
		public void onActivitySaveInstanceState(Activity a, Bundle b) {}
		public void onActivityDestroyed(Activity a) {
			add(a, "destroyed");
		}
	}

	// AOSP's order: the application's always first to last, the activity's last to first on the way down
	private static class Named implements Application.ActivityLifecycleCallbacks {
		private final String name;
		private final List<String> calls;
		private final CountDownLatch done; // counted at the application's post-paused, the last call

		Named(String name, List<String> calls, CountDownLatch done) {
			this.name = name;
			this.calls = calls;
			this.done = done;
		}

		private void add(String call) {
			synchronized (calls) {
				calls.add(name + " " + call);
			}
			if (name.equals("app") && call.equals("post-paused"))
				done.countDown();
		}

		public void onActivityCreated(Activity a, Bundle b) {}
		public void onActivityStarted(Activity a) {}
		public void onActivityResumed(Activity a) {}
		public void onActivityPrePaused(Activity a) {
			add("pre-paused");
		}
		public void onActivityPaused(Activity a) {
			add("paused");
		}
		public void onActivityPostPaused(Activity a) {
			add("post-paused");
		}
		public void onActivityStopped(Activity a) {}
		public void onActivitySaveInstanceState(Activity a, Bundle b) {}
		public void onActivityDestroyed(Activity a) {}
	}

	public void testCallbackOrderOnPause() throws InterruptedException {
		Application application = (Application)getInstrumentation().getTargetContext().getApplicationContext();
		final List<String> calls = new ArrayList<>();
		CountDownLatch done = new CountDownLatch(1);
		Named app = new Named("app", calls, done);
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final Activity started = getInstrumentation().startActivitySync(intent);
		getInstrumentation().waitForIdleSync();
		started.registerActivityLifecycleCallbacks(new Named("first", calls, done));
		started.registerActivityLifecycleCallbacks(new Named("second", calls, done));
		application.registerActivityLifecycleCallbacks(app);
		try {
			getInstrumentation().runOnMainSync(new Runnable() {
				public void run() {
					started.finish();
				}
			});
			assertTrue("no post-paused " + calls, done.await(10, TimeUnit.SECONDS));
			synchronized (calls) {
				assertEquals("[app pre-paused, second pre-paused, first pre-paused, second paused, first paused, app paused, second post-paused, first post-paused, app post-paused]", calls.toString());
			}
		} finally {
			application.unregisterActivityLifecycleCallbacks(app);
		}
	}

	public void testCallbacksSurroundTheLifecycle() {
		Application application = (Application)getInstrumentation().getTargetContext().getApplicationContext();
		Recorder recorder = new Recorder();
		application.registerActivityLifecycleCallbacks(recorder);
		try {
			Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
			Activity started = getInstrumentation().startActivitySync(intent);
			getInstrumentation().waitForIdleSync();
			assertEquals("[pre-created, created, post-created, started, post-started, resumed, post-resumed]", recorder.of(started).toString());
		} finally {
			application.unregisterActivityLifecycleCallbacks(recorder);
		}
	}
}
