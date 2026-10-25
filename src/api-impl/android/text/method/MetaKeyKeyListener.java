package android.text.method;

import android.text.Editable;
import android.view.View;

/* No meta state: hardware keys reach the toolkit's text field, not a KeyListener. */
public abstract class MetaKeyKeyListener {
	public void clearMetaKeyState(View view, Editable content, int states) {}
}
