package dev.dine.nav;

import android.view.MotionEvent;

// Q: with the extra "finish", finishes on its own ACTION_DOWN
public class TouchEndActivity extends TouchLoggingActivity {
	public TouchEndActivity() {
		super("Q");
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		boolean handled = super.dispatchTouchEvent(event);
		if (event.getAction() == MotionEvent.ACTION_DOWN && getIntent().getBooleanExtra("finish", false))
			finish();
		return handled;
	}
}
