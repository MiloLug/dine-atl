package dev.dine.nav;

import android.content.Intent;
import android.view.MotionEvent;

// P: starts Q from its own ACTION_DOWN, while the finger is down
public class TouchStartActivity extends TouchLoggingActivity {
	public TouchStartActivity() {
		super("P");
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		boolean handled = super.dispatchTouchEvent(event);
		if (event.getAction() == MotionEvent.ACTION_DOWN)
			startActivity(new Intent(this, TouchEndActivity.class));
		return handled;
	}
}
