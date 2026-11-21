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
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

	// both the label and a Paint set tabular digits: a font with proportional ones would measure them apart
	public void testLabelTakesDigitsAsThePaintDoes() {
		assertClose("digits", labelAndPaint("0123456789 1111111111", 40, 40), 2);
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

	// the overridable hooks AOSP calls from setText, the ChangeWatcher and the selection spans; no
	// @Override and no super call, so that the class compiles against a TextView without them
	private static class HookedEdit extends EditText {
		final List<String> calls = new ArrayList<String>(); // null while TextView's constructor runs

		HookedEdit(Context context) {
			super(context);
		}

		protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
			if (calls != null)
				calls.add("text " + start + " " + lengthBefore + " " + lengthAfter);
		}

		protected void onSelectionChanged(int selStart, int selEnd) {
			if (calls != null)
				calls.add("sel " + selStart + " " + selEnd);
		}
	}

	public void testSubclassHooksRun() {
		final String[] got = new String[3];
		onMain(new OnMain() {
			public Object run() {
				HookedEdit view = new HookedEdit(context());
				view.calls.clear();
				view.setText("ab");
				got[0] = view.calls.contains("text 0 0 2") ? "ok" : view.calls.toString();
				view.calls.clear();
				view.getText().append("c");
				got[1] = view.calls.toString();
				view.calls.clear();
				view.setSelection(1);
				got[2] = view.calls.toString();
				return null;
			}
		});
		assertEquals("ok", got[0]);
		assertEquals("[text 2 0 1]", got[1]);
		assertEquals("[sel 1 1]", got[2]);
	}

	private TextActivity activity() {
		return (TextActivity)getInstrumentation().startActivitySync(new android.content.Intent(context(), TextActivity.class));
	}

	private TextView attached(final TextActivity activity, final String text, final CharSequence hint) {
		final TextView[] view = new TextView[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view[0] = new TextView(activity);
				view[0].setText(text);
				if (hint != null)
					view[0].setHint(hint);
				activity.root.addView(view[0], new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
			}
		});
		settled(view[0]);
		return view[0];
	}

	// the next layout pass has run
	private void settled(TextView view) {
		getInstrumentation().waitForIdleSync();
		for (int i = 0; i < 100 && (view.isLayoutRequested() || view.getWidth() == 0 && view.getHeight() == 0 && view.getText().length() > 0); i++)
			SystemClock.sleep(20);
	}

	private static void assertAsksForLayout(String what, boolean asked) {
		assertTrue(what + " asks for a layout", asked);
	}

	public void testTextSizeRequestsALayoutAndGrows() {
		final TextActivity activity = activity();
		final TextView view = attached(activity, "abc", null);
		final int height = view.getHeight();
		final boolean[] asked = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.getTextSize() * 2);
				asked[0] = view.isLayoutRequested();
			}
		});
		assertAsksForLayout("setTextSize", asked[0]);
		settled(view);
		assertTrue(height + " then " + view.getHeight(), view.getHeight() > height);
	}

	public void testTypefaceRequestsALayout() {
		final TextActivity activity = activity();
		final TextView view = attached(activity, "abc", null);
		final boolean[] asked = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view.setTypeface(null, android.graphics.Typeface.BOLD);
				asked[0] = view.isLayoutRequested();
			}
		});
		assertAsksForLayout("setTypeface", asked[0]);
	}

	public void testAllCapsRequestsALayoutAndIsAllCaps() {
		final TextActivity activity = activity();
		final TextView view = attached(activity, "abc", null);
		final boolean[] got = new boolean[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view.setAllCaps(true);
				got[0] = view.isLayoutRequested();
				got[1] = view.isAllCaps();
			}
		});
		assertAsksForLayout("setAllCaps", got[0]);
		assertTrue("isAllCaps", got[1]);
	}

	public void testEllipsizeIsKeptAndRead() {
		final Object[] got = new Object[3];
		onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				got[0] = view.getEllipsize();
				view.setEllipsize(TextUtils.TruncateAt.END);
				got[1] = view.getEllipsize();
				view.setEllipsize(null);
				got[2] = view.getEllipsize();
				return null;
			}
		});
		assertNull(got[0]);
		assertEquals(TextUtils.TruncateAt.END, got[1]);
		assertNull(got[2]);
	}

	public void testEllipsizeAttribute() {
		final Object[] got = new Object[2];
		onMain(new OnMain() {
			public Object run() {
				android.view.View root = android.view.LayoutInflater.from(context()).inflate(R.layout.ellipsized, null);
				got[0] = ((TextView)root.findViewById(R.id.title)).getEllipsize();
				got[1] = ((TextView)root.findViewById(R.id.middle)).getEllipsize();
				return null;
			}
		});
		assertEquals(TextUtils.TruncateAt.END, got[0]);
		assertEquals(TextUtils.TruncateAt.MIDDLE, got[1]);
	}

	// AOSP's updateTextColors from drawableStateChanged
	public void testColorStateListFollowsTheDrawableState() {
		final int[] colors = new int[3];
		onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setTextColor(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}}, new int[] {0xff112233, 0xff445566}));
				colors[0] = view.getCurrentTextColor();
				view.setEnabled(false);
				colors[1] = view.getCurrentTextColor();
				view.setEnabled(true);
				colors[2] = view.getCurrentTextColor();
				return null;
			}
		});
		assertEquals(0xff445566, colors[0]);
		assertEquals(0xff112233, colors[1]);
		assertEquals(0xff445566, colors[2]);
	}

	// a TextView with no text shows its hint (an EditText's field takes it as its placeholder)
	public void testHintShowsWhileTheTextIsEmpty() {
		final TextActivity activity = activity();
		TextView hinted = attached(activity, "", "Pick a date");
		TextView bare = attached(activity, "", null);
		assertTrue("hinted width " + hinted.getWidth(), hinted.getWidth() > 0);
		assertEquals(0, bare.getWidth());
		assertEquals("", hinted.getText().toString());
	}

	// a hint set after the first layout changes what the view measures
	public void testHintRequestsALayoutAndTakesItsSize() {
		final TextActivity activity = activity();
		final TextView view = attached(activity, "", null);
		assertEquals(0, view.getWidth());
		final boolean[] asked = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view.setHint("Pick a date");
				asked[0] = view.isLayoutRequested();
			}
		});
		assertAsksForLayout("setHint", asked[0]);
		settled(view);
		assertTrue("width " + view.getWidth(), view.getWidth() > 0);
	}

	// AOSP's updateTextColors for the hint, from the drawable state
	public void testHintColorFollowsTheDrawableState() {
		final int[] colors = new int[3];
		onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(context());
				view.setHintTextColor(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}}, new int[] {0xff112233, 0xff445566}));
				colors[0] = view.getCurrentHintTextColor();
				view.setEnabled(false);
				colors[1] = view.getCurrentHintTextColor();
				view.setHintTextColor(0xff778899);
				colors[2] = view.getCurrentHintTextColor();
				return null;
			}
		});
		assertEquals(0xff445566, colors[0]);
		assertEquals(0xff112233, colors[1]);
		assertEquals(0xff778899, colors[2]);
	}

	// a view with no textColor answers AOSP's default, which its default text appearance would replace
	public void testNoTextColorAnswersTheDefault() {
		final int[] colors = new int[2];
		onMain(new OnMain() {
			public Object run() {
				TextView view = new TextView(new android.view.ContextThemeWrapper(context(), R.style.BareText));
				colors[0] = view.getCurrentTextColor();
				colors[1] = view.getTextColors().getDefaultColor();
				return null;
			}
		});
		assertEquals(0xff000000, colors[0]);
		assertEquals(0xff000000, colors[1]);
	}
}
