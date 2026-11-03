package dev.dine.framework.basics;

import android.content.res.Resources;
import android.os.Build;

public class SystemResTest extends android.test.InstrumentationTestCase {
	public void testGestureNavigation() {
		Resources system = Resources.getSystem();
		int mode = system.getIdentifier("config_navBarInteractionMode", "integer", "android");
		int inset = system.getIdentifier("config_backGestureInset", "dimen", "android");
		assertTrue(mode != 0 && inset != 0);
		assertEquals(2, system.getInteger(mode));
		assertEquals(Math.round(20 * system.getDisplayMetrics().density), system.getDimensionPixelSize(inset));
	}

	public void testApiLevel() {
		assertEquals(29, Build.VERSION.SDK_INT);
		assertEquals("from values-v24", getInstrumentation().getTargetContext().getString(R.string.level));
	}
}
