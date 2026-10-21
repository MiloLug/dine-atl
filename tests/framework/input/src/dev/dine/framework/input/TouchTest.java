package dev.dine.framework.input;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ViewGroup.dispatchTouchEvent and View.onTouchEvent as on Android: the target is pinned at DOWN
@SuppressWarnings("deprecation")
public class TouchTest extends android.test.InstrumentationTestCase {
	private final List<String> log = new ArrayList<String>();
	private Context context;

	private class Probe extends View {
		final String name;
		final boolean consumes;

		Probe(String name, boolean consumes) {
			super(context);
			this.name = name;
			this.consumes = consumes;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			Input.record(log, name, event);
			if (consumes && event.getActionMasked() == MotionEvent.ACTION_DOWN && name.equals("polite"))
				getParent().requestDisallowInterceptTouchEvent(true);
			return consumes;
		}
	}

	// intercepts from interceptAt on and consumes what it then gets
	private class Group extends FrameLayout {
		final int interceptAt;

		Group(int interceptAt) {
			super(context);
			this.interceptAt = interceptAt;
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			log.add("group intercept " + Input.name(event));
			return event.getActionMasked() == interceptAt;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			Input.record(log, "group", event);
			return true;
		}
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		context = getInstrumentation().getTargetContext();
	}

	private void onMain(Runnable runnable) {
		Input.onMain(getInstrumentation(), runnable);
	}

	private void dispatch(final View root, final MotionEvent... events) {
		onMain(new Runnable() {
			public void run() {
				for (MotionEvent event : events)
					root.dispatchTouchEvent(event);
			}
		});
	}

	private void assertLog(String... expected) {
		assertEquals(Arrays.asList(expected), log);
	}

	public void testTargetIsPinnedAtDown() {
		final FrameLayout root = new FrameLayout(context);
		onMain(new Runnable() {
			public void run() {
				Probe a = new Probe("a", true);
				Probe b = new Probe("b", true);
				root.addView(a);
				root.addView(b);
				root.layout(0, 0, 100, 100);
				a.layout(0, 0, 50, 50);
				b.layout(50, 0, 100, 50);
			}
		});
		dispatch(root, Input.touch(MotionEvent.ACTION_DOWN, 10, 10), Input.touch(MotionEvent.ACTION_MOVE, 70, 10),
		         Input.touch(MotionEvent.ACTION_UP, 70, 10));
		assertLog("a DOWN 10,10", "a MOVE 70,10", "a UP 70,10");
	}

	public void testSiblingBeneathGetsDownTheTopOneLeaves() {
		final FrameLayout root = new FrameLayout(context);
		onMain(new Runnable() {
			public void run() {
				Probe bottom = new Probe("bottom", true);
				Probe top = new Probe("top", false);
				root.addView(bottom);
				root.addView(top);
				root.layout(0, 0, 100, 100);
				bottom.layout(0, 0, 100, 100);
				top.layout(0, 0, 100, 100);
			}
		});
		dispatch(root, Input.touch(MotionEvent.ACTION_DOWN, 5, 5), Input.touch(MotionEvent.ACTION_UP, 5, 5));
		assertLog("top DOWN 5,5", "bottom DOWN 5,5", "bottom UP 5,5");
	}

	public void testInterceptCancelsTheTarget() {
		final Group group = new Group(MotionEvent.ACTION_MOVE);
		onMain(new Runnable() {
			public void run() {
				Probe child = new Probe("child", true);
				group.addView(child);
				group.layout(0, 0, 100, 100);
				child.layout(10, 10, 60, 60);
			}
		});
		dispatch(group, Input.touch(MotionEvent.ACTION_DOWN, 20, 20), Input.touch(MotionEvent.ACTION_MOVE, 30, 30),
		         Input.touch(MotionEvent.ACTION_MOVE, 40, 40), Input.touch(MotionEvent.ACTION_UP, 40, 40));
		assertLog("group intercept DOWN", "child DOWN 10,10", "group intercept MOVE", "child CANCEL 30,30",
		          "group MOVE 40,40", "group UP 40,40");
	}

	public void testDisallowInterceptKeepsTheTarget() {
		final Group group = new Group(MotionEvent.ACTION_MOVE);
		onMain(new Runnable() {
			public void run() {
				Probe child = new Probe("polite", true);
				group.addView(child);
				group.layout(0, 0, 100, 100);
				child.layout(10, 10, 60, 60);
			}
		});
		dispatch(group, Input.touch(MotionEvent.ACTION_DOWN, 20, 20), Input.touch(MotionEvent.ACTION_MOVE, 30, 30),
		         Input.touch(MotionEvent.ACTION_UP, 30, 30));
		assertLog("group intercept DOWN", "polite DOWN 10,10", "polite MOVE 20,20", "polite UP 20,20");
	}

	public void testSecondPointerSplitsToItsOwnTarget() {
		final FrameLayout root = new FrameLayout(context);
		onMain(new Runnable() {
			public void run() {
				Probe a = new Probe("a", true);
				Probe b = new Probe("b", true);
				root.addView(a);
				root.addView(b);
				root.layout(0, 0, 100, 100);
				a.layout(0, 0, 50, 100);
				b.layout(50, 0, 100, 100);
			}
		});
		int pointer1 = 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
		dispatch(root, Input.touches(MotionEvent.ACTION_DOWN, new int[] {0}, new float[] {10, 10}),
		         Input.touches(MotionEvent.ACTION_POINTER_DOWN | pointer1, new int[] {0, 1}, new float[] {10, 10, 60, 20}),
		         Input.touches(MotionEvent.ACTION_POINTER_UP | pointer1, new int[] {0, 1}, new float[] {10, 10, 60, 20}),
		         Input.touches(MotionEvent.ACTION_UP, new int[] {0}, new float[] {10, 10}));
		assertLog("a DOWN 10,10", "b DOWN 10,20", "a MOVE 10,10", "b UP 10,20", "a MOVE 10,10", "a UP 10,10");
	}

	public void testClickOnUpInside() {
		final FrameLayout root = new FrameLayout(context);
		final int[] clicks = new int[1];
		onMain(new Runnable() {
			public void run() {
				View button = new View(context);
				button.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						clicks[0]++;
					}
				});
				root.addView(button);
				root.layout(0, 0, 100, 100);
				button.layout(0, 0, 50, 50);
			}
		});
		dispatch(root, Input.touch(MotionEvent.ACTION_DOWN, 10, 10), Input.touch(MotionEvent.ACTION_UP, 10, 10));
		Input.settle(getInstrumentation(), 50);
		assertEquals(1, clicks[0]);
		dispatch(root, Input.touch(MotionEvent.ACTION_DOWN, 10, 10), Input.touch(MotionEvent.ACTION_MOVE, 90, 90),
		         Input.touch(MotionEvent.ACTION_UP, 90, 90));
		Input.settle(getInstrumentation(), 50);
		assertEquals("no click after leaving the view", 1, clicks[0]);
	}

	public void testPressedStateSelectsTheSelectorsChild() {
		final FrameLayout root = new FrameLayout(context);
		final View[] button = new View[1];
		final StateListDrawable selector = new StateListDrawable();
		final int[] colors = new int[2];
		onMain(new Runnable() {
			public void run() {
				selector.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(0xFF00C000));
				selector.addState(new int[0], new ColorDrawable(0xFF808080));
				button[0] = new View(context);
				button[0].setClickable(true);
				button[0].setBackground(selector);
				root.addView(button[0]);
				root.layout(0, 0, 100, 100);
				button[0].layout(0, 0, 50, 50);
				root.dispatchTouchEvent(Input.touch(MotionEvent.ACTION_DOWN, 10, 10));
				colors[0] = ((ColorDrawable)selector.getCurrent()).getColor();
				assertTrue(button[0].isPressed());
				root.dispatchTouchEvent(Input.touch(MotionEvent.ACTION_UP, 10, 10));
			}
		});
		Input.settle(getInstrumentation(), 200);
		onMain(new Runnable() {
			public void run() {
				colors[1] = ((ColorDrawable)selector.getCurrent()).getColor();
			}
		});
		assertEquals(0xFF00C000, colors[0]);
		assertEquals(0xFF808080, colors[1]);
		assertFalse(button[0].isPressed());
	}
}
