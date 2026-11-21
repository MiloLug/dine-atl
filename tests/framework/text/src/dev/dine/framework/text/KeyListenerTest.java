package dev.dine.framework.text;

import android.content.Context;
import android.text.InputType;
import android.text.Selection;
import android.text.method.DialerKeyListener;
import android.text.Editable;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

/* A number or phone field's key listener filters what the toolkit reports (atlEdited), after the
 * app's filters; setText runs only the app's. */
@SuppressWarnings("deprecation")
public class KeyListenerTest extends android.test.InstrumentationTestCase {
	private final Object[] result = new Object[1];

	private interface Field {
		Object run(EditText edit);
	}

	private Object onMain(final Field field) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				result[0] = field.run(new EditText(getInstrumentation().getTargetContext()));
			}
		});
		return result[0];
	}

	// the user typed `typed` at the end of Java's text
	private static String typed(EditText edit, String typed) {
		String before = edit.getText().toString();
		Selection.setSelection(edit.getText(), before.length());
		String after = before + typed;
		edit.atlEdited(before, before.length(), before.length(), after, after.length(), after.length(), 1);
		return edit.getText().toString();
	}

	public void testSetTextKeepsLetters() {
		assertEquals("12ab3", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_NUMBER);
				edit.setText("12ab3");
				return edit.getText().toString();
			}
		}));
	}

	public void testNumberFieldDropsTypedLetters() {
		assertEquals("123", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_NUMBER);
				edit.setText("12");
				typed(edit, "a");
				return typed(edit, "3");
			}
		}));
	}

	public void testPhoneFieldKeepsDialerCharacters() {
		assertEquals("+1 (555)", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_PHONE);
				return typed(edit, "+1 (555)x");
			}
		}));
	}

	public void testListenerFollowsTheInputType() {
		assertEquals(Boolean.TRUE, onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_NUMBER);
				boolean digits = edit.getKeyListener() instanceof DigitsKeyListener;
				int signedDecimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL;
				edit.setInputType(signedDecimal);
				boolean signed = edit.getKeyListener() != null && edit.getKeyListener().getInputType() == signedDecimal;
				edit.setInputType(InputType.TYPE_CLASS_PHONE);
				boolean phone = edit.getKeyListener() instanceof DialerKeyListener;
				edit.setInputType(InputType.TYPE_CLASS_TEXT);
				return digits && signed && phone && edit.getKeyListener() == null;
			}
		}));
	}

	public void testSetKeyListener() {
		assertEquals("abc " + InputType.TYPE_CLASS_NUMBER, onMain(new Field() {
			public Object run(EditText edit) {
				edit.setKeyListener(DigitsKeyListener.getInstance("abc"));
				return typed(edit, "abcd") + " " + edit.getInputType();
			}
		}));
	}

	// a key listener changes the input type's class, not whether the field is single-line
	public void testNumberAfterDigitsListenerIsSingleLine() {
		assertEquals(1, onMain(new Field() {
			public Object run(EditText edit) {
				edit.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
				edit.setInputType(InputType.TYPE_CLASS_NUMBER);
				return edit.getMaxLines();
			}
		}));
	}

	public void testTextListenerAfterDigitsKeepsMultiLine() {
		assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, onMain(new Field() {
			public Object run(EditText edit) {
				edit.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
				edit.setKeyListener(new KeyListener() {
					public int getInputType() { return InputType.TYPE_CLASS_TEXT; }
					public boolean onKeyDown(View view, Editable text, int keyCode, KeyEvent event) { return false; }
					public boolean onKeyUp(View view, Editable text, int keyCode, KeyEvent event) { return false; }
					public boolean onKeyOther(View view, Editable text, KeyEvent event) { return false; }
					public void clearMetaKeyState(View view, Editable content, int states) {}
				});
				return edit.getInputType();
			}
		}));
	}

	// the datetime class: AOSP's Date, Time and DateTime key listeners, from the locale's patterns
	public void testDatetimeClassKeepsDatesAndTimes() {
		assertEquals("1/2", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
				return typed(edit, "1x/2");
			}
		}));
		assertEquals("1:3", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
				return typed(edit, "1x:3");
			}
		}));
		assertEquals("1/2 3:4", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
				return typed(edit, "1x/2 3:4");
			}
		}));
	}

	public void testDatetimeClassHasItsKeyListeners() {
		assertEquals("DateKeyListener", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
				return edit.getKeyListener().getClass().getSimpleName();
			}
		}));
		assertEquals("TimeKeyListener", onMain(new Field() {
			public Object run(EditText edit) {
				edit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
				return edit.getKeyListener().getClass().getSimpleName();
			}
		}));
	}
}
