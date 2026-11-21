package dev.dine.framework.text;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannedString;
import android.widget.EditText;
import java.util.Arrays;

// EditText's Editable: one object between setTexts, AOSP's watcher ranges, filters and selection
@SuppressWarnings("deprecation")
public class EditTextTest extends android.test.InstrumentationTestCase {
	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	public void testGetTextIsTheEditable() {
		final Object[] texts = new Object[4];
		onMain(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				texts[0] = edit.getText();
				texts[1] = edit.getText();
				edit.setText("x");
				texts[2] = edit.getText();
				texts[3] = edit.getEditableText();
			}
		});
		assertSame(texts[0], texts[1]);
		assertSame(texts[2], texts[3]);
		assertEquals("x", texts[2].toString());
	}

	public void testSetTextNotifiesWithRanges() {
		final Watcher watcher = new Watcher();
		onMain(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				edit.setText("ab");
				edit.addTextChangedListener(watcher);
				edit.setText("xyz");
			}
		});
		assertEquals(Arrays.asList("before 0 2 3", "on 0 2 3", "after xyz"), watcher.calls);
	}

	public void testEditsToTheEditableNotify() {
		final Watcher watcher = new Watcher();
		final String[] text = new String[1];
		onMain(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				edit.setText("ab");
				edit.addTextChangedListener(watcher);
				edit.append("c");
				edit.getText().insert(0, "_");
				text[0] = edit.getText().toString();
			}
		});
		assertEquals("_abc", text[0]);
		assertEquals(Arrays.asList("before 2 0 1", "on 2 0 1", "after abc", "before 0 0 1", "on 0 0 1", "after _abc"), watcher.calls);
	}

	public void testFiltersApply() {
		final String[] texts = new String[2];
		onMain(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				edit.setFilters(new InputFilter[] {new InputFilter.LengthFilter(3)});
				edit.setText("abcdef");
				texts[0] = edit.getText().toString();
				edit.getText().append("z");
				texts[1] = edit.getText().toString();
			}
		});
		assertEquals("abc", texts[0]);
		assertEquals("abc", texts[1]);
	}

	public void testLengthFilterKeepsWhatFits() {
		InputFilter filter = new InputFilter.LengthFilter(2);
		assertEquals("ab", filter.filter("abc", 0, 3, new SpannedString(""), 0, 0).toString());
		assertNull(filter.filter("a", 0, 1, new SpannedString(""), 0, 0));
		assertEquals("", filter.filter("c", 0, 1, new SpannedString("ab"), 2, 2).toString());
	}

	public void testSelection() {
		final int[] selections = new int[6];
		onMain(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				edit.setText("hello");
				selections[0] = edit.getSelectionStart();
				selections[1] = edit.getSelectionEnd();
				edit.setSelection(1, 3);
				selections[2] = edit.getSelectionStart();
				selections[3] = edit.getSelectionEnd();
				edit.selectAll();
				selections[4] = edit.getSelectionStart();
				selections[5] = edit.getSelectionEnd();
			}
		});
		assertEquals("[0, 0, 1, 3, 0, 5]", Arrays.toString(selections));
	}

	// a bare EditText is multi-line, as AOSP's TextView constructor makes it
	public void testInputTypes() {
		final int[] types = new int[3];
		onMain(new Runnable() {
			public void run() {
				EditText edit = new EditText(context());
				types[0] = edit.getInputType();
				edit.setSingleLine();
				types[1] = edit.getInputType();
				edit.setInputType(InputType.TYPE_CLASS_NUMBER);
				types[2] = edit.getInputType();
			}
		});
		assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, types[0]);
		assertEquals(InputType.TYPE_CLASS_TEXT, types[1]);
		assertEquals(InputType.TYPE_CLASS_NUMBER, types[2]);
	}

	// a focus move leaves the selection where it was (gtk's grab selects the field's contents)
	public void testRequestFocusKeepsTheSelection() {
		final TextActivity activity = (TextActivity)getInstrumentation().startActivitySync(new android.content.Intent(context(), TextActivity.class));
		final EditText[] edit = new EditText[1];
		onMain(new Runnable() {
			public void run() {
				edit[0] = new EditText(context());
				edit[0].setText("abc");
				edit[0].setSelection(1);
				activity.root.addView(edit[0]);
			}
		});
		getInstrumentation().waitForIdleSync();
		android.os.SystemClock.sleep(500); // the toolkit's field takes Java's state
		onMain(new Runnable() {
			public void run() {
				edit[0].requestFocus();
			}
		});
		getInstrumentation().waitForIdleSync();
		android.os.SystemClock.sleep(500); // a report the focus move caused arrives
		final int[] selection = new int[2];
		onMain(new Runnable() {
			public void run() {
				selection[0] = edit[0].getSelectionStart();
				selection[1] = edit[0].getSelectionEnd();
				activity.root.removeAllViews();
			}
		});
		assertEquals(1, selection[0]);
		assertEquals(1, selection[1]);
	}
}
