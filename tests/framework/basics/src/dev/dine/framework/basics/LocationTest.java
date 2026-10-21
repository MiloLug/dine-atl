package dev.dine.framework.basics;

import android.content.Context;
import android.graphics.Rect;
import android.widget.FrameLayout;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class LocationTest extends android.test.InstrumentationTestCase {
	private FrameLayout parent;
	private TextView child;

	// views are only touched on the Android main thread
	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		final Context context = getInstrumentation().getTargetContext();
		onMain(new Runnable() {
			public void run() {
				parent = new FrameLayout(context);
				child = new TextView(context);
				parent.addView(child);
				parent.layout(10, 20, 110, 220);
				child.layout(5, 6, 205, 56);
				parent.scrollTo(1, 2);
			}
		});
	}

	public void testLocationInWindow() {
		final int[] location = new int[2];
		onMain(new Runnable() {
			public void run() {
				child.getLocationInWindow(location);
			}
		});
		assertEquals(10 + 5 - 1, location[0]);
		assertEquals(20 + 6 - 2, location[1]);
	}

	public void testGlobalVisibleRectClipsToParent() {
		final Rect rect = new Rect();
		final boolean[] visible = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				visible[0] = child.getGlobalVisibleRect(rect);
			}
		});
		assertTrue(visible[0]);
		assertEquals(new Rect(14, 24, 110, 74), rect);
	}

	public void testOffsetDescendantRect() {
		final Rect rect = new Rect(0, 0, 4, 4);
		onMain(new Runnable() {
			public void run() {
				parent.offsetDescendantRectToMyCoords(child, rect);
			}
		});
		assertEquals(new Rect(5, 6, 9, 10), rect);
	}
}
