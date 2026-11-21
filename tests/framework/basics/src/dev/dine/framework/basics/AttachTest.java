package dev.dine.framework.basics;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class AttachTest extends android.test.InstrumentationTestCase {
	private final List<String> events = new ArrayList<String>();

	private class Probe extends View {
		final String name;
		boolean logVisibility;

		Probe(String name) {
			super(getInstrumentation().getTargetContext());
			this.name = name;
		}

		@Override
		protected void onAttachedToWindow() {
			super.onAttachedToWindow();
			events.add(name + " attached " + isAttachedToWindow());
		}

		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();
			events.add(name + " detached " + isAttachedToWindow());
		}

		@Override
		protected void onWindowVisibilityChanged(int visibility) {
			super.onWindowVisibilityChanged(visibility);
			if (logVisibility)
				events.add(name + " visibility " + visibility);
		}
	}

	// RecyclerView's scrap path: detached from the parent first, removed later
	private static class Scrapping extends FrameLayout {
		Scrapping(android.content.Context context) {
			super(context);
		}

		void scrap(View child) {
			detachViewFromParent(child);
			removeDetachedView(child, false);
		}

		void attach(View child) {
			attachViewToParent(child, -1, new FrameLayout.LayoutParams(10, 10));
		}
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	public void testSubtreeFollowsTheWindow() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		final boolean[] state = new boolean[3];
		onMain(new Runnable() {
			public void run() {
				FrameLayout group = new FrameLayout(activity);
				Probe a = new Probe("a");
				Probe b = new Probe("b");
				group.addView(a);
				state[0] = a.isAttachedToWindow();
				activity.root.addView(group);
				group.addView(b);
				state[1] = b.isAttachedToWindow();
				activity.root.removeView(group);
				state[2] = a.isAttachedToWindow();
			}
		});
		assertFalse(state[0]);
		assertTrue(state[1]);
		assertFalse(state[2]);
		assertEquals("[a attached true, b attached true, a detached true, b detached true]", events.toString());
	}

	// a view that leaves a shown window hears GONE before it detaches, as AOSP's dispatchDetachedFromWindow
	public void testRemovalFromAShownWindowSendsGone() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		onMain(new Runnable() {
			public void run() {
				Probe a = new Probe("a");
				a.logVisibility = true;
				activity.root.addView(a);
				activity.root.removeView(a);
			}
		});
		assertEquals("[a attached true, a visibility 0, a visibility 8, a detached true]", events.toString());
	}

	// attachViewToParent on a view that was never detached adds it, once
	public void testAttachViewToParentAddsAViewThatWasNotDetached() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		final int[] children = new int[1];
		final boolean[] parented = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				Scrapping group = new Scrapping(activity);
				activity.root.addView(group);
				Probe c = new Probe("c");
				group.attach(c);
				children[0] = group.getChildCount();
				parented[0] = c.getParent() == group;
				activity.root.removeView(group);
			}
		});
		assertEquals(1, children[0]);
		assertTrue(parented[0]);
		assertEquals("[c attached true, c detached true]", events.toString());
	}

	public void testDetachedChildLeavesTheWindow() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		final boolean[] attached = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				Scrapping group = new Scrapping(activity);
				activity.root.addView(group);
				Probe c = new Probe("c");
				c.setKeepScreenOn(true);
				group.addView(c);
				group.scrap(c);
				attached[0] = c.isAttachedToWindow();
				activity.root.removeView(group);
			}
		});
		assertFalse(attached[0]);
		assertEquals("[c attached true, c detached true]", events.toString());
	}
}
