package dev.dine.framework.windows;

import android.atl.ATLTestHooks;
import android.content.Intent;
import android.content.res.Configuration;

// a night change for the app reaches its Resources and, on its main thread, an activity covering uiMode
public class ConfigTest extends android.test.InstrumentationTestCase {
	private ConfigModeActivity activity;
	private int original;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), ConfigModeActivity.class);
		activity = (ConfigModeActivity)getInstrumentation().startActivitySync(intent);
		original = night();
		night(Configuration.UI_MODE_NIGHT_NO); // a known start: dark is a change whatever the display's
		activity.heard_night = -1;
	}

	@Override
	protected void tearDown() throws Exception {
		night(original);
		activity.finish();
		super.tearDown();
	}

	private int night() {
		final int[] night = new int[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				night[0] = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
			}
		});
		return night[0];
	}

	/* The app's post isn't ordered with the Looper's on every loop backend (gtk's glib), so this
	 * polls; a main-thread read that sees the new night comes after the whole change. */
	private void night(int night) {
		ATLTestHooks.setNight(activity, night);
		for (long end = System.currentTimeMillis() + 5000; night() != night && System.currentTimeMillis() < end;) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	// the change drops the cached drawables, which may resolve otherwise at night
	public void testCachePruned() {
		android.graphics.drawable.Drawable box = activity.getResources().getDrawable(R.drawable.box); // setUp's change pruned onCreate's
		assertSame("cached", box.getConstantState(), activity.getResources().getDrawable(R.drawable.box).getConstantState());
		night(Configuration.UI_MODE_NIGHT_YES);
		assertNotSame("pruned", box.getConstantState(), activity.getResources().getDrawable(R.drawable.box).getConstantState());
	}

	public void testNight() {
		night(Configuration.UI_MODE_NIGHT_YES);
		assertEquals(Configuration.UI_MODE_NIGHT_YES, activity.heard_night);
		assertTrue("on the main thread", activity.heard_on_main);
		assertEquals(0xffaabbcc, activity.getResources().getColor(R.color.tint));
		assertEquals(Configuration.UI_MODE_NIGHT_YES, activity.getApplicationContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
		night(Configuration.UI_MODE_NIGHT_NO);
		assertEquals(Configuration.UI_MODE_NIGHT_NO, activity.heard_night);
		assertEquals(0xff112233, activity.getResources().getColor(R.color.tint));
	}
}
