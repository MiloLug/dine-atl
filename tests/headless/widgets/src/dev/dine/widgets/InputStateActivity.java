package dev.dine.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/* For smoke text-gtk: fields in a disabled ScrollView or transparent still take input, a disabled
 * one doesn't, and one under blocksDescendants refuses the focus a tap gives it. */
public class InputStateActivity extends Activity {
	private static final String TAG = "inputstate";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);

		ScrollView scroll = new ScrollView(this);
		scroll.setEnabled(false);
		scroll.addView(field("scrolled"));
		column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120));

		EditText faded = field("faded");
		faded.setAlpha(0);
		column.addView(faded);

		EditText off = field("off");
		off.setEnabled(false);
		column.addView(off);

		LinearLayout blocked = new LinearLayout(this);
		blocked.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
		blocked.addView(field("blocked"));
		column.addView(blocked);

		setContentView(column);
		Log.i(TAG, "inputstate shown");
	}

	private EditText field(final String label) {
		EditText field = new EditText(this);
		field.setHint(label);
		field.setInputType(InputType.TYPE_CLASS_TEXT);
		field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				Log.i(TAG, "inputstate focus " + label + " " + hasFocus);
			}
		});
		return field;
	}
}
