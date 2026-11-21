package dev.dine.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/* Views in a theme whose text appearance has no color, for run-controls.sh: the toolkit gets no text
 * color for them, and a hint shows in the hint color the theme or its attribute gives. */
public class BareColorActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);

		TextView bare = new TextView(this);
		bare.setText("bare");
		column.addView(bare);

		Button button = new Button(this);
		button.setText("bare button");
		column.addView(button);

		EditText field = new EditText(this);
		field.setHint("bare field");
		column.addView(field);

		TextView hinted = new TextView(this);
		hinted.setHint("pick");
		column.addView(hinted);
		Log.i("bare", "bare hint " + String.format("#%08x", hinted.getCurrentHintTextColor()));

		// the hint and its color from attributes
		android.view.LayoutInflater.from(this).inflate(R.layout.bare_hinted, column, true);

		setContentView(column);
	}
}
