package dev.dine.framework.basics;

import android.content.Context;
import android.content.res.Configuration;

// a build reports AOSP's hard-keyboard or soft-keyboard set, and resources select by it
public class KeyboardConfigTest extends android.test.InstrumentationTestCase {
	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	public void testKeyboardIsOneOfAospSets() {
		Configuration config = context().getResources().getConfiguration();
		String set = config.keyboard + "/" + config.keyboardHidden + "/" + config.hardKeyboardHidden;
		boolean hard = config.keyboard == Configuration.KEYBOARD_QWERTY && config.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO
		            && config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
		boolean soft = config.keyboard == Configuration.KEYBOARD_NOKEYS && config.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO
		            && config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES;
		assertTrue("keyboard/keyboardHidden/hardKeyboardHidden " + set, hard || soft);
	}

	public void testResourcesSelectByKeyboard() {
		Configuration config = context().getResources().getConfiguration();
		assertEquals(config.keyboard == Configuration.KEYBOARD_QWERTY ? "qwerty" : "nokeys", context().getString(R.string.keyboard));
	}

	// AOSP's soft-keyboard device matches keyssoft over keysexposed
	public void testResourcesSelectByKeysHidden() {
		Configuration config = context().getResources().getConfiguration();
		assertEquals(config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO ? "exposed" : "soft", context().getString(R.string.keys));
	}
}
