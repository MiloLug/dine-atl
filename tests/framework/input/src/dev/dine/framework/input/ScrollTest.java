package dev.dine.framework.input;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ScrollView's offset: Java's mirror of the toolkit's, clamped to the child, reported once per change
@SuppressWarnings("deprecation")
public class ScrollTest extends android.test.InstrumentationTestCase {
	private final List<String> log = new ArrayList<String>();
	private ScrollView scroll;

	private void onMain(Runnable runnable) {
		Input.onMain(getInstrumentation(), runnable);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		final Context context = getInstrumentation().getTargetContext();
		onMain(new Runnable() {
			public void run() {
				scroll = new ScrollView(context) {
					@Override
					protected void onScrollChanged(int l, int t, int oldl, int oldt) {
						super.onScrollChanged(l, t, oldl, oldt);
						log.add("scrolled " + l + "," + t + " from " + oldl + "," + oldt);
					}
				};
				View child = new View(context);
				child.setMinimumHeight(500); // ScrollView measures it UNSPECIFIED in height
				scroll.addView(child, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
				int exactly100 = View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY);
				scroll.measure(exactly100, exactly100);
				scroll.layout(0, 0, 100, 100);
			}
		});
	}

	// what the ui backend reports after the toolkit scrolled
	private void toolkitScrolled(final int x, final int y) {
		onMain(new Runnable() {
			public void run() {
				try {
					Method scrolled = View.class.getDeclaredMethod("atlScrolled", int.class, int.class);
					scrolled.setAccessible(true);
					scrolled.invoke(scroll, x, y);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	public void testScrollToClampsToTheChild() {
		final int[] y = new int[1];
		onMain(new Runnable() {
			public void run() {
				scroll.scrollTo(0, 1000);
				y[0] = scroll.getScrollY();
			}
		});
		assertEquals(400, y[0]);
		assertEquals(Arrays.asList("scrolled 0,400 from 0,0"), log);
	}

	public void testTheToolkitsEchoIsDropped() {
		onMain(new Runnable() {
			public void run() {
				scroll.scrollTo(0, 100);
			}
		});
		toolkitScrolled(0, 100);
		toolkitScrolled(0, 150);
		assertEquals(Arrays.asList("scrolled 0,100 from 0,0", "scrolled 0,150 from 0,100"), log);
		assertEquals(150, scroll.getScrollY());
	}

	public void testScrollChangeListener() {
		final List<String> heard = new ArrayList<String>();
		onMain(new Runnable() {
			public void run() {
				scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
					public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
						heard.add(x + "," + y + " from " + oldX + "," + oldY);
					}
				});
				scroll.fullScroll(View.FOCUS_DOWN);
				scroll.smoothScrollTo(0, 0);
			}
		});
		assertEquals(Arrays.asList("0,400 from 0,0", "0,0 from 0,400"), heard);
	}
}
