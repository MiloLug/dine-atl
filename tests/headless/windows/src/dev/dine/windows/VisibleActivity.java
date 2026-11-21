package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;

// stateVisible in its manifest entry, a field focused from the start: the keyboard comes with the window
public class VisibleActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EditText field = new EditText(this);
		field.setHint("visible field");
		setContentView(field);
		field.requestFocus();
	}
}
