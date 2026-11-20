package dev.dine.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/* A text that draws nothing and a button: a tap on "move" moves the text a screen to the right with
 * offsetLeftAndRight, and nothing else changes. The smoke run reads the phone's accessibility
 * elements: with the frame pushed to UIKit the text is off screen and no longer listed. */
public class FramesActivity extends Activity {
	private static final String TAG = "widgets";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final TextView text = new TextView(this);
		text.setText("target text");
		final Button move = new Button(this);
		move.setText("move");
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		column.addView(text);
		column.addView(move);
		setContentView(column);
		move.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// after the button's own press and release have drawn
				view.postDelayed(new Runnable() {
					public void run() {
						text.offsetLeftAndRight(2 * getResources().getDisplayMetrics().widthPixels);
						Log.i(TAG, "frames moved " + text.getLeft());
					}
				}, 500);
			}
		});
		column.post(new Runnable() {
			public void run() {
				Log.i(TAG, "frames shown");
			}
		});
	}
}
