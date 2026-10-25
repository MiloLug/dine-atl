package dev.dine.framework.text;

import android.view.View;
import android.widget.TextView;

/* Line limits in a TextView's measured height, compared within one build (each has its own font). */
@SuppressWarnings("deprecation")
public class LineLimitTest extends android.test.InstrumentationTestCase {
	private interface Limit {
		void apply(TextView view);
	}

	private static final Limit NONE = new Limit() {
		public void apply(TextView view) {}
	};

	private final int[] height = new int[1];

	private int heightOf(final String text, final Limit limit) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(getInstrumentation().getTargetContext());
				view.setText(text);
				limit.apply(view);
				view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
				height[0] = view.getMeasuredHeight();
			}
		});
		return height[0];
	}

	public void testMaxLinesCaps() {
		int five = heightOf("1\n2\n3\n4\n5", NONE);
		int capped = heightOf("1\n2\n3\n4\n5", new Limit() {
			public void apply(TextView view) {
				view.setMaxLines(2);
			}
		});
		assertEquals(heightOf("1\n2", NONE), capped);
		assertTrue(capped < five);
	}

	public void testMinLinesPads() {
		assertEquals(heightOf("1\n2\n3", NONE), heightOf("1", new Limit() {
			public void apply(TextView view) {
				view.setMinLines(3);
			}
		}));
	}

	public void testLinesIsBoth() {
		Limit two = new Limit() {
			public void apply(TextView view) {
				view.setLines(2);
			}
		};
		int lines = heightOf("1\n2", NONE);
		assertEquals(lines, heightOf("1", two));
		assertEquals(lines, heightOf("1\n2\n3", two));
	}

	public void testMaxHeightCaps() {
		assertEquals(10, heightOf("1\n2\n3", new Limit() {
			public void apply(TextView view) {
				view.setMaxHeight(10);
			}
		}));
	}

	public void testEditTextSingleLineSetsLines() {
		final int[] lines = new int[3];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				android.widget.EditText edit = new android.widget.EditText(getInstrumentation().getTargetContext());
				edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
				lines[0] = edit.getMaxLines();
				edit.setSingleLine(false);
				lines[1] = edit.getMaxLines();
				edit.setSingleLine(true);
				lines[2] = edit.getMaxLines();
			}
		});
		assertEquals(1, lines[0]);
		assertEquals(Integer.MAX_VALUE, lines[1]);
		assertEquals(1, lines[2]);
	}

	public void testTypefaceWithNormalStyleKeepsIt() {
		final Object[] typeface = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(getInstrumentation().getTargetContext());
				view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.NORMAL);
				typeface[0] = view.getTypeface();
			}
		});
		assertSame(android.graphics.Typeface.DEFAULT_BOLD, typeface[0]);
	}

	public void testMaxHeightReplacesMaxLines() {
		assertEquals(heightOf("1\n2\n3", NONE), heightOf("1\n2\n3", new Limit() {
			public void apply(TextView view) {
				view.setMaxLines(1);
				view.setMaxHeight(10000);
				assertEquals(-1, view.getMaxLines());
				assertEquals(10000, view.getMaxHeight());
			}
		}));
	}
}
