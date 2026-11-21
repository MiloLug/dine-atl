package dev.dine.framework.text;

import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

// the keyboard's state: the class initializes on every build, showSoftInput serves a focused field
@SuppressWarnings("deprecation")
public class InputMethodTest extends android.test.InstrumentationTestCase {
	public void testShowAndHide() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), TextActivity.class);
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(intent);
		final boolean[] results = new boolean[6];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				EditText edit = new EditText(activity);
				activity.root.addView(edit);
				results[0] = imm.isActive();
				edit.requestFocus();
				results[1] = imm.showSoftInput(edit, 0);
				results[2] = imm.isActive(edit);
				results[3] = imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
				results[4] = imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
				results[5] = imm.isActive(edit);
			}
		});
		assertFalse("active before", results[0]);
		assertTrue("shown", results[1]);
		assertTrue("active", results[2]);
		assertTrue("hidden", results[3]);
		assertFalse("hidden twice", results[4]);
		assertTrue("still served", results[5]);
	}

	// as AOSP's mInputShown, a show request follows Java's focus to another field
	public void testRequestFollowsFocus() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), TextActivity.class);
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(intent);
		final boolean[] hid = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				EditText first = new EditText(activity);
				EditText second = new EditText(activity);
				activity.root.addView(first);
				activity.root.addView(second);
				first.requestFocus();
				imm.showSoftInput(first, 0);
				second.requestFocus();
				hid[0] = imm.hideSoftInputFromWindow(second.getWindowToken(), 0);
				activity.root.removeAllViews(); // the app's IMM outlives the test: no field stays served
			}
		});
		assertTrue(hid[0]);
	}

	// a view with no InputConnection (the default View) gets false, on every build
	public void testShowSoftInputOnAPlainViewIsFalse() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), TextActivity.class);
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(intent);
		final Object[] result = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				try {
					result[0] = imm.showSoftInput(new android.view.View(activity), 0);
				} catch (Throwable t) {
					result[0] = t;
				}
			}
		});
		assertEquals(Boolean.FALSE, result[0]);
	}

	// AOSP's View passes every focus gain to the keyboard: a focused field is the served one, and the app's
	// toggle shows the keyboard on it
	public void testFocusedFieldIsServedAndToggleShowsIt() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), TextActivity.class);
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(intent);
		final boolean[] results = new boolean[3];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				EditText edit = new EditText(activity);
				activity.root.addView(edit);
				edit.requestFocus();
				results[0] = imm.isActive(edit);
				imm.toggleSoftInput(0, 0);
				results[1] = imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
				results[2] = imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
				activity.root.removeAllViews();
			}
		});
		assertTrue("served after requestFocus", results[0]);
		assertTrue("the toggle's request", results[1]);
		assertFalse("hidden twice", results[2]);
	}

	// what the toolkit was told about the keyboard of a field
	private static class SpyEdit extends EditText {
		final java.util.List<Boolean> keyboard = new java.util.ArrayList<Boolean>(); // null while the constructor runs

		SpyEdit(Context context) {
			super(context);
		}

		@Override
		public boolean atlSetKeyboard(boolean shown) {
			if (keyboard != null)
				keyboard.add(shown);
			return super.atlSetKeyboard(shown);
		}
	}

	// the toolkit gave its focus to a field Java won't focus: it takes the focus back, with or without the
	// keyboard it gave (uikit: the field is first responder either way), and no request stands
	public void testRefusedToolkitFocusRecordsNothing() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), TextActivity.class);
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(intent);
		final boolean[] results = new boolean[6];
		final String[] taken = new String[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				boolean[] keyboards = {true, false};
				for (int i = 0; i < 2; i++) {
					SpyEdit edit = new SpyEdit(activity);
					edit.setFocusable(false);
					activity.root.addView(edit);
					edit.atlToolkitFocus(true, keyboards[i]);
					results[i * 3] = imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
					results[i * 3 + 1] = imm.isActive(edit);
					results[i * 3 + 2] = edit.keyboard.size() > 0 && !edit.keyboard.get(edit.keyboard.size() - 1);
					taken[i] = edit.keyboard.toString();
					activity.root.removeAllViews();
				}
			}
		});
		for (int i = 0; i < 2; i++) {
			assertFalse("a request recorded" + i, results[i * 3]);
			assertFalse("served" + i, results[i * 3 + 1]);
			assertTrue("the toolkit's focus stays, calls " + taken[i], results[i * 3 + 2]);
		}
	}
}
