package dev.dine.framework.graphics;

import android.content.Context;
import android.content.res.TypedArray;

public class ThemedColorTest extends android.test.InstrumentationTestCase {
	public void testContextColorsUseTheTheme() {
		Context context = getInstrumentation().getTargetContext();
		TypedArray a = context.getTheme().obtainStyledAttributes(new int[] {android.R.attr.colorForeground});
		int foreground = a.getColor(0, 0);
		a.recycle();
		assertTrue(foreground != 0);
		assertEquals(foreground, context.getColorStateList(R.color.themed).getDefaultColor());
		assertEquals(foreground, context.getColor(R.color.themed));
	}
}
