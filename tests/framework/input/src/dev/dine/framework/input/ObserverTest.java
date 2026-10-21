package dev.dine.framework.input;

import android.content.Intent;
import android.view.View;
import android.view.ViewTreeObserver;

@SuppressWarnings("deprecation")
public class ObserverTest extends android.test.InstrumentationTestCase {
	// a view without a window keeps one floating observer, merged into the window's on attach
	public void testFloatingObserverKeepsItsListeners() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), InputActivity.class);
		final InputActivity activity = (InputActivity)getInstrumentation().startActivitySync(intent);
		final View[] view = new View[1];
		final ViewTreeObserver[] observers = new ViewTreeObserver[2];
		final int[] layouts = new int[1];
		Input.onMain(getInstrumentation(), new Runnable() {
			public void run() {
				view[0] = new View(activity);
				observers[0] = view[0].getViewTreeObserver();
				observers[0].addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
					public void onGlobalLayout() {
						layouts[0]++;
					}
				});
				observers[1] = view[0].getViewTreeObserver();
				activity.root.addView(view[0]);
			}
		});
		Input.settle(getInstrumentation(), 100);
		assertSame(observers[0], observers[1]);
		assertTrue("the listener hears the window's layout", layouts[0] > 0);
	}
}
