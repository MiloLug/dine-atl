package android.text.method;

import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;

/* Takes no keys: the toolkit's field edits the text, and the Editable's filters, a key listener
 * among them, see each edit (EditText.atlEdited). */
public abstract class BaseKeyListener extends MetaKeyKeyListener implements KeyListener {
	public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyOther(View view, Editable content, KeyEvent event) {
		return false;
	}
}
