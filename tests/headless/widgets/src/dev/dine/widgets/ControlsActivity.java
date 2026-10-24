package dev.dine.widgets;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

/* The Button family and ImageButton, for run-controls.sh on headless and checks by eye on gtk and
 * iOS; labels differ case-insensitively, as the debug agent matches them. */
public class ControlsActivity extends Activity {
	private static final String TAG = "controls";

	private int dp(int value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);

		Button push = new Button(this);
		push.setText("push");
		push.setAllCaps(true);
		push.setOnClickListener(clickLogger("push"));
		column.addView(push);

		Button locked = new Button(this);
		locked.setText("locked");
		locked.setOnClickListener(clickLogger("locked"));
		locked.setEnabled(false);
		column.addView(locked);

		CheckBox check = new CheckBox(this);
		check.setText("check");
		check.setOnCheckedChangeListener(checkedLogger("check"));
		column.addView(check);

		RadioGroup group = new RadioGroup(this);
		final RadioButton red = radio(group, "red");
		radio(group, "green");
		group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup g, int id) {
				Log.i(TAG, "controls radio " + (id == red.getId() ? "red" : "green"));
			}
		});
		column.addView(group);

		Switch toggle = new Switch(this);
		toggle.setText("switch");
		toggle.setOnCheckedChangeListener(checkedLogger("switch"));
		column.addView(toggle);

		final ToggleButton lamp = new ToggleButton(this);
		lamp.setTextOn("lamp on");
		lamp.setTextOff("lamp off");
		lamp.setText("lamp off");
		lamp.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "controls click toggle " + lamp.getText());
			}
		});
		column.addView(lamp);

		ImageButton image = new ImageButton(this);
		image.setImageDrawable(new ColorDrawable(0xFF00C000));
		image.setOnClickListener(clickLogger("image"));
		column.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));

		TextView centered = new TextView(this);
		centered.setText("centered");
		centered.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
		column.addView(centered, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		// ATL's Html.fromHtml keeps the tags: markup text, upper-cased around them
		TextView marked = new TextView(this);
		marked.setText(new SpannableString("<b>bold</b> &amp; caps"));
		marked.setAllCaps(true);
		column.addView(marked);

		CheckBox icon = new CheckBox(this);
		icon.setText("icon");
		ColorDrawable square = new ColorDrawable(0xFF2196F3);
		square.setBounds(0, 0, dp(24), dp(24));
		icon.setCompoundDrawables(square, null, null, null);
		column.addView(icon);

		setContentView(column);
	}

	private RadioButton radio(RadioGroup group, String name) {
		RadioButton radio = new RadioButton(this);
		radio.setText(name);
		group.addView(radio);
		radio.setOnCheckedChangeListener(checkedLogger(name));
		return radio;
	}

	private View.OnClickListener clickLogger(final String name) {
		return new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "controls click " + name);
			}
		};
	}

	private CompoundButton.OnCheckedChangeListener checkedLogger(final String name) {
		return new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				Log.i(TAG, "controls checked " + name + " " + checked);
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "controls shown");
	}
}
