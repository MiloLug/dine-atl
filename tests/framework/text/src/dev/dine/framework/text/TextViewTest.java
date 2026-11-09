package dev.dine.framework.text;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.os.SystemClock;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class TextViewTest extends android.test.InstrumentationTestCase {
	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	public void testHint() {
		final CharSequence[] hints = new CharSequence[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				hints[0] = view.getHint();
				view.setHint("hint");
				hints[1] = view.getHint();
			}
		});
		assertNull(hints[0]);
		assertEquals("hint", hints[1].toString());
	}

	public void testSelectable() {
		final boolean[] selectable = new boolean[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				selectable[0] = view.isTextSelectable();
				view.setTextIsSelectable(true);
				selectable[1] = view.isTextSelectable();
			}
		});
		assertFalse(selectable[0]);
		assertTrue(selectable[1]);
	}

	// NORMAL keeps a copy: spans the caller adds to its object later don't reach the view
	public void testNormalTextIsACopy() {
		final Object[] got = new Object[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				SpannableString source = new SpannableString("ab");
				source.setSpan(new StyleSpan(1), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				view.setText(source);
				source.setSpan(new UnderlineSpan(), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				Spanned text = (Spanned)view.getText();
				got[0] = text.getSpans(0, 2, StyleSpan.class).length;
				got[1] = text.getSpans(0, 2, UnderlineSpan.class).length;
			}
		});
		assertEquals(1, got[0]);
		assertEquals(0, got[1]);
	}

	public void testSpannableTextIsTheViewsOwn() {
		final Object[] got = new Object[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				SpannableString source = new SpannableString("ab");
				view.setText(source, TextView.BufferType.SPANNABLE);
				got[0] = view.getText() instanceof Spannable;
				got[1] = view.getText() != source;
			}
		});
		assertEquals(true, got[0]);
		assertEquals(true, got[1]);
	}

	public void testTextChangedListenersHearSetTextAndAppend() {
		final Watcher watcher = new Watcher();
		final Object[] got = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				view.setText("ab");
				view.addTextChangedListener(watcher);
				view.setText("xyz");
				view.append("!");
				got[0] = view.getText() instanceof Editable;
			}
		});
		assertEquals("[before 0 2 3, on 0 2 3, after xyz, before 3 0 1, on 3 0 1, after xyz!]", watcher.calls.toString());
		assertEquals(true, got[0]);
	}

	// no link colour of its own: the theme's textColorLink
	public void testLinkColorComesFromTheTheme() {
		final Object[] got = new Object[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				ColorStateList links = view.getLinkTextColors();
				got[0] = links != null ? links.getDefaultColor() : null;
				TypedValue value = new TypedValue();
				context().getTheme().resolveAttribute(android.R.attr.textColorLink, value, true);
				got[1] = value.resourceId != 0 ? context().getResources().getColorStateList(value.resourceId).getDefaultColor() : value.data;
			}
		});
		assertNotNull(got[0]);
		assertEquals(got[1], got[0]);
	}

	// AOSP's mBufferType: setText(CharSequence) keeps the last type
	public void testBufferTypeStays() {
		final Object[] got = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TextView view = new TextView(context());
				view.setText("a", TextView.BufferType.SPANNABLE);
				view.setText("b");
				got[0] = view.getText() instanceof Spannable;
			}
		});
		assertEquals(true, got[0]);
	}

	// a wrap_content view grows with its appended text
	public void testAppendResizes() {
		android.content.Intent intent = new android.content.Intent(context(), TextActivity.class);
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(intent);
		final TextView[] view = new TextView[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view[0] = new TextView(activity);
				view[0].setText("a");
				android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
				root.addView(view[0], new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
				activity.setContentView(root);
				view[0].append("b");
			}
		});
		getInstrumentation().waitForIdleSync();
		final int[] width = new int[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				width[0] = view[0].getWidth();
				view[0].append(" and much more text after it");
			}
		});
		getInstrumentation().waitForIdleSync();
		for (int i = 0; i < 100 && view[0].getWidth() == width[0]; i++)
			SystemClock.sleep(20);
		width[1] = view[0].getWidth();
		assertTrue(width[0] + " then " + width[1], width[1] > width[0]);
	}

	private interface OnMain {
		Object run();
	}

	private Object onMain(final OnMain body) {
		final Object[] result = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				result[0] = body.run();
			}
		});
		return result[0];
	}

	public void testLayoutIsKept() {
		assertEquals(Boolean.TRUE, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a");
				return view.getLayout() == view.getLayout();
			}
		}));
	}

	public void testLayoutFollowsSetText() {
		assertEquals("b", onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a");
				view.getLayout();
				view.setText("b");
				return view.getLayout().getText().toString();
			}
		}));
	}

	public void testLayoutFollowsAnEdit() {
		assertEquals("ab", onMain(new OnMain() {
			public Object run() {
				EditText edit = new EditText(context());
				edit.setText("a");
				edit.getLayout();
				edit.getText().append("b");
				return edit.getLayout().getText().toString();
			}
		}));
	}

	public void testLayoutFollowsTextSize() {
		assertEquals(Boolean.TRUE, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a");
				Layout before = view.getLayout();
				view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.getTextSize() * 2);
				return before != view.getLayout();
			}
		}));
	}

	public void testLayoutFollowsWidth() {
		assertEquals(200, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a");
				view.layout(0, 0, 100, 50);
				view.getLayout();
				view.layout(0, 0, 200, 50);
				return view.getLayout().getWidth();
			}
		}));
	}

	public void testLineCountFollowsText() {
		assertEquals(3, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a\nb\nc");
				view.layout(0, 0, 200, 50);
				return view.getLineCount();
			}
		}));
	}

	// AOSP has no layout before the first measure
	public void testLineCountBeforeMeasure() {
		assertEquals(0, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a\nb\nc");
				return view.getLineCount();
			}
		}));
	}

	// AOSP builds its layout in onMeasure; at width 0 each letter would be a line
	public void testLineCountAfterMeasure() {
		assertEquals(2, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("ab\ncd");
				view.measure(android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY),
				             android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
				return view.getLineCount();
			}
		}));
	}

	// without an ellipsis AOSP's StaticLayout keeps the lines past maxLines
	public void testLineCountIgnoresMaxLines() {
		assertEquals(3, onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("a\nb\nc");
				view.setMaxLines(1);
				view.layout(0, 0, 200, 50);
				return view.getLineCount();
			}
		}));
	}

	// a width the text just fits in
	private static int fitting(TextView view, String text) {
		return (int)Math.ceil(view.getPaint().measureText(text)) + 2;
	}

	public void testLineCountInsidePadding() {
		assertEquals("1 2", onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("aaaa bbbb");
				int width = fitting(view, "aaaa bbbb");
				view.layout(0, 0, width, 50);
				int plain = view.getLineCount();
				view.setPadding(width / 4, 0, width / 4, 0);
				return plain + " " + view.getLineCount();
			}
		}));
	}

	public void testLineCountBesideDrawable() {
		int[] got = (int[])onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setText("aaaa bbbb");
				int width = fitting(view, "aaaa bbbb");
				view.layout(0, 0, width, 50);
				android.graphics.drawable.ColorDrawable icon = new android.graphics.drawable.ColorDrawable(0xff000000);
				icon.setBounds(0, 0, width / 2 - 3, 1);
				view.setPadding(3, 0, 0, 0);
				view.setCompoundDrawables(icon, null, null, null);
				return new int[] {view.getCompoundPaddingLeft(), width / 2, view.getLineCount()};
			}
		});
		assertEquals("compound padding", got[1], got[0]);
		assertEquals("lines", 2, got[2]);
	}

	// [the label's width, ceil of a Paint's at paintSize]; the view's text is shown at viewSize
	private int[] labelAndPaint(final CharSequence shown, final int viewSize, final int paintSize) {
		return (int[])onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setTextSize(TypedValue.COMPLEX_UNIT_PX, viewSize);
				view.setText(shown);
				int unspecified = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED);
				view.measure(unspecified, unspecified);
				android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
				paint.setTextSize(paintSize);
				return new int[] {view.getMeasuredWidth(), (int)Math.ceil(paint.measureText(shown.toString()))};
			}
		});
	}

	private static void assertClose(String what, int[] widths, int tolerance) {
		assertTrue(what + ": label " + widths[0] + ", paint " + widths[1], Math.abs(widths[0] - widths[1]) <= tolerance);
	}

	public void testLabelTakesPx() {
		assertClose("size", labelAndPaint("Translation", 40, 40), 2);
	}

	public void testSpanTakesPx() {
		SpannableString span = new SpannableString("Translation");
		span.setSpan(new android.text.style.AbsoluteSizeSpan(40), 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		assertClose("span", labelAndPaint(span, 10, 40), 2);
	}

	/* A Paint measures as the label draws, on the main thread and on another one. The text is long
	 * enough that a context's glyph rounding moves its width past the tolerance. */
	public void testPaintMeasuresAsLabelOnEveryThread() throws InterruptedException {
		final String text = "The quick brown fox jumps over the lazy dog while five boxing wizards jump quickly and a sphinx of black quartz judges my vow";
		int[] main = labelAndPaint(text, 14, 14);
		assertClose("main", main, 1);
		final float[] worker = new float[1];
		Thread thread = new Thread(new Runnable() {
			public void run() {
				android.graphics.Paint paint = new android.graphics.Paint();
				paint.setTextSize(14);
				worker[0] = paint.measureText(text);
			}
		});
		thread.start();
		thread.join();
		assertClose("worker", new int[] {main[0], (int)Math.ceil(worker[0])}, 1);
	}

	// listeners run after the change: the layout they read already has the new text
	public void testWatcherSeesTheNewLayout() {
		assertEquals("b bc", onMain(new OnMain() {
			public Object run() {
				final StringBuilder seen = new StringBuilder();
				final EditText edit = new EditText(context());
				edit.setText("a");
				edit.getLayout();
				edit.addTextChangedListener(new TextWatcher() {
					public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
					public void onTextChanged(CharSequence s, int start, int before, int count) {}
					public void afterTextChanged(Editable s) {
						seen.append(seen.length() == 0 ? "" : " ").append(edit.getLayout().getText());
					}
				});
				edit.setText("b");
				edit.getText().append("c");
				return seen.toString();
			}
		}));
	}
}
