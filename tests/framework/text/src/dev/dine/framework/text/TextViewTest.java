package dev.dine.framework.text;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.os.SystemClock;
import android.util.TypedValue;
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
}
