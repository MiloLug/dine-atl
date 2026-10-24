package dev.dine.framework.text;

import android.content.Context;
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
}
