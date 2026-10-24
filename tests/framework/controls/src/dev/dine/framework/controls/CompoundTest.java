package dev.dine.framework.controls;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.ToggleButton;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

// CompoundButton's checked state is Java's: a click toggles, then the listeners run (AOSP 10)
@SuppressWarnings("deprecation")
public class CompoundTest extends android.test.InstrumentationTestCase {
	private final List<String> log = new ArrayList<String>();

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	private CompoundButton.OnCheckedChangeListener logChecked(final String name) {
		return new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				log.add(name + " checked " + checked);
			}
		};
	}

	private View.OnClickListener logClick(final String name) {
		return new View.OnClickListener() {
			public void onClick(View v) {
				log.add(name + " click");
			}
		};
	}

	public void testClickTogglesBeforeTheClickListener() {
		final boolean[] checked = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				CheckBox box = new CheckBox(context());
				box.setOnCheckedChangeListener(logChecked("box"));
				box.setOnClickListener(logClick("box"));
				box.performClick();
				checked[0] = box.isChecked();
				box.performClick();
				checked[1] = box.isChecked();
			}
		});
		assertTrue(checked[0]);
		assertFalse(checked[1]);
		assertEquals("[box checked true, box click, box checked false, box click]", log.toString());
	}

	public void testSetCheckedInTheListenerDoesNotRecurse() {
		final boolean[] checked = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				final Switch toggle = new Switch(context());
				toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton button, boolean isChecked) {
						log.add("switch checked " + isChecked);
						if (isChecked)
							button.setChecked(false);
					}
				});
				toggle.setChecked(true);
				checked[0] = toggle.isChecked();
			}
		});
		assertFalse(checked[0]);
		assertEquals("[switch checked true]", log.toString());
	}

	public void testRadioButtonClickNeverUnchecks() {
		final boolean[] checked = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				RadioButton radio = new RadioButton(context());
				radio.setOnCheckedChangeListener(logChecked("radio"));
				radio.performClick();
				radio.performClick();
				checked[0] = radio.isChecked();
			}
		});
		assertTrue(checked[0]);
		assertEquals("[radio checked true]", log.toString());
	}

	// the group keeps one checked through its own listener, and the app's listener on a child stays
	public void testRadioGroupKeepsOneCheckedAndTheAppsListener() {
		final int[] ids = new int[3];
		final boolean[] checked = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				RadioGroup group = new RadioGroup(context());
				RadioButton red = new RadioButton(context());
				RadioButton green = new RadioButton(context());
				group.addView(red);
				group.addView(green);
				red.setOnCheckedChangeListener(logChecked("red"));
				group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
					public void onCheckedChanged(RadioGroup g, int id) {
						log.add("group " + id);
					}
				});
				red.performClick();
				green.performClick();
				ids[0] = red.getId();
				ids[1] = green.getId();
				ids[2] = group.getCheckedRadioButtonId();
				checked[0] = red.isChecked();
				checked[1] = green.isChecked();
			}
		});
		assertFalse(checked[0]);
		assertTrue(checked[1]);
		assertEquals(ids[1], ids[2]);
		assertEquals("[red checked true, group " + ids[0] + ", red checked false, group " + ids[1] + "]", log.toString());
	}

	public void testToggleButtonShowsItsOnAndOffText() {
		final String[] texts = new String[3];
		onMain(new Runnable() {
			public void run() {
				ToggleButton toggle = new ToggleButton(context(), null, 0);
				toggle.setTextOn("lamp on");
				toggle.setTextOff("lamp off");
				toggle.setChecked(true);
				texts[0] = toggle.getText().toString();
				toggle.performClick();
				texts[1] = toggle.getText().toString();
				texts[2] = String.valueOf(toggle.isChecked());
			}
		});
		assertEquals("lamp on", texts[0]);
		assertEquals("lamp off", texts[1]);
		assertEquals("false", texts[2]);
	}

	public void testCheckedIsADrawableState() {
		final boolean[] has = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				CheckBox box = new CheckBox(context());
				has[0] = contains(box.getDrawableState(), android.R.attr.state_checked);
				box.setChecked(true);
				has[1] = contains(box.getDrawableState(), android.R.attr.state_checked);
			}
		});
		assertFalse(has[0]);
		assertTrue(has[1]);
	}

	private static boolean contains(int[] states, int state) {
		for (int s : states) {
			if (s == state)
				return true;
		}
		return false;
	}
}
