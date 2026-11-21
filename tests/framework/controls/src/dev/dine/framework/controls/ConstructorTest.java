package dev.dine.framework.controls;

import android.content.Context;
import android.util.AttributeSet;

/* AOSP 10 gives each of these widgets the (Context, AttributeSet, int) and (Context, AttributeSet, int,
 * int) constructors (api/current.txt at android-10.0.0_r47); an app's own subclass, and AppCompat's
 * replacements for <CheckBox> and <RadioButton>, call the three-argument one through super. */
@SuppressWarnings("deprecation")
public class ConstructorTest extends android.test.InstrumentationTestCase {
	private static final String[] CLASSES = {
		"android.widget.AbsSeekBar", "android.widget.AbsoluteLayout", "android.widget.CheckBox", "android.widget.CheckedTextView",
		"android.widget.GridView", "android.widget.HorizontalScrollView", "android.widget.ImageButton", "android.widget.ProgressBar",
		"android.widget.RadioButton", "android.widget.RatingBar", "android.widget.ScrollView", "android.widget.SeekBar",
		"android.widget.Space", "android.widget.Spinner", "android.widget.Switch", "android.widget.Toolbar",
		"android.widget.ZoomButton", "android.view.SurfaceView", "android.widget.TabHost", "android.widget.TabWidget", "android.widget.MultiAutoCompleteTextView", "android.view.TextureView",
	};

	public void testThreeAndFourArgumentConstructorsExist() throws ClassNotFoundException {
		StringBuilder missing = new StringBuilder();
		for (String name : CLASSES) {
			Class<?> c = Class.forName(name);
			try {
				c.getConstructor(Context.class, AttributeSet.class, int.class);
			} catch (NoSuchMethodException e) {
				missing.append(" ").append(name).append("(Context, AttributeSet, int)");
			}
			try {
				c.getConstructor(Context.class, AttributeSet.class, int.class, int.class);
			} catch (NoSuchMethodException e) {
				missing.append(" ").append(name).append("(Context, AttributeSet, int, int)");
			}
		}
		assertEquals("", missing.toString());
	}
}
