package dev.dine.widgets;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* A ScrollView with a gradient background (the toolkit draws no colour for it: the view's own list
 * does) and rows three screens tall, scrolled past the first screen once laid out. The background
 * stays in the viewport on Android; a screenshot by hand shows whether the phone's draw layer follows
 * the scroll. It stays up. */
public class ScrolledActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ScrollView scroll = new ScrollView(this);
		scroll.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xffff0000, 0xff0000ff}));
		LinearLayout rows = new LinearLayout(this);
		rows.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < 80; i++) {
			TextView row = new TextView(this);
			row.setText("row " + i);
			row.setTextSize(24);
			row.setGravity(Gravity.CENTER);
			row.setHeight(60);
			rows.addView(row);
		}
		scroll.addView(rows);
		setContentView(scroll);
		scroll.post(new Runnable() {
			public void run() {
				scroll.scrollTo(0, 2500);
				Log.i("widgets", "scrolled to " + scroll.getScrollY());
			}
		});
	}
}
