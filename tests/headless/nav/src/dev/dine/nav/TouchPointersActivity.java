package dev.dine.nav;

import android.content.Intent;
import android.view.MotionEvent;

// V: starts Q when a second finger lifts, while the first is still down
public class TouchPointersActivity extends TouchLoggingActivity {
	public TouchPointersActivity() {
		super("V");
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		boolean handled = super.dispatchTouchEvent(event);
		if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP)
			startActivity(new Intent(this, TouchEndActivity.class));
		return handled;
	}
}
