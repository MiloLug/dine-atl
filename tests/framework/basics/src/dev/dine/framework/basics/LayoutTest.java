package dev.dine.framework.basics;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

// AOSP's View.layout: any frame change runs onLayout; offsets move without layout()
public class LayoutTest extends android.test.InstrumentationTestCase {
	static class Probe extends View {
		final List<String> calls = new ArrayList<>();

		Probe(android.content.Context context) {
			super(context);
		}

		@Override
		public void layout(int l, int t, int r, int b) {
			calls.add("layout " + l + "," + t);
			super.layout(l, t, r, b);
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			calls.add("onLayout " + changed + " " + l + "," + t + "," + r + "," + b);
		}
	}

	private WindowActivity start() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		return (WindowActivity)getInstrumentation().startActivitySync(intent);
	}

	public void testAMoveRunsOnLayout() {
		final WindowActivity activity = start();
		final Probe[] probe = new Probe[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0] = new Probe(activity);
				activity.root.addView(probe[0], new FrameLayout.LayoutParams(40, 30));
			}
		});
		getInstrumentation().waitForIdleSync();
		final List<String> calls = new ArrayList<>();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0].calls.clear();
				probe[0].layout(10, 20, 50, 50);
				calls.addAll(probe[0].calls);
			}
		});
		assertEquals("[layout 10,20, onLayout true 10,20,50,50]", calls.toString());
	}

	public void testOffsetsDontCallLayout() {
		final WindowActivity activity = start();
		final Probe[] probe = new Probe[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0] = new Probe(activity);
				activity.root.addView(probe[0], new FrameLayout.LayoutParams(40, 30));
			}
		});
		getInstrumentation().waitForIdleSync();
		final Object[] got = new Object[3];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0].calls.clear();
				int top = probe[0].getTop(), left = probe[0].getLeft();
				probe[0].offsetTopAndBottom(7);
				probe[0].offsetLeftAndRight(3);
				got[0] = probe[0].calls.toString();
				got[1] = probe[0].getTop() - top;
				got[2] = probe[0].getLeft() - left;
			}
		});
		assertEquals("[]", got[0]);
		assertEquals(7, got[1]);
		assertEquals(3, got[2]);
	}

	// a parent's layout pass leaves an unmoved child unchanged (AbsListView relays out on changed)
	public void testParentPassKeepsAnUnmovedChildUnchanged() {
		final WindowActivity activity = start();
		final Probe[] probe = new Probe[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0] = new Probe(activity);
				activity.root.addView(probe[0], new FrameLayout.LayoutParams(40, 30));
			}
		});
		getInstrumentation().waitForIdleSync();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0].calls.clear();
				activity.root.requestLayout();
			}
		});
		getInstrumentation().waitForIdleSync();
		final List<String> calls = new ArrayList<>();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				calls.addAll(probe[0].calls);
			}
		});
		for (String call : calls)
			assertFalse(calls.toString(), call.startsWith("onLayout true"));
	}
}
