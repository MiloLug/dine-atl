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
}
