package dev.dine.views;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* One view for each state a view-tree dump words: checked, disabled, invisible, gone, and a scroll
 * node scrolled by 20 px. run-views.sh dumps it once laid out (views-state.viewtree). */
public class StateActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		CheckBox checked = new CheckBox(this);
		checked.setText("checked");
		checked.setChecked(true);
		column.addView(checked);
		CheckBox unchecked = new CheckBox(this);
		unchecked.setText("unchecked");
		column.addView(unchecked);
		TextView disabled = new TextView(this);
		disabled.setText("disabled");
		disabled.setEnabled(false);
		column.addView(disabled);
		TextView invisible = new TextView(this);
		invisible.setText("invisible");
		invisible.setVisibility(View.INVISIBLE);
		column.addView(invisible);
		TextView gone = new TextView(this);
		gone.setText("gone");
		gone.setVisibility(View.GONE);
		column.addView(gone);
		final ScrollView scroll = new ScrollView(this);
		LinearLayout tall = new LinearLayout(this);
		tall.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < 10; i++) {
			TextView row = new TextView(this);
			row.setText("row " + i);
			tall.addView(row);
		}
		scroll.addView(tall);
		column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100));
		setContentView(column);
		column.post(new Runnable() {
			public void run() {
				scroll.scrollTo(0, 20);
				column.postDelayed(new Runnable() {
					public void run() {
						Log.i("views", "views state");
					}
				}, 200);
			}
		});
	}
}
