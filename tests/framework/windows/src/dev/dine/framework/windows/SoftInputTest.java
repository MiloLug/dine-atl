package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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

	/* The state half: InputMethodManagerService's decision when a window gains focus. The keyboard's
	 * request is observable as AOSP's mInputShown: hideSoftInputFromWindow answers whether one stood
	 * (and ends it). */
	private boolean requested(final Context context) {
		return onMain(new java.util.concurrent.Callable<Boolean>() {
			public Boolean call() {
				return ((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(null, 0);
			}
		});
	}

	// the field of an activity of no soft input state, focused and with its keyboard up
	private EditText fieldWithKeyboard(final PlainActivity activity) {
		return onMain(new java.util.concurrent.Callable<EditText>() {
			public EditText call() {
				EditText field = new EditText(activity);
				activity.setContentView(field);
				field.requestFocus();
				((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(field, 0);
				return field;
			}
		});
	}

	// a dialog of the given state with a focused field, made the way an input dialog is
	private Dialog showDialog(final PlainActivity activity, final int state) {
		Dialog dialog = onMain(new java.util.concurrent.Callable<Dialog>() {
			public Dialog call() {
				Dialog made = new Dialog(activity);
				EditText field = new EditText(activity);
				made.setContentView(field);
				made.getWindow().setSoftInputMode(state);
				field.requestFocus();
				made.show();
				return made;
			}
		});
		getInstrumentation().waitForIdleSync();
		return dialog;
	}

	public void testDialogStateVisibleOpensTheKeyboard() {
		PlainActivity activity = start(PlainActivity.class);
		showDialog(activity, WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		assertTrue(requested(activity));
		activity.finish();
	}

	public void testDialogStateAlwaysVisibleOpensTheKeyboard() {
		PlainActivity activity = start(PlainActivity.class);
		showDialog(activity, WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		assertTrue(requested(activity));
		activity.finish();
	}

	public void testDialogStateHiddenHidesTheKeyboard() {
		PlainActivity activity = start(PlainActivity.class);
		fieldWithKeyboard(activity);
		showDialog(activity, WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		assertFalse(requested(activity));
		activity.finish();
	}

	public void testDialogStateAlwaysHiddenHidesTheKeyboard() {
		PlainActivity activity = start(PlainActivity.class);
		fieldWithKeyboard(activity);
		showDialog(activity, WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		assertFalse(requested(activity));
		activity.finish();
	}

	public void testActivityStateVisibleOpensTheKeyboard() {
		PlainActivity activity = start(VisibleModeActivity.class);
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity));
		activity.finish();
	}

	// stateVisible is for going forward into the window: a return to it doesn't open the keyboard again
	public void testActivityStateVisibleOnlyGoingForward() {
		PlainActivity activity = start(VisibleModeActivity.class);
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity)); // and hides it
		PlainActivity above = start(PlainActivity.class);
		above.finish();
		getInstrumentation().waitForIdleSync();
		assertFalse("not forward", requested(activity));
		activity.finish();
	}

	// stateAlwaysVisible is not: each time the window regains the focus
	public void testActivityStateAlwaysVisibleOpensTheKeyboardAgain() {
		PlainActivity activity = start(AlwaysVisibleModeActivity.class);
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity));
		PlainActivity above = start(PlainActivity.class);
		above.finish();
		getInstrumentation().waitForIdleSync();
		assertTrue("every focus", requested(activity));
		activity.finish();
	}

	public void testActivityStateHiddenHidesTheKeyboardGoingForward() {
		PlainActivity first = start(PlainActivity.class);
		fieldWithKeyboard(first);
		PlainActivity second = start(ResizeModeActivity.class); // adjustResize|stateHidden
		getInstrumentation().waitForIdleSync();
		assertFalse(requested(second));
		second.finish();
		first.finish();
	}

	public void testActivityStateAlwaysHiddenHidesTheKeyboard() {
		PlainActivity first = start(PlainActivity.class);
		fieldWithKeyboard(first);
		PlainActivity second = start(AlwaysHiddenModeActivity.class);
		getInstrumentation().waitForIdleSync();
		assertFalse(requested(second));
		second.finish();
		first.finish();
	}

	// an unspecified state going forward into a field opens the keyboard where the window resizes for it (where it
	// pans it opens none: run-keyboard.sh's PanActivity, which no "keyboard: on" line may follow; gtk's toolkit focus requests one)
	public void testUnspecifiedStateResizeWindowOpensTheKeyboardGoingForward() {
		PlainActivity activity = start(ResizeFieldActivity.class);
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity));
		activity.finish();
	}

	// the forward bit: a return to the window opens nothing
	public void testUnspecifiedStateResizeWindowOnlyGoingForward() {
		PlainActivity activity = start(ResizeFieldActivity.class);
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity)); // and hides it
		PlainActivity above = start(PlainActivity.class);
		above.finish();
		getInstrumentation().waitForIdleSync();
		assertFalse("not forward", requested(activity));
		activity.finish();
	}

	// any other window that may use the input method hides the keyboard
	public void testActivityWithoutAFieldHidesTheKeyboard() {
		PlainActivity first = start(PlainActivity.class);
		fieldWithKeyboard(first);
		PlainActivity second = start(PlainActivity.class);
		getInstrumentation().waitForIdleSync();
		assertFalse(requested(second));
		second.finish();
		first.finish();
	}

	public void testDialogWithoutAFieldHidesTheKeyboard() {
		final PlainActivity activity = start(PlainActivity.class);
		fieldWithKeyboard(activity);
		onMain(new java.util.concurrent.Callable<Void>() {
			public Void call() {
				Dialog dialog = new Dialog(activity);
				dialog.setContentView(new TextView(activity));
				dialog.show();
				return null;
			}
		});
		getInstrumentation().waitForIdleSync();
		assertFalse(requested(activity));
		activity.finish();
	}

	// AlertController sets FLAG_ALT_FOCUSABLE_IM on an alert with no field to type into: it leaves the keyboard alone
	public void testAlertWithoutAFieldLeavesTheKeyboard() {
		final PlainActivity activity = start(PlainActivity.class);
		fieldWithKeyboard(activity);
		onMain(new java.util.concurrent.Callable<Void>() {
			public Void call() {
				new android.app.AlertDialog.Builder(activity).setMessage("message").show();
				return null;
			}
		});
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity));
		activity.finish();
	}

	// a window that can't take the focus never gains it: not even stateAlwaysHidden touches the keyboard
	public void testNotFocusableDialogLeavesTheKeyboard() {
		final PlainActivity activity = start(PlainActivity.class);
		fieldWithKeyboard(activity);
		onMain(new java.util.concurrent.Callable<Void>() {
			public Void call() {
				Dialog dialog = new Dialog(activity);
				dialog.setContentView(new TextView(activity));
				dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
				dialog.show();
				return null;
			}
		});
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity));
		activity.finish();
	}

	public void testUnchangedStateLeavesTheKeyboard() {
		final PlainActivity activity = start(PlainActivity.class);
		fieldWithKeyboard(activity);
		onMain(new java.util.concurrent.Callable<Void>() {
			public Void call() {
				Dialog dialog = new Dialog(activity);
				dialog.setContentView(new TextView(activity));
				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
				dialog.show();
				return null;
			}
		});
		getInstrumentation().waitForIdleSync();
		assertTrue(requested(activity));
		activity.finish();
	}
}
