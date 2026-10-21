package dev.dine.framework.input;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

// Informational: what a MOVE costs through ten levels; it prints, it doesn't grade.
@SuppressWarnings("deprecation")
public class DispatchCostTest extends android.test.InstrumentationTestCase {
	private static final int DEPTH = 10;
	private static final int MOVES = 1000;

	public void testMoveCost() {
		final Context context = getInstrumentation().getTargetContext();
		final long[] nanos = new long[1];
		Input.onMain(getInstrumentation(), new Runnable() {
			public void run() {
				ViewGroup root = new FrameLayout(context);
				root.layout(0, 0, 1000, 1000);
				ViewGroup parent = root;
				for (int i = 1; i < DEPTH; i++) {
					FrameLayout child = new FrameLayout(context);
					parent.addView(child);
					child.layout(0, 0, 1000, 1000);
					parent = child;
				}
				View leaf = new View(context) {
					@Override
					public boolean onTouchEvent(MotionEvent event) {
						return true;
					}
				};
				parent.addView(leaf);
				leaf.layout(0, 0, 1000, 1000);
				root.dispatchTouchEvent(Input.touch(MotionEvent.ACTION_DOWN, 10, 10));
				long start = System.nanoTime();
				for (int i = 0; i < MOVES; i++)
					root.dispatchTouchEvent(Input.touch(MotionEvent.ACTION_MOVE, 10 + i % 100, 10));
				nanos[0] = System.nanoTime() - start;
				root.dispatchTouchEvent(Input.touch(MotionEvent.ACTION_UP, 10, 10));
			}
		});
		Log.i("input", "dispatch cost: " + nanos[0] / MOVES / 1000 + " us per MOVE through " + DEPTH + " levels");
	}
}
