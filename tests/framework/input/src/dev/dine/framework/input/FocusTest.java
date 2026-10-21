package dev.dine.framework.input;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// focus as on Android: one focused view per tree, touch mode, FocusFinder
@SuppressWarnings("deprecation")
public class FocusTest extends android.test.InstrumentationTestCase {
	private final List<String> log = new ArrayList<String>();
	private Context context;
	private LinearLayout column;
	private View a, b, c;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		context = getInstrumentation().getTargetContext();
		onMain(new Runnable() {
			public void run() {
				column = new LinearLayout(context);
				column.setOrientation(LinearLayout.VERTICAL);
				a = row("a");
				b = row("b");
				c = row("c");
				column.layout(0, 0, 100, 300);
				a.layout(0, 0, 100, 100);
				b.layout(0, 100, 100, 200);
				c.layout(0, 200, 100, 300);
			}
		});
	}

	private View row(final String name) {
		View row = new View(context);
		row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				log.add(name + " " + hasFocus);
			}
		});
		column.addView(row);
		return row;
	}

	private void onMain(Runnable runnable) {
		Input.onMain(getInstrumentation(), runnable);
	}

	private void focusable(boolean inTouchMode) {
		for (View row : new View[] {a, b, c}) {
			row.setFocusable(true);
			if (inTouchMode)
				row.setFocusableInTouchMode(true);
		}
	}

	public void testTouchModeRefusesPlainFocusables() {
		getInstrumentation().setInTouchMode(true);
		final boolean[] took = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				focusable(false);
				assertTrue(a.isInTouchMode());
				took[0] = a.requestFocus();
				a.setFocusableInTouchMode(true);
				took[1] = a.requestFocus();
			}
		});
		assertFalse(took[0]);
		assertTrue(took[1]);
	}

	public void testFocusMovesAndListenersHearIt() {
		getInstrumentation().setInTouchMode(true);
		onMain(new Runnable() {
			public void run() {
				focusable(true);
				assertTrue(a.requestFocus());
				assertTrue(a.isFocused());
				assertSame(a, column.findFocus());
				assertTrue(column.hasFocus());
				assertSame(a, column.getFocusedChild());
				assertTrue(b.requestFocus());
				assertFalse(a.isFocused());
				assertSame(b, column.findFocus());
			}
		});
		assertEquals(Arrays.asList("a true", "a false", "b true"), log);
	}

	// AbsListView's onFocusChanged reaches View's, which calls the listener
	public void testListViewHearsItsFocus() {
		getInstrumentation().setInTouchMode(true);
		final boolean[] took = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				focusable(true);
				ListView list = new ListView(context);
				list.setOnFocusChangeListener(new View.OnFocusChangeListener() {
					public void onFocusChange(View v, boolean hasFocus) {
						log.add("list " + hasFocus);
					}
				});
				// an empty AdapterView isn't focusable
				list.setAdapter(new ArrayAdapter<String>(context, 0, new String[] {"x"}));
				list.setFocusableInTouchMode(true);
				column.addView(list);
				took[0] = list.requestFocus();
				took[1] = a.requestFocus();
			}
		});
		assertTrue(took[0]);
		assertTrue(took[1]);
		assertEquals(Arrays.asList("list true", "list false", "a true"), log);
	}

	// targetSdk 27: a cleared focus goes back to the tree's first focusable
	public void testClearFocusRefocusesFromTheRoot() {
		getInstrumentation().setInTouchMode(true);
		final View[] focus = new View[1];
		onMain(new Runnable() {
			public void run() {
				focusable(true);
				b.requestFocus();
				b.clearFocus();
				focus[0] = column.findFocus();
			}
		});
		assertFalse(b.isFocused());
		assertSame(a, focus[0]);
	}

	public void testRemovedFocusedChildLosesFocus() {
		getInstrumentation().setInTouchMode(true);
		onMain(new Runnable() {
			public void run() {
				b.setFocusable(true);
				b.setFocusableInTouchMode(true);
				b.requestFocus();
				column.removeView(b);
			}
		});
		assertFalse(b.isFocused());
		assertNull(column.findFocus());
	}

	public void testBlockedDescendantsTakeNoFocus() {
		getInstrumentation().setInTouchMode(true);
		final boolean[] took = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				focusable(true);
				column.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
				took[0] = b.requestFocus();
			}
		});
		assertFalse(took[0]);
		assertNull(column.findFocus());
	}

	public void testFocusSearch() {
		getInstrumentation().setInTouchMode(false);
		final View[] found = new View[3];
		onMain(new Runnable() {
			public void run() {
				focusable(false);
				a.requestFocus();
				found[0] = a.focusSearch(View.FOCUS_FORWARD);
				found[1] = b.focusSearch(View.FOCUS_DOWN);
				found[2] = c.focusSearch(View.FOCUS_UP);
			}
		});
		getInstrumentation().setInTouchMode(true);
		assertSame(b, found[0]);
		assertSame(c, found[1]);
		assertSame(b, found[2]);
	}

	public void testFocusedStateSelectsTheSelectorsChild() {
		getInstrumentation().setInTouchMode(true);
		final int[] colors = new int[2];
		onMain(new Runnable() {
			public void run() {
				focusable(true);
				StateListDrawable selector = new StateListDrawable();
				selector.addState(new int[] {android.R.attr.state_focused}, new ColorDrawable(0xFF0000C0));
				selector.addState(new int[0], new ColorDrawable(0xFF808080));
				b.setBackground(selector);
				b.requestFocus();
				colors[0] = ((ColorDrawable)selector.getCurrent()).getColor();
				a.requestFocus();
				colors[1] = ((ColorDrawable)selector.getCurrent()).getColor();
			}
		});
		assertEquals(0xFF0000C0, colors[0]);
		assertEquals(0xFF808080, colors[1]);
	}
}
