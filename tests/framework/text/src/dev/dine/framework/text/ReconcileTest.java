package dev.dine.framework.text;

import android.content.Context;
import android.text.InputFilter;
import android.text.Selection;
import android.widget.EditText;
import java.util.Arrays;

/* EditText.atlEdited, the entry a toolkit's report takes: the change between the report's states,
 * found around its old selection, applied to Java's own selection */
@SuppressWarnings("deprecation")
public class ReconcileTest extends android.test.InstrumentationTestCase {
	private String text;
	private int start, end;

	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	// edits sets up Java's state, then the report runs
	private void report(final Runnable edits, final EditText[] holder, final String prev, final int prevStart, final int prevEnd,
	                    final String now, final int nowStart, final int nowEnd) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				holder[0] = edit;
				edits.run();
				edit.atlEdited(prev, prevStart, prevEnd, now, nowStart, nowEnd, 1);
				text = edit.getText().toString();
				start = edit.getSelectionStart();
				end = edit.getSelectionEnd();
			}
		});
	}

	private Runnable state(final EditText[] holder, final String text, final int start, final int end) {
		return new Runnable() {
			public void run() {
				holder[0].setText(text);
				Selection.setSelection(holder[0].getText(), start, end);
			}
		};
	}

	public void testMatchedBase() {
		EditText[] edit = new EditText[1];
		final Watcher watcher = new Watcher();
		final EditText[] holder = edit;
		report(new Runnable() {
			public void run() {
				state(holder, "ab", 2, 2).run();
				holder[0].addTextChangedListener(watcher);
			}
		}, edit, "ab", 2, 2, "abc", 3, 3);
		assertEquals("abc", text);
		assertEquals(3, start);
		assertEquals(Arrays.asList("before 2 0 1", "on 2 0 1", "after abc"), watcher.calls);
	}

	// Java's setText came first: the typed character lands at Java's cursor, as an IME's commitText
	public void testStaleBaseLandsAtTheCursor() {
		EditText[] edit = new EditText[1];
		report(state(edit, "ab", 0, 0), edit, "hello", 5, 5, "hello!", 6, 6);
		assertEquals("!ab", text);
		assertEquals(1, start);
		assertEquals(1, end);
	}

	public void testForwardDelete() {
		EditText[] edit = new EditText[1];
		report(state(edit, "aa", 1, 1), edit, "aa", 1, 1, "a", 1, 1);
		assertEquals("a", text);
		assertEquals(1, start);
	}

	public void testReversedSelectionIsReplaced() {
		EditText[] edit = new EditText[1];
		report(state(edit, "abcd", 3, 1), edit, "abcd", 3, 1, "aXd", 2, 2);
		assertEquals("aXd", text);
		assertEquals(2, start);
	}

	public void testPositionsPastJavasTextClamp() {
		EditText[] edit = new EditText[1];
		report(state(edit, "ab", 2, 2), edit, "abcdef", 6, 6, "abcdefg", 9, 9);
		assertEquals("abg", text);
		assertEquals(3, start);
	}

	public void testFilterShortensTheInsertion() {
		final EditText[] edit = new EditText[1];
		report(new Runnable() {
			public void run() {
				edit[0].setFilters(new InputFilter[] {new InputFilter.LengthFilter(3)});
				state(edit, "ab", 2, 2).run();
			}
		}, edit, "ab", 2, 2, "abcd", 4, 4);
		assertEquals("abc", text);
		assertEquals(3, start);
	}

	public void testSelectionOnly() {
		EditText[] edit = new EditText[1];
		report(state(edit, "abc", 0, 0), edit, "abc", 0, 0, "abc", 1, 2);
		assertEquals("abc", text);
		assertEquals(1, start);
		assertEquals(2, end);
	}

	// two emoji share their high surrogate: the change takes the whole pair
	public void testSurrogatePairIsNotSplit() {
		final String grin = new String(Character.toChars(0x1F600)), beam = new String(Character.toChars(0x1F601));
		final EditText[] edit = new EditText[1];
		final Watcher watcher = new Watcher();
		report(new Runnable() {
			public void run() {
				state(edit, "a" + grin, 3, 3).run();
				edit[0].addTextChangedListener(watcher);
			}
		}, edit, "a" + grin, 3, 3, "a" + beam, 3, 3);
		assertEquals("a" + beam, text);
		assertEquals("on 1 2 2", watcher.calls.get(1));
	}
}
