package dev.dine.framework.controls;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Switch;

// an app's own subclass, and AppCompat's replacements for <CheckBox> and <RadioButton>, call the
// three-argument constructor through super
@SuppressWarnings("deprecation")
public class SubclassConstructorTest extends android.test.InstrumentationTestCase {
	private static class MyCheckBox extends CheckBox {
		MyCheckBox(Context context) {
			super(context, null, 0);
		}
	}

	private static class MyRadioButton extends RadioButton {
		MyRadioButton(Context context) {
			super(context, null, 0);
		}
	}

	private static class MySwitch extends Switch {
		MySwitch(Context context) {
			super(context, null, 0);
		}
	}

	public void testConstructThroughTheThreeArgumentSuper() {
		final Context context = getInstrumentation().getTargetContext();
		final Object[] views = new Object[3];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				views[0] = new MyCheckBox(context);
				views[1] = new MyRadioButton(context);
				views[2] = new MySwitch(context);
			}
		});
		assertTrue(views[0] instanceof CheckBox);
		assertTrue(views[1] instanceof RadioButton);
		assertTrue(views[2] instanceof Switch);
	}
}
