package dev.dine.framework.controls;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// getButton's Buttons are the alert's: their click is the dialog's listener and then dismiss,
// unless the app sets its own OnClickListener (AOSP's AlertController)
@SuppressWarnings("deprecation")
public class AlertButtonTest extends android.test.InstrumentationTestCase {
	private final List<String> log = new ArrayList<String>();
	private final CountDownLatch dismissed = new CountDownLatch(1);
	private ControlsActivity activity;
	private AlertDialog dialog;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), ControlsActivity.class);
		activity = (ControlsActivity)getInstrumentation().startActivitySync(intent);
		onMain(new Runnable() {
			public void run() {
				DialogInterface.OnClickListener logClick = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int which) {
						log.add("onClick " + which);
					}
				};
				dialog = new AlertDialog.Builder(activity)
				             .setTitle("t")
				             .setPositiveButton("Yes", logClick)
				             .setNegativeButton("No", logClick)
				             .setOnDismissListener(new DialogInterface.OnDismissListener() {
					             public void onDismiss(DialogInterface d) {
						             dismissed.countDown();
					             }
				             })
				             .show();
			}
		});
	}

	// the backend's close comes 10 ms after dismiss(), and its output must not land inside the runner's
	@Override
	protected void tearDown() throws Exception {
		onMain(new Runnable() {
			public void run() {
				dialog.dismiss();
			}
		});
		assertTrue(dismissed.await(10, TimeUnit.SECONDS));
		getInstrumentation().waitForIdleSync();
		super.tearDown();
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	// Dialog.atlDialogResult, the ui backend's entry
	private static boolean result(AlertDialog dialog, int which) {
		try {
			Method method = AlertDialog.class.getDeclaredMethod("atlDialogResult", int.class);
			method.setAccessible(true);
			return (Boolean)method.invoke(dialog, which);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	public void testButtonsCarryTheLabels() {
		final Object[] state = new Object[3];
		onMain(new Runnable() {
			public void run() {
				state[0] = dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString();
				state[1] = dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getText().toString();
				state[2] = dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getVisibility();
			}
		});
		assertEquals("Yes", state[0]);
		assertEquals("No", state[1]);
		assertEquals(View.GONE, state[2]);
	}

	public void testClickRunsTheListenerAndDismisses() {
		final boolean[] showing = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
				showing[0] = dialog.isShowing();
			}
		});
		assertEquals("[onClick -1]", log.toString());
		assertFalse(showing[0]);
	}

	public void testOwnClickListenerKeepsItOpen() {
		final boolean[] state = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				Button no = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
				no.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						log.add("own click");
					}
				});
				state[0] = result(dialog, DialogInterface.BUTTON_NEGATIVE);
				state[1] = dialog.isShowing();
			}
		});
		assertEquals("[own click]", log.toString());
		assertTrue(state[0]);
		assertTrue(state[1]);
	}

	public void testDisabledOrGoneButtonIgnoresResults() {
		final boolean[] state = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
				dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
				state[0] = result(dialog, DialogInterface.BUTTON_POSITIVE);
				state[1] = result(dialog, DialogInterface.BUTTON_NEGATIVE);
			}
		});
		assertEquals("[]", log.toString());
		assertTrue(state[0]);
		assertTrue(state[1]);
	}
}
