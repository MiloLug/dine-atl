package dev.dine.framework.windows;

import android.os.Bundle;
import android.widget.EditText;

// its manifest entry says stateVisible; a field has the focus from the start
public class VisibleModeActivity extends PlainActivity {
	EditText field;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		field = new EditText(this);
		setContentView(field);
		field.requestFocus();
	}
}
