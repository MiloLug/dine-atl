package dev.dine.views;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

/* A column of a, b and c that loses b once it is laid out, then stays up: run-views.sh dumps the
 * tree the removal leaves (views-removed.viewtree), where c has moved up. */
public class RemovedActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		for (String text : new String[] {"a", "b", "c"}) {
			TextView view = new TextView(this);
			view.setText(text);
			column.addView(view);
		}
		setContentView(column);
		column.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			private int passes;

			public void onGlobalLayout() {
				passes++;
				if (passes == 1)
					column.removeViewAt(1);
				else if (passes == 2)
					Log.i("views", "views removed");
			}
		});
	}
}
