package dev.dine.framework.text;

import android.content.Context;
import android.text.InputType;
import android.text.Selection;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
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
}
