package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

// a Dialog's window as AOSP's dialog theme and window manager make it
@SuppressWarnings("deprecation")
public class DialogTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
	}

	// themeResId 0: the activity theme's dialogTheme; outside touches as the theme says
	private Dialog shown(final int themeResId) {
		final Dialog[] dialog = new Dialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity, themeResId);
				TextView content = new TextView(activity); // wraps: a plain View fills an AT_MOST width
				content.setText("dialog");
				dialog[0].setContentView(content);
				dialog[0].show();
			}
		});
		getInstrumentation().waitForIdleSync();
		return dialog[0];
	}

	private void dismiss(final Dialog dialog) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.dismiss();
			}
		});
	}

	private Dialog shown() {
		return shown(0);
	}

	public void testFloatingCenteredDimmed() {
		Dialog dialog = shown();
		WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
		assertEquals(Gravity.CENTER, params.gravity);
		assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width);
		assertTrue("dim behind", (params.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0);
		dismiss(dialog);
	}

	// the dialog theme's windowCloseOnTouchOutside (config_closeDialogWhenTouchOutside)
	public void testTouchOutsideCancels() {
		final Dialog dialog = shown();
		final boolean[] showing = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				long now = SystemClock.uptimeMillis();
				MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, -100, -100, 0);
				MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, -100, -100, 0);
				dialog.dispatchTouchEvent(down);
				dialog.dispatchTouchEvent(up);
				showing[0] = dialog.isShowing();
			}
		});
		assertFalse("still showing after a touch outside", showing[0]);
	}

	// Theme.DeviceDefault.Dialog.MinWidth: a WRAP_CONTENT window at least windowMinWidthMajor/Minor wide
	public void testMinWidthTheme() throws InterruptedException {
		Dialog dialog = shown(android.R.style.Theme_DeviceDefault_Dialog_MinWidth);
		View decor = dialog.getWindow().getDecorView();
		waitForLayout(decor);
		int screen = activity.getWindow().getDecorView().getWidth();
		assertTrue("width " + decor.getWidth() + " of " + screen, decor.getWidth() >= screen / 2);
		dismiss(dialog);
	}

	// the window's layout pass runs after idle on records: poll as CTS's PollingCheck does
	private static void waitForLayout(View view) throws InterruptedException {
		for (int i = 0; i < 250 && view.getWidth() == 0; i++)
			Thread.sleep(20);
	}

	public void testWindowIsCentered() throws InterruptedException {
		Dialog dialog = shown();
		View decor = dialog.getWindow().getDecorView();
		waitForLayout(decor);
		View screen = activity.getWindow().getDecorView();
		int[] location = new int[2];
		decor.getLocationOnScreen(location);
		assertTrue("laid out " + decor.getWidth() + "x" + decor.getHeight(), decor.getWidth() > 0 && decor.getHeight() > 0);
		assertEquals((screen.getWidth() - decor.getWidth()) / 2, location[0]);
		assertEquals((screen.getHeight() - decor.getHeight()) / 2, location[1]);
		dismiss(dialog);
	}
}
