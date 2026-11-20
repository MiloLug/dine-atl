package dev.dine.views;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

/* A column of a and b: once it is laid out, a posted Runnable translates b and does nothing else,
 * and a later one says so. run-views.sh dumps the tree then (views-translated.viewtree): b is at its
 * translation with no layout request to carry it. */
public class TranslateActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		final TextView[] views = new TextView[2];
		for (int i = 0; i < views.length; i++) {
			views[i] = new TextView(this);
			views[i].setText(i == 0 ? "a" : "b");
			column.addView(views[i]);
		}
		setContentView(column);
		column.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				column.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				column.post(new Runnable() {
					public void run() {
						views[1].setTranslationX(40);
						column.postDelayed(new Runnable() {
							public void run() {
								Log.i("views", "views translated");
							}
						}, 200);
					}
				});
			}
		});
	}
}
