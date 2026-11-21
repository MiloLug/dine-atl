package dev.dine.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

/* Four scroll nodes, each 200 px wide and 100 px tall, at x 20 and y 20, 140, 260 and 380, with a
 * child that logs every touch action it gets as "pan <name> <action>" (return true), and each scroll
 * offset as "pan scroll <name> <offset>":
 * - claim: a ScrollView with taller content whose child asks its parent not to intercept on its DOWN;
 * - fits: a ScrollView whose content fits;
 * - wide: a HorizontalScrollView whose content fits;
 * - plain: a ScrollView with taller content and a child that asks nothing.
 * run-pan.sh drags each. */
public class PanActivity extends Activity {
	private static final String TAG = "widgets";

	private TextView child(final String name, final int width, final int height, final boolean claim) {
		TextView row = new TextView(this) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				setMeasuredDimension(width, height);
			}
		};
		row.setText(name);
		row.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (claim && event.getActionMasked() == MotionEvent.ACTION_DOWN)
					v.getParent().requestDisallowInterceptTouchEvent(true);
				Log.i(TAG, "pan " + name + " " + MotionEvent.actionToString(event.getAction()));
				return true;
			}
		});
		return row;
	}

	private void place(FrameLayout content, View scroller, int top) {
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(200, 100, Gravity.LEFT | Gravity.TOP);
		params.leftMargin = 20;
		params.topMargin = top;
		content.addView(scroller, params);
	}

	private ScrollView vertical(final String name, int contentHeight, boolean claim) {
		ScrollView scroll = new ScrollView(this) {
			@Override
			protected void onScrollChanged(int l, int t, int oldl, int oldt) {
				super.onScrollChanged(l, t, oldl, oldt);
				Log.i(TAG, "pan scroll " + name + " " + t);
			}
		};
		scroll.addView(child(name, 200, contentHeight, claim), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return scroll;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FrameLayout content = new FrameLayout(this);
		place(content, vertical("claim", 400, true), 20);
		place(content, vertical("fits", 50, false), 140);
		HorizontalScrollView wide = new HorizontalScrollView(this);
		wide.addView(child("wide", 100, 100, false), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
		place(content, wide, 260);
		place(content, vertical("plain", 400, false), 380);
		setContentView(content);
		Log.i(TAG, "pan shown");
	}
}
