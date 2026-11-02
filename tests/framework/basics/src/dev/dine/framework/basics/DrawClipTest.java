package dev.dine.framework.basics;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// a view's draw starts at its own size, whatever its last draw clipped
@SuppressWarnings("deprecation")
public class DrawClipTest extends android.test.InstrumentationTestCase {
	private static void waitFor(List<?> list, int size) throws InterruptedException {
		for (int i = 0; i < 100 && list.size() < size; i++)
			Thread.sleep(50);
		assertTrue("draws: " + list.size(), list.size() >= size);
	}

	public void testEachDrawStartsAtTheViewsSize() throws Exception {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		final List<Rect> bounds = Collections.synchronizedList(new ArrayList<Rect>());
		final View[] view = new View[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view[0] = new View(activity) {
					@Override
					public void onDraw(Canvas canvas) {
						bounds.add(canvas.getClipBounds());
						canvas.clipRect(0, 0, 1, 1);
					}
				};
				activity.root.addView(view[0], new FrameLayout.LayoutParams(40, 30));
			}
		});
		waitFor(bounds, 1);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view[0].invalidate();
			}
		});
		waitFor(bounds, 2);
		assertEquals(new Rect(0, 0, 40, 30), bounds.get(0));
		assertEquals(new Rect(0, 0, 40, 30), bounds.get(bounds.size() - 1));
	}
}
