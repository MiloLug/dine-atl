package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

// the soft input mode a window starts with and takes, and the scroll containers an unspecified one resizes for
public class SoftInputTest extends android.test.InstrumentationTestCase {
	private static final int ADJUST = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;

	private <T> T onMain(final java.util.concurrent.Callable<T> body) {
		final Object[] result = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				try {
					result[0] = body.call();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		@SuppressWarnings("unchecked")
		T value = (T)result[0];
		return value;
	}

	private PlainActivity start(Class<? extends PlainActivity> cls) {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), cls);
		return (PlainActivity)getInstrumentation().startActivitySync(intent);
	}

	public void testManifestMode() {
		final PlainActivity activity = start(ResizeModeActivity.class);
		int mode = onMain(new java.util.concurrent.Callable<Integer>() {
			public Integer call() {
				return activity.getWindow().getAttributes().softInputMode;
			}
		});
		activity.finish();
		assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN, mode);
	}

	// AOSP's: an unspecified state leaves the mode as it was
	public void testSetter() {
		final PlainActivity activity = start(PlainActivity.class);
		int[] modes = onMain(new java.util.concurrent.Callable<int[]>() {
			public int[] call() {
				activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
				int set = activity.getWindow().getAttributes().softInputMode;
				activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
				return new int[] {set, activity.getWindow().getAttributes().softInputMode};
			}
		});
		activity.finish();
		assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN, modes[0]);
		assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN, modes[1]);
	}

	// Theme.Material.Dialog's windowSoftInputMode: adjustPan
	public void testDialogThemeMode() {
		final PlainActivity activity = start(PlainActivity.class);
		int mode = onMain(new java.util.concurrent.Callable<Integer>() {
			public Integer call() {
				Dialog dialog = new Dialog(activity);
				dialog.setContentView(new TextView(activity));
				return dialog.getWindow().getAttributes().softInputMode;
			}
		});
		activity.finish();
		assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN, mode & ADJUST);
	}

	// AOSP's: a view with a vertical scroll bar is one unless it says
	public void testScrollContainer() {
		final PlainActivity activity = start(PlainActivity.class);
		boolean[] containers = onMain(new java.util.concurrent.Callable<boolean[]>() {
			public boolean[] call() {
				View cleared = new ScrollView(activity);
				cleared.setScrollContainer(false);
				View set = new View(activity);
				set.setScrollContainer(true);
				return new boolean[] {new ScrollView(activity).isScrollContainer(), new ListView(activity).isScrollContainer(),
				                      new View(activity).isScrollContainer(), new TextView(activity).isScrollContainer(),
				                      cleared.isScrollContainer(), set.isScrollContainer()};
			}
		});
		activity.finish();
		assertTrue("ScrollView", containers[0]);
		assertTrue("ListView", containers[1]);
		assertFalse("View", containers[2]);
		assertFalse("TextView", containers[3]);
		assertFalse("cleared", containers[4]);
		assertTrue("set", containers[5]);
	}
}
