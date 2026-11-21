package dev.dine.framework.windows;

import android.atl.ATLTestHooks;
import android.content.Intent;
import android.app.Dialog;
import android.content.res.Configuration;
import android.view.View;
import android.view.WindowManager;

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

	// a view that counts the configuration its window gives it
	private static class Counter extends View {
		volatile int calls;
		volatile int night = -1;

		Counter(android.content.Context context) {
			super(context);
		}

		@Override
		protected void onConfigurationChanged(Configuration config) {
			calls++;
			night = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		}
	}

	private Dialog showDialog(final PlainActivity owner, final Counter view, final boolean hidden) {
		final Dialog[] dialog = new Dialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(owner);
				dialog[0].setContentView(view);
				dialog[0].show();
				if (hidden)
					dialog[0].hide();
			}
		});
		getInstrumentation().waitForIdleSync();
		return dialog[0];
	}

	// AOSP's ViewRootImpl gives its view tree the configuration: a dialog's of an activity that handles uiMode
	public void testDialogOfAnActivityThatHandlesUiMode() {
		Counter view = new Counter(activity);
		Counter hidden = new Counter(activity);
		showDialog(activity, view, false);
		showDialog(activity, hidden, true);
		night(Configuration.UI_MODE_NIGHT_YES);
		assertEquals("one call", 1, view.calls);
		assertEquals(Configuration.UI_MODE_NIGHT_YES, view.night);
		assertEquals("a hidden dialog is added still", 1, hidden.calls);
	}

	// its activity's own rule: one that doesn't handle uiMode keeps its configuration, and its dialog does
	public void testDialogOfAnActivityThatDoesNotHandleUiMode() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		PlainActivity plain = (PlainActivity)getInstrumentation().startActivitySync(intent);
		Counter view = new Counter(plain);
		showDialog(plain, view, false);
		night(Configuration.UI_MODE_NIGHT_YES);
		getInstrumentation().waitForIdleSync();
		assertEquals(0, view.calls);
	}

	// a window of no activity (its own, as an application overlay) takes the change
	public void testOverlayWindowHearsIt() {
		final Counter view = new Counter(activity);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				WindowManager.LayoutParams params = new WindowManager.LayoutParams(10, 10, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0,
				                                                                   android.graphics.PixelFormat.TRANSLUCENT);
				activity.getWindowManager().addView(view, params);
			}
		});
		night(Configuration.UI_MODE_NIGHT_YES);
		assertEquals(1, view.calls);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				activity.getWindowManager().removeView(view);
			}
		});
	}
}
