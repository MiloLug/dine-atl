package dev.dine.framework.controls;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

@SuppressWarnings("deprecation")
public class ButtonTest extends android.test.InstrumentationTestCase {
	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	public void testButtonsKeepTheirText() {
		final String[] texts = new String[2];
		onMain(new Runnable() {
			public void run() {
				Button button = new Button(context());
				button.setText("push");
				texts[0] = button.getText().toString();
				CheckBox box = new CheckBox(context());
				box.setText("check");
				texts[1] = box.getText().toString();
			}
		});
		assertEquals("push", texts[0]);
		assertEquals("check", texts[1]);
	}

	// the shown text is upper-cased; getText keeps what the app set
	public void testAllCapsKeepsTheText() {
		final String[] text = new String[1];
		onMain(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				view.setText("Mixed");
				view.setAllCaps(true);
				text[0] = view.getText().toString();
			}
		});
		assertEquals("Mixed", text[0]);
	}

	public void testTextAlignmentResolves() {
		final int[] alignments = new int[3];
		onMain(new Runnable() {
			public void run() {
				LinearLayout parent = new LinearLayout(context());
				TextView child = new TextView(context());
				parent.addView(child);
				alignments[0] = child.getTextAlignment();
				parent.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
				child.setTextAlignment(View.TEXT_ALIGNMENT_INHERIT);
				alignments[1] = child.getTextAlignment();
				child.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
				alignments[2] = child.getTextAlignment();
			}
		});
		assertEquals(View.TEXT_ALIGNMENT_GRAVITY, alignments[0]);
		assertEquals(View.TEXT_ALIGNMENT_CENTER, alignments[1]);
		assertEquals(View.TEXT_ALIGNMENT_VIEW_END, alignments[2]);
	}

	public void testCompoundDrawablesOnButtons() {
		final Object[] drawables = new Object[1];
		onMain(new Runnable() {
			public void run() {
				ColorDrawable icon = new ColorDrawable(0xFF00C000);
				icon.setBounds(0, 0, 10, 10);
				CheckBox box = new CheckBox(context());
				box.setCompoundDrawables(icon, null, null, null);
				Button button = new Button(context());
				button.setCompoundDrawables(null, null, icon, null);
				drawables[0] = button.getCompoundDrawables()[2];
			}
		});
		assertTrue(drawables[0] instanceof ColorDrawable);
	}

	public void testImageButtonIsFocusable() {
		final boolean[] focusable = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				ImageButton button = new ImageButton(context(), null, 0);
				button.setImageDrawable(new ColorDrawable(0xFF00C000));
				button.setImageDrawable(null);
				focusable[0] = button.isFocusable();
			}
		});
		assertTrue(focusable[0]);
	}

	// each with its theme's default style: a style background that reaches a native not yet behind
	// a slot fails here
	public void testDefaultStyles() {
		onMain(new Runnable() {
			public void run() {
				new Button(context());
				new CheckBox(context());
				new RadioButton(context());
				new Switch(context());
				new ToggleButton(context());
				new ImageButton(context());
			}
		});
	}
}
