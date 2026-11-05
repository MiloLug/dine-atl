package dev.dine.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* A non-touch-modal dialog over the activity: a drag scrolls its list, a touch outside reaches the
 * activity, "next" covers it with another activity, a DOWN on "hider" hides it mid-gesture,
 * "finish" ends the activity under it. */
public class StackActivity extends Activity {
	private static final String TAG = "stack";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FrameLayout root = new FrameLayout(this);
		Button under = new Button(this);
		under.setText("under");
		under.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "stack under click");
			}
		});
		root.addView(under, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
		setContentView(root);

		final Dialog dialog = new Dialog(this);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		ScrollView scroll = new ScrollView(this);
		LinearLayout lines = new LinearLayout(this);
		lines.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < 40; i++) {
			TextView line = new TextView(this);
			line.setText("line " + i);
			lines.addView(line);
		}
		scroll.addView(lines);
		scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
			public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
				if (oldY == 0 && y > 0)
					Log.i(TAG, "stack scrolled");
			}
		});
		column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120));
		column.addView(button("next", new View.OnClickListener() {
			public void onClick(View v) {
				startActivity(new Intent(StackActivity.this, CoverActivity.class));
			}
		}));
		// a DOWN on it hides the dialog: the gesture ends with a CANCEL, the dialog comes back
		TextView hider = new TextView(this);
		hider.setText("hider");
		hider.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					Log.i(TAG, "stack hider down");
					dialog.hide();
				} else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
					Log.i(TAG, "stack hider cancel");
					v.postDelayed(new Runnable() {
						public void run() {
							dialog.show();
							Log.i(TAG, "stack shown again");
						}
					}, 100);
				}
				return true;
			}
		});
		column.addView(hider);
		column.addView(button("finish", new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		}));
		dialog.setContentView(column);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
		dialog.show();
		Log.i(TAG, "stack shown");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "stack resumed");
	}

	private Button button(String text, View.OnClickListener listener) {
		Button button = new Button(this);
		button.setText(text);
		button.setOnClickListener(listener);
		return button;
	}
}
