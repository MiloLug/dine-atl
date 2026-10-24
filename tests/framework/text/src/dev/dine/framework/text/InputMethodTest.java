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
}
