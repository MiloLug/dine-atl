package dev.dine.framework.basics;

import android.content.Context;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

@SuppressWarnings("deprecation")
public class WindowInsetsTest extends android.test.InstrumentationTestCase {
	private static WindowInsets sample() {
		return new WindowInsets.Builder()
		    .setInsets(WindowInsets.Type.systemGestures(), Insets.of(20, 0, 20, 34))
		    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 34))
		    .build();
	}

	public void testTypesAndSystemWindow() {
		WindowInsets insets = sample();
		assertEquals(Insets.of(20, 0, 20, 34), insets.getSystemGestureInsets());
		assertEquals(34, insets.getSystemWindowInsetBottom());
		assertEquals(Insets.of(20, 0, 20, 34), insets.getInsets(WindowInsets.Type.systemGestures() | WindowInsets.Type.navigationBars()));
		assertFalse(insets.isConsumed());
	}

	// as AOSP 10: consuming the system window drops every type's insets, not the stable ones
	public void testConsumeAndReplace() {
		WindowInsets consumed = sample().consumeSystemWindowInsets();
		assertEquals(0, consumed.getSystemWindowInsetBottom());
		assertEquals(Insets.NONE, consumed.getSystemGestureInsets());
		assertEquals(34, consumed.getStableInsetBottom());
		assertFalse(consumed.isConsumed());
		assertTrue(consumed.consumeStableInsets().isConsumed());
		assertSame(consumed, consumed.replaceSystemWindowInsets(1, 2, 3, 5));
		assertEquals(5, sample().replaceSystemWindowInsets(1, 2, 3, 5).getSystemWindowInsetBottom());
		assertTrue(new WindowInsets.Builder().build().isConsumed());
	}

	// only a view that fits system windows pads; the siblings after it get the consumed insets
	public void testFitsSystemWindowsPads() {
		final Context context = getInstrumentation().getTargetContext();
		final int[] result = new int[3];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				FrameLayout root = new FrameLayout(context);
				View fits = new View(context);
				fits.setFitsSystemWindows(true);
				View plain = new View(context);
				final Insets[] seen = new Insets[1];
				plain.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
					public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
						seen[0] = insets.getSystemGestureInsets();
						return insets;
					}
				});
				root.addView(fits);
				root.addView(plain);
				root.dispatchApplyWindowInsets(sample());
				result[0] = root.getPaddingBottom();
				result[1] = fits.getPaddingBottom();
				result[2] = seen[0].right;
			}
		});
		assertEquals(0, result[0]);
		assertEquals(34, result[1]);
		assertEquals(0, result[2]);
	}
}
